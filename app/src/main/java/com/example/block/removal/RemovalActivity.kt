package com.example.block.removal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.block.admin.DeviceAdminManager
import com.example.block.security.PinManager
import com.example.block.ui.theme.BlockTheme
import kotlinx.coroutines.delay

class RemovalActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BlockTheme {
                RemovalFlow()
            }
        }
    }

    override fun onStop() {
        super.onStop()

        if (!isChangingConfigurations) {
            RemovalController.reset()
        }
    }

    @Composable
    private fun RemovalFlow() {

        var phase by remember {

            mutableStateOf(

                if (RemovalController.isRunning()) {
                    RemovalPhase.COUNTDOWN
                } else {
                    RemovalPhase.START
                }

            )
        }

        when (phase) {

            RemovalPhase.START -> {

                StartRemovalScreen(
                    onStart = {

                        RemovalController.start()

                        phase =
                            RemovalPhase.COUNTDOWN
                    }
                )
            }

            RemovalPhase.COUNTDOWN -> {

                CountdownScreen(
                    onComplete = {

                        phase =
                            RemovalPhase.PIN
                    }
                )
            }

            RemovalPhase.PIN -> {

                PinScreen(
                    onSubmit = { pin ->

                        if (
                            PinManager.verifyPin(
                                this,
                                pin
                            )
                        ) {

                            uninstallAfterDeactivation()

                            true

                        } else {

                            false
                        }
                    }
                )
            }
        }
    }

    private fun uninstallAfterDeactivation() {

        // Mark this as the sanctioned path *before* touching Device Admin so
        // the tamper guard lets the resulting uninstall prompt through
        // instead of treating it as a bypass attempt.
        RemovalController.markLegitimateFlowStarting()

        if (!DeviceAdminManager.deactivate(this)) {
            return
        }

        window.decorView.postDelayed({

            val uninstallIntent =
                Intent(
                    Intent.ACTION_DELETE,
                    Uri.parse(
                        "package:$packageName"
                    )
                )

            startActivity(
                uninstallIntent
            )

        }, 500L)
    }

    private enum class RemovalPhase {
        START,
        COUNTDOWN,
        PIN
    }
}

@Composable
private fun StartRemovalScreen(
    onStart: () -> Unit
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
            text = "Remove Block",
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
                "To remove protection, you must remain on this screen for one full hour."
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        Button(
            onClick = onStart
        ) {
            Text("Request to Remove")
        }
    }
}

@Composable
private fun CountdownScreen(
    onComplete: () -> Unit
) {

    var remaining by remember {

        mutableLongStateOf(
            RemovalController.remainingMs()
        )
    }

    LaunchedEffect(Unit) {

        while (true) {

            remaining =
                RemovalController
                    .remainingMs()

            if (
                RemovalController
                    .isComplete()
            ) {

                onComplete()

                break
            }

            delay(250L)
        }
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
            text =
                "Keep this screen open.",
            style =
                MaterialTheme
                    .typography
                    .titleLarge
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        Text(
            text =
                formatTime(remaining),

            style =
                MaterialTheme
                    .typography
                    .displayLarge
        )

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        Text(
            text =
                "Leaving this screen resets the timer."
        )
    }
}

@Composable
private fun PinScreen(
    onSubmit:
        (String) -> Boolean
) {

    var pin by remember {
        mutableStateOf("")
    }

    var error by remember {
        mutableStateOf(false)
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
            text = "Enter PIN",
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
                    error = false
                }
            },

            label = {
                Text("PIN")
            },

            visualTransformation =
                PasswordVisualTransformation()
        )

        if (error) {

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text("Incorrect PIN")
        }

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        Button(
            onClick = {

                if (pin.isNotEmpty()) {

                    error =
                        !onSubmit(pin)
                }
            }
        ) {

            Text("Unlock")
        }
    }
}

private fun formatTime(
    milliseconds: Long
): String {

    val totalSeconds =
        milliseconds / 1000L

    val hours =
        totalSeconds / 3600L

    val minutes =
        (totalSeconds % 3600L) / 60L

    val seconds =
        totalSeconds % 60L

    return String.format(
        "%02d:%02d:%02d",
        hours,
        minutes,
        seconds
    )
}