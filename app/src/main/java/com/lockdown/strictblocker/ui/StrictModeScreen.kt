package com.lockdown.strictblocker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lockdown.strictblocker.core.Prefs
import com.lockdown.strictblocker.core.formatRemaining
import com.lockdown.strictblocker.engine.StrictMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tab 2 — Strict Mode.
 * Pick a duration, hit ACTIVATE LOCKDOWN, and there is no way out until the
 * timer ends: lists freeze, Settings is guarded, uninstall is blocked.
 */
@Composable
fun StrictModeScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val active by vm.strictActive.collectAsState()
    val remaining by vm.strictRemaining.collectAsState()

    Column(
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
            ActiveStrictView(remaining = remaining, total = prefs.strictTotalMillis, endMillis = prefs.strictEndMillis)
        } else {
            ActivationView(vm)
        }
    }
}

@Composable
private fun ActiveStrictView(remaining: Long, total: Long, endMillis: Long) {
    val fraction = if (total > 0) (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 1f
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
        Canvas(Modifier.size(260.dp)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = CharcoalLight,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = ElectricLime,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatRemaining(remaining),
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                color = PureWhite
            )
            Text("remaining", color = MutedWhite)
        }
    }
    Spacer(Modifier.height(24.dp))
    Text(
        "LOCKDOWN IS ACTIVE",
        color = ElectricLime,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp
    )
    Spacer(Modifier.height(8.dp))
    val endText = SimpleDateFormat("EEE, h:mm a", Locale.getDefault()).format(Date(endMillis))
    Text(
        "Ends $endText. Until then: lists are frozen, Settings is guarded, " +
            "and LOCKDOWN cannot be uninstalled, force-stopped or opened up. " +
            "There is no override — that's the point.",
        color = MutedWhite,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun ActivationView(vm: MainViewModel) {
    var selectedPreset by remember { mutableStateOf(StrictMode.PRESETS[2]) } // 1 hour
    var useCustom by remember { mutableStateOf(false) }
    var customHours by remember { mutableIntStateOf(4) }
    var showConfirm by remember { mutableStateOf(false) }

    val duration = if (useCustom) customHours * 3_600_000L else selectedPreset.second

    Text(
        "When Strict Mode is on, it cannot be turned off — not by you, not by " +
            "rebooting, not by anyone — until the timer runs out.",
        color = MutedWhite,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))

    // Preset chips
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StrictMode.PRESETS.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { preset ->
                    val selected = !useCustom && selectedPreset == preset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (selected) ElectricLime else Charcoal,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedPreset = preset
                                useCustom = false
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            preset.first,
                            color = if (selected) VaultBlack else PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    Spacer(Modifier.height(16.dp))

    // Custom duration
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Charcoal, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (useCustom) ElectricLime else CharcoalLight,
                RoundedCornerShape(12.dp)
            )
            .clickable { useCustom = true }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Custom", fontWeight = FontWeight.Bold)
                Text(
                    "Up to ${StrictMode.MAX_CUSTOM_HOURS} hours",
                    color = MutedWhite,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            TextButton(onClick = {
                useCustom = true
                customHours = (customHours - 1).coerceAtLeast(1)
            }) { Text("−", fontSize = 22.sp, color = PureWhite) }
            Text(
                "${customHours}h",
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            TextButton(onClick = {
                useCustom = true
                customHours = (customHours + 1).coerceAtMost(StrictMode.MAX_CUSTOM_HOURS)
            }) { Text("+", fontSize = 22.sp, color = PureWhite) }
        }
    }
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
    Spacer(Modifier.height(10.dp))
    Text(
        "Duration: ${formatRemaining(duration)}",
        color = MutedWhite,
        fontFamily = MonoFont
    )

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = Charcoal,
            title = { Text("Seal the vault?") },
            text = {
                Text(
                    "Strict Mode will run for ${formatRemaining(duration)}. " +
                        "During that time you cannot edit your lists, open Settings, " +
                        "remove LOCKDOWN, or stop it early. This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.activateStrict(duration)
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
