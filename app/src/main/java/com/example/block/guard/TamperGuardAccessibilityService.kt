package com.example.block.guard

import android.os.Handler
import android.os.Looper
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.block.R
import com.example.block.removal.RemovalActivity
import com.example.block.removal.RemovalController

/**
 * Without Device Owner provisioning, Android gives a regular Device Admin app
 * no way to stop the user from opening Settings and turning the admin off, or
 * uninstalling the app outright — see the limitations note shipped alongside
 * this file. This service is the mitigation available to a normal (non
 * device-owner) app: it watches for the *specific* system screens that would
 * let someone disable protection, uninstall the app, or turn this service
 * off, and — unless the sanctioned in-app removal flow (RemovalActivity's
 * hour + PIN process) is currently running — kicks the user back to the home
 * screen and opens RemovalActivity instead.
 *
 * This is a deterrent, not a guarantee. Booting into Safe Mode disables all
 * accessibility services (including this one), and ADB or a factory reset
 * bypass it entirely. See the project notes for the Device Owner option,
 * which is the only way to make uninstall itself blockable at the OS level.
 */
class TamperGuardAccessibilityService : AccessibilityService() {

    // Only screens from these apps can ever trigger a redirect. This is what
    // stops the guard from firing on unrelated things like pulling down the
    // notification shade (which shows Block's "Block is active" notification
    // plus an "App info" long-press action — from systemui, not Settings) or
    // the launcher's own app-info menu. Add more OEM package names here if a
    // specific device's Settings app uses a different one.
    private val handler = Handler(Looper.getMainLooper())

    // Labels seen across different launchers' Recents "clear everything"
    // control (Pixel/AOSP says "Clear all", Samsung says "Close all",
    // some OEMs say "Clear recents"). Matched against both visible text
    // and contentDescription since some launchers use an icon-only button.
    private val clearAllLabels = listOf("clear all", "close all", "clear recents")
    private val watchedPackages = setOf(
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.miui.securitycenter",
        "com.coloros.safecenter",
        "com.oppo.safe",
        "com.vivo.permissionmanager",
        "com.huawei.systemmanager",
        "com.samsung.android.lool"
    )

    // Phrases tied to the specific actions we want to block — deactivating
    // Device Admin, uninstalling, or turning this accessibility service off —
    // rather than broad words that also show up on screens someone has every
    // reason to visit (e.g. "accessibility" alone matches the whole
    // Accessibility settings list, not just Block's entry in it).
    private val sensitiveKeywords = listOf(
        "deactivate",
        "uninstall",
        "stop this service",
        "stop the service",
        "turn off this service"
    )

    // Tracks the windowId we've already bounced, so a single Settings screen
    // that fires several TYPE_WINDOW_STATE_CHANGED events while it loads
    // only triggers one redirect instead of repeatedly opening/closing.
    private var lastHandledWindowId: Int? = null

    // On top of the per-window dedupe above: a short global cooldown so
    // drilling from a list screen into a detail screen (two different
    // windows, both flagged) doesn't produce two back-to-back bounces that
    // read as a flicker.
    private var lastInterceptAtMs = 0L
    private val interceptCooldownMs = 500L

    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 200
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val sourcePackage = event.packageName?.toString()

        // Never intercept our own screens (the removal flow itself lives here).
        if (sourcePackage == applicationContext.packageName) {
            lastHandledWindowId = null
            return
        }

        // The user is inside the official removal flow right now — let them through.
        if (RemovalController.isLegitimateFlowActive()) {
            lastHandledWindowId = null
            return
        }

        // Ignore everything that isn't Settings / a package installer / a
        // known OEM permission manager — this is what stops false triggers
        // from the notification shade, launcher, recents, etc.
        if (sourcePackage !in watchedPackages) {
            lastHandledWindowId = null
            return
        }

        val root = rootInActiveWindow ?: return

        val screenText = collectText(root).lowercase()

        val appLabel = getString(R.string.app_name).lowercase()

        val mentionsUs =
            screenText.contains(applicationContext.packageName.lowercase()) ||
                screenText.contains(appLabel)

        val mentionsSensitiveAction =
            sensitiveKeywords.any { screenText.contains(it) }

        // Normally we only bounce when a sensitive word is actually visible
        // (avoids false positives on, say, the full Accessibility settings
        // list, which mentions every app including ours). But once a
        // lockout is active from a prior attempt, we tighten this up: any
        // watched-package screen that even mentions us gets closed, so
        // there's no window to scroll to "Uninstall" before we react.
        val shouldIntercept =
            mentionsUs && (mentionsSensitiveAction || TamperLockout.isActive(applicationContext))

        if (shouldIntercept) {

            if (event.windowId == lastHandledWindowId) {
                // Already redirected away from this exact window — ignore
                // the follow-up events it's still firing while it closes.
                return
            }

            val now = System.currentTimeMillis()

            if (now - lastInterceptAtMs < interceptCooldownMs) {
                // Still cooling down from the last redirect — avoid a
                // second bounce for what's effectively the same attempt.
                lastHandledWindowId = event.windowId
                return
            }

            lastHandledWindowId = event.windowId
            lastInterceptAtMs = now

            Log.i(TAG, "Tamper attempt detected on package=$sourcePackage — redirecting")
            interceptTamperAttempt()

        } else {
            lastHandledWindowId = null
        }
    }

    private fun collectText(
        node: AccessibilityNodeInfo,
        depth: Int = 0,
        builder: StringBuilder = StringBuilder()
    ): String {

        if (depth > 40) return builder.toString()

        node.text?.let { builder.append(it).append(' ') }
        node.contentDescription?.let { builder.append(it).append(' ') }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectText(child, depth + 1, builder)
            }
        }

        return builder.toString()
    }

    private fun interceptTamperAttempt() {
        TamperLockout.trigger(applicationContext)

        performGlobalAction(GLOBAL_ACTION_HOME)

        // Sending Home only backgrounds the App Info screen — Settings
        // keeps its task alive in Recents, where it can be resumed
        // directly without "opening" it again, skipping this service's
        // detection entirely. Best-effort mitigation: open Recents
        // ourselves and tap whatever "clear all" control the launcher
        // offers, wiping every recent task (not just Settings — there's no
        // API to target just one) so there's nothing left to resume.
        handler.postDelayed({
            clearRecentsIfPossible()
        }, 300L)

        val intent = Intent(this, RemovalActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        startActivity(intent)
    }

    private fun clearRecentsIfPossible() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)

        handler.postDelayed({
            val root = rootInActiveWindow

            if (root != null) {
                val clearButton = findClearAllButton(root)

                if (clearButton != null) {
                    clearButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.i(TAG, "Cleared Recents after tamper attempt")
                } else {
                    Log.d(TAG, "No 'clear all' control found in Recents on this launcher")
                }
            }

            // Whether or not clearing worked, get back off Recents.
            performGlobalAction(GLOBAL_ACTION_HOME)
        }, 350L)
    }

    private fun findClearAllButton(
        node: AccessibilityNodeInfo,
        depth: Int = 0
    ): AccessibilityNodeInfo? {

        if (depth > 40) return null

        val label = (node.text?.toString() ?: node.contentDescription?.toString())
            ?.lowercase()

        if (label != null && clearAllLabels.any { label.contains(it) }) {
            return node
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findClearAllButton(child, depth + 1)?.let { return it }
            }
        }

        return null
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "TamperGuard"
    }
}
