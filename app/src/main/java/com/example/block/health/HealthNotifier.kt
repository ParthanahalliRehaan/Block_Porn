package com.example.block.health

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object HealthNotifier {

    private const val CHANNEL_ID = "block_health_channel"
    private const val NOTIFICATION_ID_OVERLAY = 1101

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                context.getSystemService(NotificationManager::class.java)

            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Block Protection Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description =
                        "Warns when a permission Block needs to stay active gets turned off"
                }

                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * "Display over other apps" is what lets DnsOverlayManager and
     * BatteryOverlayManager show their full-screen warnings at all — if it's
     * off, we can't overlay a warning about it being off, so this uses a
     * plain high-priority notification instead.
     */
    fun notifyOverlayPermissionMissing(context: Context) {
        ensureChannel(context)

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Block's warning screens are off")
            .setContentText("\"Display over other apps\" was turned off — tap to turn it back on.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_OVERLAY, notification)
    }

    fun clearOverlayPermissionWarning(context: Context) {
        NotificationManagerCompat.from(context)
            .cancel(NOTIFICATION_ID_OVERLAY)
    }
}
