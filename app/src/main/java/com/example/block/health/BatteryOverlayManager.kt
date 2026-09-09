package com.example.block.health

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen overlay shown when battery optimization is restricting Block
 * (which can cause the DNS watcher / accessibility guard to get killed or
 * throttled in the background). Structurally the same idea as
 * DnsOverlayManager, kept as its own manager so the two can be shown/hidden
 * independently without interfering with each other's state.
 *
 * Only usable when "Display over other apps" is already granted — if that
 * permission itself is what's missing, HealthNotifier's notification is used
 * instead, since we can't overlay a warning about the permission needed to
 * draw that same overlay.
 */
object BatteryOverlayManager {

    private var overlayView: LinearLayout? = null

    fun requestUnrestrictedBattery(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("BatteryOverlayManager", "Direct battery request failed, falling back", e)

            val fallback = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }

    fun showOverlay(context: Context) {
        if (overlayView != null) return
        if (!SystemHealthChecker.hasOverlayPermission(context)) {
            Log.d("BatteryOverlayManager", "No overlay permission — can't show overlay")
            return
        }

        val windowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

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
            text = "Battery optimization is restricting Block.\nAllow unrestricted battery use so protection keeps running in the background."
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val fixButton = Button(context).apply {
            text = "Open Battery Settings"
            setOnClickListener {
                // Hide first: this overlay renders above regular app windows,
                // so leaving it up would bury the Settings screen behind it
                // and make the button look like it does nothing.
                hideOverlay(context)
                requestUnrestrictedBattery(context)
            }
        }

        layout.addView(messageText)
        layout.addView(fixButton)

        try {
            windowManager.addView(layout, params)
            overlayView = layout
            Log.d("BatteryOverlayManager", "Overlay shown")
        } catch (e: Exception) {
            Log.e("BatteryOverlayManager", "Failed to show overlay", e)
        }
    }

    fun hideOverlay(context: Context) {
        val view = overlayView ?: return
        try {
            val windowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(view)
            overlayView = null
            Log.d("BatteryOverlayManager", "Overlay hidden")
        } catch (e: Exception) {
            Log.e("BatteryOverlayManager", "Failed to hide overlay", e)
        }
    }
}
