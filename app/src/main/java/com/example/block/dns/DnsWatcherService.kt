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
import com.example.block.guard.AccessibilityGuardOverlayManager
import com.example.block.guard.TamperGuardStatus
import com.example.block.health.BatteryOverlayManager
import com.example.block.health.HealthNotifier
import com.example.block.health.SystemHealthChecker

/**
 * Foreground service that polls Private DNS, the Tamper Guard accessibility
 * service, and battery optimization every few seconds, showing a full-screen
 * overlay for whichever protection is off. If it's found to be off or
 * changed, attempts to directly launch the relevant settings screen.
 *
 * IMPORTANT LIMITATION (read this before assuming it's broken):
 * Android 10+ restricts apps from launching activities from the background
 * without a preceding user interaction. This is a deliberate OS security
 * restriction, not a bug in this code. On some devices/Android versions
 * this direct launch may work; on others (especially MIUI) it may be
 * silently blocked. If that happens, the sanctioned alternative is a
 * full-screen intent notification (the same mechanism alarm/call apps
 * use) — ask to switch to that approach if this doesn't work reliably.
 *
 * ACCESSIBILITY-SPECIFIC NOTE: on Android 13+, a sideloaded app's
 * accessibility service can get silently kicked back to "off" by the OS's
 * Restricted Settings feature, not by anything in this app's own code —
 * that's what "it disables itself without asking" usually is. The fix on
 * the phone itself is: Settings → Apps → Block → the "⋮" overflow menu →
 * "Allow restricted settings", once, before enabling accessibility. The
 * overlay below can only prompt the user back to the toggle; it can't
 * bypass that OS restriction.
 */
class DnsWatcherService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val pollIntervalMs = 2_000L // 3 seconds — fast enough that turning any protection off is noticed almost immediately

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

        val guardOk = checkAccessibilityGuard(dnsOk)
        checkBatteryOptimization(dnsOk, guardOk)
    }

    /**
     * Checks whether the Tamper Guard accessibility service is on. Returns
     * true if it's on (or we're deliberately not showing anything for it
     * right now), false if the overlay is up because it's off — used by
     * checkBatteryOptimization so we never stack two full-screen overlays.
     */
    private fun checkAccessibilityGuard(dnsOk: Boolean): Boolean {
        val guardOk = TamperGuardStatus.isEnabled(this)

        if (guardOk) {
            AccessibilityGuardOverlayManager.hideOverlay(this)
            return true
        }

        // DNS being broken means nothing is being filtered at all — that
        // takes priority over the accessibility overlay.
        if (!dnsOk) {
            AccessibilityGuardOverlayManager.hideOverlay(this)
            return false
        }

        // Just sent the user to the settings toggle — give them a short
        // window to actually flip it before covering the screen again.
        if (AccessibilityGuardOverlayManager.isInGracePeriod()) {
            return false
        }

        Log.d("DnsWatcherService", "Accessibility guard off — showing overlay")
        AccessibilityGuardOverlayManager.showOverlay(this)
        return false
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

    private fun checkBatteryOptimization(dnsOk: Boolean, guardOk: Boolean) {
        val batteryOk = SystemHealthChecker.isBatteryUnrestricted(this)

        if (batteryOk) {
            BatteryOverlayManager.hideOverlay(this)
            return
        }

        // Don't stack two full-screen overlays — the DNS warning takes
        // priority since broken DNS means nothing is being filtered at all,
        // and the accessibility-guard warning takes priority over this one
        // too (it's the more urgent problem).
        if (dnsOk && guardOk) {
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