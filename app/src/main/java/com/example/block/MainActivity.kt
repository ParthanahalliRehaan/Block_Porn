package com.example.block

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.block.admin.DeviceAdminManager
import com.example.block.dns.DnsOverlayManager
import com.example.block.dns.DnsSetupHelper
import com.example.block.dns.DnsWatcherService
import com.example.block.guard.TamperGuardStatus
import com.example.block.health.AutoStartHelper
import com.example.block.health.BatteryOverlayManager
import com.example.block.health.SystemHealthChecker
import com.example.block.removal.RemovalActivity
import com.example.block.security.PinManager
import com.example.block.ui.theme.BlockTheme
import android.widget.Toast

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        if (
            !DnsOverlayManager
                .hasOverlayPermission(this)
        ) {

            DnsOverlayManager
                .requestOverlayPermission(this)
        }

        if (
            !SystemHealthChecker
                .isBatteryUnrestricted(this)
        ) {

            BatteryOverlayManager
                .requestUnrestrictedBattery(this)
        }

        val serviceIntent =
            Intent(
                this,
                DnsWatcherService::class.java
            )

        ContextCompat
            .startForegroundService(
                this,
                serviceIntent
            )

        enableEdgeToEdge()

        if (
            intent?.getBooleanExtra(
                EXTRA_PROTECTION_WAS_DISABLED,
                false
            ) == true
        ) {

            Toast.makeText(
                this,
                "Protection was turned off outside Block. Please re-enable it.",
                Toast.LENGTH_LONG
            ).show()
        }

        renderScreen()
    }

    override fun onResume() {
        super.onResume()

        DnsSetupHelper
            .verifyAndPromptIfNeeded(this)

        renderScreen()
    }

    private fun renderScreen() {

        setContent {
            BlockTheme {

                MainScreen(
                    hasPin =
                        PinManager.hasPin(this),

                    isAdminActive =
                        DeviceAdminManager
                            .isAdminActive(this),

                    isGuardEnabled =
                        TamperGuardStatus
                            .isEnabled(this),

                    isAutostartAcknowledged =
                        AutoStartHelper
                            .hasAcknowledged(this),

                    onPinCreated = { pin ->

                        PinManager.setPin(
                            this,
                            pin
                        )

                        DeviceAdminManager
                            .requestAdminActivation(
                                this
                            )
                    },

                    onEnableProtection = {

                        DeviceAdminManager
                            .requestAdminActivation(
                                this
                            )
                    },

                    onEnableGuard = {

                        TamperGuardStatus
                            .openAccessibilitySettings(
                                this
                            )
                    },

                    onOpenAutostartSettings = {

                        AutoStartHelper
                            .openAutoStartSettings(
                                this
                            )
                    },

                    onAcknowledgeAutostart = {

                        AutoStartHelper
                            .acknowledge(this)

                        renderScreen()
                    },

                    onRequestRemoval = {

                        startActivity(
                            Intent(
                                this,
                                RemovalActivity::class.java
                            )
                        )
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_PROTECTION_WAS_DISABLED = "protection_was_disabled"
    }
}

@Composable
private fun MainScreen(
    hasPin: Boolean,
    isAdminActive: Boolean,
    isGuardEnabled: Boolean,
    isAutostartAcknowledged: Boolean,
    onPinCreated: (String) -> Unit,
    onEnableProtection: () -> Unit,
    onEnableGuard: () -> Unit,
    onOpenAutostartSettings: () -> Unit,
    onAcknowledgeAutostart: () -> Unit,
    onRequestRemoval: () -> Unit
) {

    when {

        !hasPin -> {

            PinSetupScreen(
                onPinCreated =
                    onPinCreated
            )
        }

        !isAdminActive -> {

            EnableProtectionScreen(
                onEnableProtection =
                    onEnableProtection
            )
        }

        !isGuardEnabled -> {

            EnableGuardScreen(
                onEnableGuard =
                    onEnableGuard
            )
        }

        !isAutostartAcknowledged -> {

            EnableAutostartScreen(
                onOpenSettings =
                    onOpenAutostartSettings,

                onAcknowledge =
                    onAcknowledgeAutostart
            )
        }

        else -> {

            ProtectedHomeScreen(
                onRequestRemoval =
                    onRequestRemoval
            )
        }
    }
}

@Composable
private fun EnableAutostartScreen(
    onOpenSettings: () -> Unit,
    onAcknowledge: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "Allow Autostart",
            style =
                MaterialTheme
                    .typography
                    .headlineMedium
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        Text(
            text =
                "Many phones stop background apps from starting after a restart unless autostart is allowed for them. Turn this on for Block so protection survives a reboot. Android doesn't let apps check this setting, so tap below, enable it, then confirm here."
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        Button(
            onClick =
                onOpenSettings
        ) {

            Text("Open Autostart Settings")
        }

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        Button(
            onClick =
                onAcknowledge
        ) {

            Text("I've Enabled It")
        }
    }
}

@Composable
private fun EnableGuardScreen(
    onEnableGuard: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "One More Step",
            style =
                MaterialTheme
                    .typography
                    .headlineMedium
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        Text(
            text =
                "Turn on the Block Tamper Guard accessibility service. Without it, protection can still be turned off directly from Settings, bypassing the removal process."
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        Button(
            onClick =
                onEnableGuard
        ) {

            Text("Open Accessibility Settings")
        }
    }
}

@Composable
private fun PinSetupScreen(
    onPinCreated: (String) -> Unit
) {

    var pin by remember {
        mutableStateOf("")
    }

    var confirmPin by remember {
        mutableStateOf("")
    }

    var error by remember {
        mutableStateOf("")
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "Create Protection PIN",
            style =
                MaterialTheme
                    .typography
                    .headlineMedium
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        OutlinedTextField(
            value = pin,

            onValueChange = {

                if (
                    it.length <= 12 &&
                    it.all(Char::isDigit)
                ) {

                    pin = it
                    error = ""
                }
            },

            label = {
                Text("PIN")
            },

            visualTransformation =
                PasswordVisualTransformation()
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        OutlinedTextField(
            value = confirmPin,

            onValueChange = {

                if (
                    it.length <= 12 &&
                    it.all(Char::isDigit)
                ) {

                    confirmPin = it
                    error = ""
                }
            },

            label = {
                Text("Confirm PIN")
            },

            visualTransformation =
                PasswordVisualTransformation()
        )

        if (error.isNotEmpty()) {

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text(error)
        }

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        Button(
            onClick = {

                when {

                    pin.length < 4 -> {
                        error =
                            "PIN must be at least 4 digits"
                    }

                    pin != confirmPin -> {
                        error =
                            "PINs do not match"
                    }

                    else -> {
                        onPinCreated(pin)
                    }
                }
            }
        ) {

            Text("Save PIN")
        }
    }
}

@Composable
private fun EnableProtectionScreen(
    onEnableProtection: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "Enable Protection",
            style =
                MaterialTheme
                    .typography
                    .headlineMedium
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        Text(
            text =
                "Block needs Device Admin protection to prevent normal uninstall before the removal process is completed."
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        Button(
            onClick =
                onEnableProtection
        ) {

            Text("Enable Protection")
        }
    }
}

@Composable
private fun ProtectedHomeScreen(
    onRequestRemoval: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "Block is active",
            style =
                MaterialTheme
                    .typography
                    .headlineMedium
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        Text(
            text =
                "Protection is enabled."
        )

        Spacer(
            modifier =
                Modifier.height(32.dp)
        )

        Button(
            onClick =
                onRequestRemoval
        ) {

            Text("Request to Remove")
        }
    }
}