package com.example.block.health

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Android has no public API to read or set "autostart" / "allow background
 * activity" — each manufacturer ships its own screen for it, with component
 * names that can also change between firmware versions. This is a
 * best-effort deep link, not a guarantee: if none of the known screens for
 * the device's brand resolve, it falls back to the app's own App Info page.
 *
 * Because it can't be verified programmatically (unlike overlay/battery),
 * this is handled as a one-time onboarding step the user confirms manually,
 * not something the background poll can continuously enforce.
 */
object AutoStartHelper {

    private const val PREFS = "block_health"
    private const val KEY_AUTOSTART_ACK = "autostart_ack"

    private val knownScreens: List<Pair<String, String>>
        get() {
            val brand = Build.MANUFACTURER.lowercase()

            return when {
                brand.contains("xiaomi") -> listOf(
                    "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )

                brand.contains("oppo") -> listOf(
                    "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                    "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity"
                )

                brand.contains("vivo") -> listOf(
                    "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )

                brand.contains("huawei") || brand.contains("honor") -> listOf(
                    "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )

                brand.contains("samsung") -> listOf(
                    "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity"
                )

                brand.contains("oneplus") -> listOf(
                    "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )

                else -> emptyList()
            }
        }

    fun openAutoStartSettings(context: Context) {

        for ((pkg, cls) in knownScreens) {
            try {
                val intent = Intent().apply {
                    component = ComponentName(pkg, cls)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                context.startActivity(intent)
                return

            } catch (e: ActivityNotFoundException) {
                Log.d("AutoStartHelper", "$pkg/$cls not present, trying next option")
            } catch (e: Exception) {
                Log.d("AutoStartHelper", "Failed to open $pkg/$cls", e)
            }
        }

        // No known screen for this brand/firmware — fall back to the app's
        // own settings page, where equivalent options often live on stock
        // Android (e.g. "Allow background activity").
        try {
            val fallback = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        } catch (e: Exception) {
            Log.e("AutoStartHelper", "No autostart-equivalent settings screen found", e)
        }
    }

    fun hasAcknowledged(context: Context): Boolean {
        return context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTOSTART_ACK, false)
    }

    fun acknowledge(context: Context) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTOSTART_ACK, true)
            .apply()
    }
}
