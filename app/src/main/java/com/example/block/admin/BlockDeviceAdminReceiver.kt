package com.example.block.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.block.MainActivity
import com.example.block.R
import com.example.block.removal.RemovalController

class BlockDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(
            context,
            "Block protection enabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Shown by the system on the "Deactivate this device admin app?" screen,
     * right before the user can confirm. This is the one hook Android gives
     * a normal (non device-owner) admin to warn at the point of disabling —
     * it doesn't block the action, only asks for a second thought.
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.device_admin_disable_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(
            context,
            "Block protection disabled",
            Toast.LENGTH_SHORT
        ).show()

        // If this deactivation didn't come from the app's own sanctioned
        // hour + PIN removal flow, immediately bring the user back to the
        // "Enable Protection" screen instead of leaving Block silently off.
        if (!RemovalController.isLegitimateFlowActive()) {
            val reactivate = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_PROTECTION_WAS_DISABLED, true)
            }
            context.startActivity(reactivate)
        }
    }
}
