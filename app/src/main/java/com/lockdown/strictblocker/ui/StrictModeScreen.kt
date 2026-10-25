package com.lockdown.strictblocker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tab 2 — Strict Mode, PIN-only lock.
 *
 * Set up your lists, hit ACTIVATE LOCKDOWN, and the lock stays on
 * indefinitely — no timer, no expiry, not even across reboots. The ONLY way
 * to change anything afterwards is to enter the vault code on this tab,
 * which lifts the lock. Forgot the code? The recovery question (set at
 * first launch) lets you choose a new code while the lock stays on.
 */
@Composable
fun StrictModeScreen(vm: MainViewModel) {
    val active by vm.strictActive.collectAsState()
    var unlocking by remember { mutableStateOf(false) }
    var resettingPin by remember { mutableStateOf(false) }

    when {
        resettingPin -> CreatePinScreen(onDone = { resettingPin = false })
        unlocking -> PinUnlock(
            onUnlocked = {
                vm.deactivateStrict()
                unlocking = false
            },
            // PIN was recovered and cleared while locked: pick a new code.
            // The lock stays on until the new code unlocks it.
            onPinCleared = {
                unlocking = false
                resettingPin = true
            }
        )
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .background(VaultBlack)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Strict Mode",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))

            if (active) {
                LockedView(onUnlock = { unlocking = true })
            } else {
                ActivationView(vm)
            }
        }
    }
}

@Composable
private fun LockedView(onUnlock: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
        Canvas(Modifier.size(260.dp)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = ElectricLime,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "LOCKED",
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                color = PureWhite
            )
            Text("no timer, no expiry", color = MutedWhite)
        }
    }
    Spacer(Modifier.height(24.dp))
    Text(
        "LOCKDOWN IS LOCKED",
        color = ElectricLime,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Lists are frozen, Settings is guarded, and LOCKDOWN cannot be " +
            "uninstalled, force-stopped or opened up. The only way to change " +
            "anything is your vault code — there is no timer to wait out.",
        color = MutedWhite,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(28.dp))
    Button(
        onClick = onUnlock,
        colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = VaultBlack),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text("Unlock with PIN", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
    }
}

@Composable
private fun ActivationView(vm: MainViewModel) {
    var showConfirm by remember { mutableStateOf(false) }

    Text(
        "When Strict Mode is on, it stays on — no timer, no expiry, not " +
            "even across reboots. The only way to change anything afterwards " +
            "is your vault code.",
        color = MutedWhite,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(28.dp))

    Button(
        onClick = { showConfirm = true },
        colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = VaultBlack),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text("ACTIVATE LOCKDOWN", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = Charcoal,
            title = { Text("Seal the vault?") },
            text = {
                Text(
                    "Once locked, you cannot edit your lists, open Settings, " +
                        "remove LOCKDOWN, or stop it until you enter your vault " +
                        "code. There is no timer to wait out — make sure you " +
                        "know your code and your recovery question."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.activateStrict()
                        showConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricLime,
                        contentColor = VaultBlack
                    )
                ) { Text("Activate", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Not yet", color = MutedWhite)
                }
            }
        )
    }
}
