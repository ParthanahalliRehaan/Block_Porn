package com.example.block.health

import android.content.Context
import android.os.PowerManager
import android.provider.Settings

/**
 * Autostart has no equivalent here on purpose: Android exposes no public API
 * to read or set it — it's an OEM-specific setting (MIUI, ColorOS, EMUI,
 * FuntouchOS, One UI all ship their own screen for it). See AutoStartHelper
 * for the best-effort deep link instead of a check.
 */
object SystemHealthChecker {

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun isBatteryUnrestricted(context: Context): Boolean {
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as PowerManager

        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
