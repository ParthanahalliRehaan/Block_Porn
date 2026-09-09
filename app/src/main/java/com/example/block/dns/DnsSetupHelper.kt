package com.example.block.dns

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast

/**
 * Helper for guiding the user to set Android's Private DNS to a fixed
 * filtering resolver (family.adguard-dns.com), and checking whether
 * it's still set correctly.
 *
 * IMPORTANT LIMITATION: Android does not allow a normal (non-Device-Owner)
 * app to silently write the Private DNS setting on the user's behalf.
 * The best we can do without enterprise/MDM provisioning is:
 *   1. Deep-link the user straight to the correct settings screen
 *   2. Periodically check the value and prompt again if it's been changed
 */
object DnsSetupHelper {
    // (Compose project note: call verifyAndPromptIfNeeded(this) right after
    // super.onCreate(savedInstanceState) in MainActivity — no setContentView needed)

    // The hostname we want Private DNS locked to.
    const val TARGET_DNS_HOSTNAME = "family.adguard-dns.com"

    /**
     * Opens the Private DNS settings screen directly.
     * On most stock Android (9+) this action exists; if a manufacturer's
     * skin doesn't support it, we fall back to the general Network settings.
     */
    fun openPrivateDnsSettings(context: Context) {
        Log.d("DnsSetupHelper", "openPrivateDnsSettings() called")
        try {
            val directIntent = Intent("android.settings.PRIVATE_DNS_SETTINGS")
            directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = directIntent.resolveActivity(context.packageManager)
            Log.d("DnsSetupHelper", "resolveActivity result: $resolved")

            if (resolved != null) {
                context.startActivity(directIntent)
                Log.d("DnsSetupHelper", "Started direct Private DNS settings intent")
            } else {
                Toast.makeText(
                    context,
                    "Open Network Settings → Private DNS and enter: $TARGET_DNS_HOSTNAME",
                    Toast.LENGTH_LONG
                ).show()
                val fallback = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
                Log.d("DnsSetupHelper", "Started fallback Wireless settings intent")
            }
        } catch (e: Exception) {
            Log.e("DnsSetupHelper", "Failed to open settings", e)
        }
    }

    /**
     * Reads the currently configured Private DNS hostname, if any.
     * Returns null if Private DNS is off or set to "automatic".
     *
     * Note: this reads a Settings.Global key. It works without special
     * permissions on most Android versions for READING (writing would
     * require WRITE_SECURE_SETTINGS, which we're intentionally not using).
     */
    fun getCurrentPrivateDnsHostname(context: Context): String? {
        return try {
            Settings.Global.getString(context.contentResolver, "private_dns_specifier")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns the current Private DNS mode: "off", "opportunistic" (automatic),
     * or "hostname" (strict mode, using the specifier below).
     */
    fun getPrivateDnsMode(context: Context): String? {
        return try {
            Settings.Global.getString(context.contentResolver, "private_dns_mode")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns true only if Private DNS is actually ON (mode = "hostname")
     * AND the hostname matches our target. The specifier value alone isn't
     * enough — Android keeps the last-typed hostname saved even after the
     * user switches mode to "off" or "automatic", so checking specifier
     * alone gives false positives.
     */
    fun isDnsCorrectlySet(context: Context): Boolean {
        val mode = getPrivateDnsMode(context)
        val current = getCurrentPrivateDnsHostname(context)
        return mode == "hostname" && current != null && current.equals(TARGET_DNS_HOSTNAME, ignoreCase = true)
    }

    /**
     * Call this on every app open (e.g. from MainActivity.onCreate/onResume).
     * If DNS has drifted from the target, nudges the user back to settings.
     */
    fun verifyAndPromptIfNeeded(context: Context) {
        Log.d("DnsSetupHelper", "verifyAndPromptIfNeeded() called, mode = ${getPrivateDnsMode(context)}, hostname = ${getCurrentPrivateDnsHostname(context)}")
        if (!isDnsCorrectlySet(context)) {
            Toast.makeText(
                context,
                "Private DNS isn't set to $TARGET_DNS_HOSTNAME — let's fix that.",
                Toast.LENGTH_LONG
            ).show()
            openPrivateDnsSettings(context)
        }
    }
}