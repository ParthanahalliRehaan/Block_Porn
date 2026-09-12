package com.example.block.guard

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen overlay shown when the Tamper Guard accessibility service is
 * off. Structurally the same as DnsOverlayManager — its own object so it can
 * be shown/hidden independently of the DNS and battery overlays.
 *
 * Same permission dependency as the others: needs "Display over other apps"
 * already granted. If that's missing, HealthNotifier's notification path is
 * the fallback (see DnsWatcherService for how the two are coordinated).
 */
object AccessibilityGuardOverlayManager {

    private var overlayView: LinearLayout? = null

    // Once the user taps "Open Accessibility Settings" we have to back off
    // for a bit — the overlay is full-screen and would otherwise render on
    // top of the very toggle they're trying to reach, making it impossible
    // to actually turn the service back on. This is not a leniency window
    // for skipping the fix, just enough time to reach the switch and flip
    // it. If they back out without fixing it, the watcher's next poll after
    // this expires puts the overlay right back.
    private const val FIX_ATTEMPT_GRACE_MS = 1_000L
    private var graceUntilMs = 0L

    fun isShowing(): Boolean = overlayView != null

    fun markFixAttemptStarted() {
        graceUntilMs = System.currentTimeMillis() + FIX_ATTEMPT_GRACE_MS
    }

    fun isInGracePeriod(): Boolean =
        System.currentTimeMillis() < graceUntilMs

    /** Shows the warning overlay, if not already showing and permission is granted. */
    fun showOverlay(context: Context) {
        if (overlayView != null) return // already showing
        if (!com.example.block.dns.DnsOverlayManager.hasOverlayPermission(context)) {
            Log.d("AccessibilityGuardOverlay", "No overlay permission — can't show overlay")
            return
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#DD000000"))
            setPadding(60, 60, 60, 60)
        }

        val messageText = TextView(context).apply {
            text = "Tamper Guard is off.\nTurn it back on to keep protection active."
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val fixButton = Button(context).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                // Hide first so the Settings screen we're about to open
                // isn't rendered behind this overlay (same reasoning as
                // DnsOverlayManager / BatteryOverlayManager), and start the
                // grace window so the watcher doesn't immediately cover the
                // toggle back up while they're trying to flip it.
                markFixAttemptStarted()
                hideOverlay(context)
                TamperGuardStatus.openAccessibilitySettings(context)
            }
        }

        layout.addView(messageText)
        layout.addView(fixButton)

        try {
            windowManager.addView(layout, params)
            overlayView = layout
            Log.d("AccessibilityGuardOverlay", "Overlay shown")
        } catch (e: Exception) {
            Log.e("AccessibilityGuardOverlay", "Failed to show overlay", e)
        }
    }

    /** Removes the warning overlay, if currently showing. */
    fun hideOverlay(context: Context) {
        val view = overlayView ?: return
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(view)
            overlayView = null
            Log.d("AccessibilityGuardOverlay", "Overlay hidden")
        } catch (e: Exception) {
            Log.e("AccessibilityGuardOverlay", "Failed to hide overlay", e)
        }
    }
}