package com.lockdown.strictblocker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lockdown.strictblocker.core.Prefs
import com.lockdown.strictblocker.security.PinManager

/**
 * The vault keypad: 4 dots, monospace digits, no biometric bypass.
 * Submits automatically when the 4th digit lands.
 */
@Composable
fun PinPad(
    title: String,
    subtitle: String?,
    error: String?,
    resetKey: Int,
    onComplete: (String) -> Unit,
    onForgot: (() -> Unit)? = null
) {
    var entered by remember(resetKey) { mutableStateOf("") }

    fun press(key: String) {
        when (key) {
            "⌫" -> entered = entered.dropLast(1)
            else -> if (entered.length < 4) {
                entered += key
                if (entered.length == 4) onComplete(entered)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                color = MutedWhite,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { i ->
                val filled = i < entered.length
                val color by animateColorAsState(
                    if (filled) ElectricLime else CharcoalLight,
                    label = "dot$i"
                )
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.height(24.dp)) {
            if (error != null) {
                Text(error, color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(if (key.isEmpty()) VaultBlack else Charcoal)
                            .clickable(enabled = key.isNotEmpty()) { press(key) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            key,
                            color = PureWhite,
                            fontSize = if (key == "⌫") 22.sp else 28.sp,
                            fontFamily = MonoFont,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        if (onForgot != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onForgot) {
                Text("Forgot PIN?", color = MutedWhite)
            }
        }
    }
}

/** First-run PIN creation: enter, confirm, then an optional recovery question. */
@Composable
fun CreatePinScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val pinManager = remember { PinManager.get(context) }
    var stage by remember { mutableIntStateOf(0) }
    var firstPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var resetKey by remember { mutableIntStateOf(0) }

    when (stage) {
        0 -> PinPad(
            title = "Create your vault code",
            subtitle = "4 digits guard your lists, settings and uninstall.",
            error = error,
            resetKey = resetKey,
            onComplete = {
                firstPin = it
                error = null
                stage = 1
                resetKey++
            }
        )
        1 -> PinPad(
            title = "Repeat the code",
            subtitle = null,
            error = error,
            resetKey = resetKey,
            onComplete = { pin ->
                if (pin == firstPin) {
                    pinManager.setPin(pin)
                    stage = 2
                } else {
                    error = "Codes don't match — start over"
                    stage = 0
                    resetKey++
                }
            }
        )
        else -> SecurityQuestionSetup(pinManager, onDone)
    }
}

@Composable
private fun SecurityQuestionSetup(pinManager: PinManager, onDone: () -> Unit) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultBlack)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Recovery question", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "If you ever forget the code, this question is one way back in. " +
                "Recovery only works when Strict Mode is not running.",
            color = MutedWhite,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Question") },
            placeholder = { Text("e.g. Name of my first school?") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            label = { Text("Answer") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                pinManager.setSecurityQuestion(question.trim(), answer.trim())
                onDone()
            },
            enabled = question.isNotBlank() && answer.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricLime,
                contentColor = VaultBlack
            ),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save and continue", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Skip", color = MutedWhite)
        }
    }
}

/**
 * PIN gate for sensitive areas (Settings tab, PIN change).
 * Handles wrong-attempt lockout and the Forgot PIN recovery rules.
 */
@Composable
fun PinUnlock(onUnlocked: () -> Unit, onPinCleared: () -> Unit) {
    val context = LocalContext.current
    val pinManager = remember { PinManager.get(context) }
    val prefs = remember { Prefs.get(context) }
    var error by remember { mutableStateOf<String?>(null) }
    var resetKey by remember { mutableIntStateOf(0) }
    var showForgot by remember { mutableStateOf(false) }

    PinPad(
        title = "Enter vault code",
        subtitle = "This area is PIN protected.",
        error = error,
        resetKey = resetKey,
        onComplete = { pin ->
            when (val result = pinManager.verify(pin)) {
                PinManager.VerifyResult.Success -> onUnlocked()
                is PinManager.VerifyResult.Wrong -> {
                    error = "Wrong code — ${result.attemptsLeft} attempts left"
                    resetKey++
                }
                is PinManager.VerifyResult.Locked -> {
                    error = "Locked. Try again in ${(result.remainingMillis / 1000) + 1}s"
                    resetKey++
                }
            }
        },
        onForgot = { showForgot = true }
    )

    if (showForgot) {
        ForgotPinDialog(
            pinManager = pinManager,
            strictActive = prefs.isStrictActive(),
            onClose = { showForgot = false },
            onRecovered = {
                showForgot = false
                onPinCleared()
            }
        )
    }
}

@Composable
private fun ForgotPinDialog(
    pinManager: PinManager,
    strictActive: Boolean,
    onClose: () -> Unit,
    onRecovered: () -> Unit
) {
    var answer by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val question = pinManager.securityQuestion()
    val now = System.currentTimeMillis()
    val requestedAt = pinManager.recoveryRequestedAt
    val waitDone = requestedAt > 0 && now - requestedAt >= PinManager.RECOVERY_WAIT_MILLIS
    val hoursLeft = if (requestedAt > 0 && !waitDone) {
        ((PinManager.RECOVERY_WAIT_MILLIS - (now - requestedAt)) / 3_600_000.0)
    } else 0.0

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Charcoal,
        title = { Text("Forgot PIN") },
        text = {
            Column {
                when {
                    strictActive -> Text(
                        "PIN recovery is disabled while Strict Mode is active. " +
                            "It unlocks when the timer ends."
                    )
                    question != null -> {
                        Text(question)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = answer,
                            onValueChange = { answer = it },
                            label = { Text("Your answer") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    waitDone -> Text("Your 24-hour wait is over. You can reset the PIN now.")
                    requestedAt > 0 -> Text(
                        "Reset requested. It unlocks in about ${"%.1f".format(hoursLeft)} hours."
                    )
                    else -> Text(
                        "No recovery question is set. You can request a reset and wait 24 hours."
                    )
                }
                if (message != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(message!!, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            when {
                strictActive -> TextButton(onClick = onClose) { Text("OK") }
                question != null -> Button(
                    onClick = {
                        if (pinManager.verifySecurityAnswer(answer)) {
                            pinManager.clearPin()
                            onRecovered()
                        } else {
                            message = "That answer doesn't match."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricLime,
                        contentColor = VaultBlack
                    )
                ) { Text("Recover") }
                waitDone -> Button(
                    onClick = {
                        pinManager.clearPin()
                        onRecovered()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricLime,
                        contentColor = VaultBlack
                    )
                ) { Text("Reset PIN") }
                requestedAt == 0L -> Button(
                    onClick = {
                        pinManager.recoveryRequestedAt = System.currentTimeMillis()
                        message = "Reset requested. Come back in 24 hours."
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricLime,
                        contentColor = VaultBlack
                    )
                ) { Text("Request 24-hour reset") }
                else -> TextButton(onClick = onClose) { Text("OK") }
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Cancel", color = MutedWhite) }
        }
    )
}
