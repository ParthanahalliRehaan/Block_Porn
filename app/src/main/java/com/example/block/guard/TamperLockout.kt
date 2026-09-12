package com.example.block.guard

import android.content.Context

/**
 * A punishment window that starts the moment a tamper attempt is detected
 * (opening App Info, Accessibility settings, Device Admin settings, the
 * uninstaller, etc. for this app). Unlike RemovalController's hour+PIN
 * flow — which is the *sanctioned* path to eventually remove protection —
 * this lockout exists purely to make quick/impulsive attempts fail: while
 * it's active, RemovalActivity won't even show the "Request to Remove"
 * button, so there's nothing to tap or back out of.
 *
 * Persisted to SharedPreferences (not just a var in the service) so it
 * survives the accessibility service being killed and restarted by the
 * OS, force-stopping the app from Recents, or a reboot.
 */
object TamperLockout {

    private const val PREFS = "block_tamper_lockout"
    private const val KEY_LOCKED_UNTIL = "locked_until"

    const val LOCKOUT_DURATION_MS = 60L * 60L * 1000L // 1 hour

    /** Starts (or restarts, if already active) a fresh one-hour lockout. */
    fun trigger(context: Context) {
        val until = System.currentTimeMillis() + LOCKOUT_DURATION_MS
        prefs(context).edit()
            .putLong(KEY_LOCKED_UNTIL, until)
            .apply()
    }

    fun isActive(context: Context): Boolean =
        remainingMs(context) > 0L

    fun remainingMs(context: Context): Long {
        val until = prefs(context).getLong(KEY_LOCKED_UNTIL, 0L)
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
