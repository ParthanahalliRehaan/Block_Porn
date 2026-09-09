package com.example.block.removal

object RemovalController {

    const val ONE_HOUR_MS =
        60L * 60L * 1000L

    // How long a sanctioned deactivate-then-uninstall sequence is allowed to
    // run before the tamper guard resumes treating admin/uninstall screens
    // as an attempted bypass. Kept short and only ever started by our own
    // code right before we call DeviceAdminManager.deactivate() ourselves.
    private const val LEGITIMATE_WINDOW_MS = 90L * 1000L

    private var startedAt: Long? = null
    private var legitimateFlowExpiresAt: Long? = null

    /**
     * Call this immediately before programmatically deactivating Device Admin
     * as part of the completed PIN + one-hour removal flow, so the tamper
     * guard doesn't intercept the uninstall prompt we're about to trigger.
     */
    fun markLegitimateFlowStarting() {
        legitimateFlowExpiresAt =
            System.currentTimeMillis() + LEGITIMATE_WINDOW_MS
    }

    fun isLegitimateFlowActive(): Boolean {
        val expiresAt =
            legitimateFlowExpiresAt ?: return false

        return System.currentTimeMillis() < expiresAt
    }

    fun start() {
        startedAt =
            System.currentTimeMillis()
    }

    fun reset() {
        startedAt = null
    }

    fun isRunning(): Boolean {
        return startedAt != null
    }

    fun remainingMs(): Long {

        val start =
            startedAt
                ?: return ONE_HOUR_MS

        val elapsed =
            System.currentTimeMillis() - start

        return (
                ONE_HOUR_MS - elapsed
                ).coerceAtLeast(0L)
    }

    fun isComplete(): Boolean {

        return isRunning() &&
                remainingMs() <= 0L
    }
}