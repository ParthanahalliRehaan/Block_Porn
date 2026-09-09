package com.example.block.dns

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.block.health.BatteryOverlayManager
import com.example.block.health.HealthNotifier
import com.example.block.health.SystemHealthChecker

/**
 * Foreground service that polls the Private DNS setting every 10 seconds.
 * If it's found to be off or changed, attempts to directly launch the
 * Private DNS settings screen.
 *
 * IMPORTANT LIMITATION (read this before assuming it's broken):
 * Android 10+ restricts apps from launching activities from the background
 * without a preceding user interaction. This is a deliberate OS security
 * restriction, not a bug in this code. On some devices/Android versions
 * this direct launch may work; on others (especially MIUI) it may be
 * silently blocked. If that happens, the sanctioned alternative is a
 * full-screen intent notification (the same mechanism alarm/call apps
 * use) — ask to switch to that approach if this doesn't work reliably.
 */
class DnsWatcherService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val pollIntervalMs = 10_000L // 10 seconds

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkDnsAndAct()
            handler.postDelayed(this, pollIntervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        handler.post(pollRunnable)
        Log.d("DnsWatcherService", "Service created, polling started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY tells Android to try to recreate the service if it gets killed
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
        Log.d("DnsWatcherService", "Service destroyed, polling stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkDnsAndAct() {
        val mode = DnsSetupHelper.getPrivateDnsMode(this)
        val hostname = DnsSetupHelper.getCurrentPrivateDnsHostname(this)
        Log.d("DnsWatcherService", "Poll check: mode=$mode, hostname=$hostname")

        val dnsOk = DnsSetupHelper.isDnsCorrectlySet(this)

        if (!dnsOk) {
            Log.d("DnsWatcherService", "DNS incorrect — showing overlay")
            DnsOverlayManager.showOverlay(this)
        } else {
            DnsOverlayManager.hideOverlay(this)
        }

        checkOverlayPermission()
        checkBatteryOptimization(dnsOk)
    }

    /**
     * "Display over other apps" being off means neither this overlay nor the
     * battery one below can be drawn at all — so this uses a notification
     * instead, which doesn't need that permission.
     */
    private fun checkOverlayPermission() {
        if (!SystemHealthChecker.hasOverlayPermission(this)) {
            HealthNotifier.notifyOverlayPermissionMissing(this)
        } else {
            HealthNotifier.clearOverlayPermissionWarning(this)
        }
    }

    private fun checkBatteryOptimization(dnsOk: Boolean) {
        val batteryOk = SystemHealthChecker.isBatteryUnrestricted(this)

        if (batteryOk) {
            BatteryOverlayManager.hideOverlay(this)
            return
        }

        // Don't stack two full-screen overlays — the DNS warning takes
        // priority since broken DNS means nothing is being filtered at all.
        if (dnsOk) {
            BatteryOverlayManager.showOverlay(this)
        } else {
            BatteryOverlayManager.hideOverlay(this)
        }
    }

    private fun startForegroundWithNotification() {
        val channelId = "dns_watcher_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "DNS Watcher",
                NotificationManager.IMPORTANCE_MIN // as unobtrusive as Android allows
            )
            channel.description = "Keeps Private DNS enforcement active"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Block is active")
            .setContentText("Monitoring DNS protection")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(1, notification)
    }
}