package com.example.block.guard

import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object TamperGuardStatus {

    fun isEnabled(context: Context): Boolean {

        val expectedComponent =
            "${context.packageName}/${TamperGuardAccessibilityService::class.java.name}"

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        if (TextUtils.isEmpty(enabledServices)) return false

        return enabledServices
            .split(':')
            .any { it.equals(expectedComponent, ignoreCase = true) }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
