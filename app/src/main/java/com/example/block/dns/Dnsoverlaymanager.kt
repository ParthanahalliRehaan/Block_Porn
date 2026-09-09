package com.example.block.dns

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Manages a full-screen overlay window shown when Private DNS is off.
 * Uses the SYSTEM_ALERT_WINDOW / TYPE_APPLICATION_OVERLAY mechanism —
 * requires the user to have granted "Display over other apps" once,
 * manually, via Settings.
 */
object DnsOverlayManager {

    private var overlayView: LinearLayout? = null

    /** True if the "Display over other apps" permission is currently granted. */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /** Opens the system screen where the user grants the overlay permission. */
    fun requestOverlayPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Shows the warning overlay, if not already showing and permission is granted. */
    fun showOverlay(context: Context) {
        if (overlayView != null) return // already showing
        if (!hasOverlayPermission(context)) {
            Log.d("DnsOverlayManager", "No overlay permission — can't show overlay")
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // remove this flag if you want it to intercept back/etc
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#DD000000")) // dark translucent
            setPadding(60, 60, 60, 60)
        }

        val messageText = TextView(context).apply {
            text = "Private DNS is off.\nTap below to fix it."
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val fixButton = Button(context).apply {
            text = "Open DNS Settings"
            setOnClickListener {
                // Hide first — see BatteryOverlayManager for why: this
                // overlay type renders above regular app windows, so the
                // Settings screen would otherwise open hidden behind it.
                hideOverlay(context)
                DnsSetupHelper.openPrivateDnsSettings(context)
            }
        }

        layout.addView(messageText)
        layout.addView(fixButton)

        try {
            // Need FLAG_NOT_FOCUSABLE removed to let the button actually receive taps
            params.flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            windowManager.addView(layout, params)
            overlayView = layout
            Log.d("DnsOverlayManager", "Overlay shown")
        } catch (e: Exception) {
            Log.e("DnsOverlayManager", "Failed to show overlay", e)
        }
    }

    /** Removes the warning overlay, if currently showing. */
    fun hideOverlay(context: Context) {
        val view = overlayView ?: return
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(view)
            overlayView = null
            Log.d("DnsOverlayManager", "Overlay hidden")
        } catch (e: Exception) {
            Log.e("DnsOverlayManager", "Failed to hide overlay", e)
        }
    }
}