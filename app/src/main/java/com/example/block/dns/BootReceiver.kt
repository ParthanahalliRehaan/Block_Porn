package com.example.block.dns

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Restarts DnsWatcherService automatically after the device reboots.
 * Without this, the service only starts when the user manually opens
 * the app again — meaning DNS enforcement silently stops after every
 * restart until the app is reopened.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "Received action: ${intent.action}")
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val serviceIntent = Intent(context, DnsWatcherService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.d("BootReceiver", "Restarted DnsWatcherService after boot")
        }
    }
}