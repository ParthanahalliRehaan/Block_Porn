package com.example.block.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

object DeviceAdminManager {

    private const val TAG = "DeviceAdminManager"

    fun component(context: Context): ComponentName {
        return ComponentName(
            context,
            BlockDeviceAdminReceiver::class.java
        )
    }

    fun isAdminActive(context: Context): Boolean {
        val manager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                    as DevicePolicyManager

        return manager.isAdminActive(component(context))
    }

    fun requestAdminActivation(context: Context) {

        if (isAdminActive(context)) {
            return
        }

        val intent = Intent(
            DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
        ).apply {

            putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                component(context)
            )

            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Block uses Device Admin to prevent the app from being uninstalled until the removal process is completed."
            )
        }

        context.startActivity(intent)
    }

    fun deactivate(context: Context): Boolean {

        val manager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                    as DevicePolicyManager

        val admin = component(context)

        return try {

            if (manager.isAdminActive(admin)) {
                manager.removeActiveAdmin(admin)
                Log.i(TAG, "Device Admin deactivated")
            }

            true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to deactivate Device Admin",
                e
            )

            false
        }
    }
}