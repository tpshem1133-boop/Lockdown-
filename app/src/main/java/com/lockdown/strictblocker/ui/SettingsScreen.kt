package com.lockdown.strictblocker.ui

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lockdown.strictblocker.engine.AdminReceiver
import com.lockdown.strictblocker.engine.Permissions
import com.lockdown.strictblocker.security.PinManager

/**
 * Tab 3 — Settings. The whole tab sits behind the vault PIN, and the
 * protection-weakening actions are disabled while Strict Mode runs.
 */
@Composable
fun SettingsScreen(vm: MainViewModel, refreshKey: Int, onPinCleared: () -> Unit) {
    var unlocked by remember { mutableStateOf(false) }
    var changingPin by remember { mutableStateOf(false) }
    var pinChangedNote by remember { mutableStateOf(false) }
    if (!unlocked) {
        PinUnlock(onUnlocked = { unlocked = true }, onPinCleared = onPinCleared)
        return
    }
    if (changingPin) {
        ChangePinFlow(
            onFinished = { changed ->
                changingPin = false
                if (changed) pinChangedNote = true
            }
        )
        return
    }
    SettingsContent(
        vm = vm,
        refreshKey = refreshKey,
        pinChangedNote = pinChangedNote,
        onChangePin = { changingPin = true }
    )
}

@Composable
private fun SettingsContent(
    vm: MainViewModel,
    refreshKey: Int,
    pinChangedNote: Boolean,
    onChangePin: () -> Unit
) {
    val context = LocalContext.current
    val strictActive by vm.strictActive.collectAsState()
    @Suppress("UNUSED_EXPRESSION") refreshKey

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultBlack)
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            if (strictActive) "Strict Mode is active — protection cannot be weakened."
            else "PIN verified. Handle with care.",
            color = if (strictActive) ElectricLime else MutedWhite
        )
        Spacer(Modifier.height(22.dp))

        SectionHeader("PERMISSIONS")
        PermissionRow("Usage Access", Permissions.hasUsageAccess(context)) {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        PermissionRow("Accessibility Service", Permissions.isAccessibilityEnabled(context)) {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        PermissionRow("Display Over Apps", Permissions.canDrawOverlays(context)) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        PermissionRow("Device Admin", Permissions.isAdminActive(context)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(context, AdminReceiver::class.java)
                )
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "LOCKDOWN needs device admin for uninstall protection during Strict Mode."
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        PermissionRow("Battery Exemption", Permissions.isIgnoringBatteryOptimizations(context)) {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        PermissionRow("Device Owner (maximum protection)", Permissions.isDeviceOwner(context), null)

        Spacer(Modifier.height(14.dp))
        DeviceOwnerCard(context, Permissions.isDeviceOwner(context))

        Spacer(Modifier.height(22.dp))
        SectionHeader("VAULT CODE")
        Button(
            onClick = onChangePin,
            enabled = !strictActive,
            colors = ButtonDefaults.buttonColors(containerColor = Charcoal, contentColor = PureWhite),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (strictActive) "Change PIN (locked during Strict Mode)" else "Change PIN") }
        if (pinChangedNote) {
            Spacer(Modifier.height(8.dp))
            Text("PIN changed.", color = ElectricLime, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        RecoveryQuestionSection(context)

        Spacer(Modifier.height(22.dp))
        SectionHeader("HOW TO UNINSTALL")
        Text(
            "1. Wait until Strict Mode has ended (it cannot be removed during a session).\n" +
                "2. Open this tab with your PIN and tap 'Deactivate Device Admin' below.\n" +
                "3. Uninstall LOCKDOWN like any other app.\n" +
                "If LOCKDOWN is Device Owner, remove it first with: " +
                "adb shell dpm remove-active-admin com.lockdown.strictblocker/.engine.AdminReceiver",
            color = MutedWhite,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                runCatching { dpm.removeActiveAdmin(ComponentName(context, AdminReceiver::class.java)) }
            },
            enabled = !strictActive && Permissions.isAdminActive(context),
            colors = ButtonDefaults.buttonColors(containerColor = Charcoal, contentColor = PureWhite),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Deactivate Device Admin") }

        Spacer(Modifier.height(22.dp))
        SectionHeader("ABOUT")
        Text(
            "LOCKDOWN 1.0 — Unbreakable Focus. No Way Out.\n" +
                "No ads. No trackers. No accounts. Everything stays on this device.",
            color = MutedWhite,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, color = ElectricLime, fontWeight = FontWeight.ExtraBold)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun PermissionRow(name: String, granted: Boolean, onFix: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Charcoal, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(
                if (granted) "Granted" else "Not granted",
                color = if (granted) ElectricLime else MutedWhite,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (!granted && onFix != null) {
            TextButton(onClick = onFix) { Text("Fix", color = ElectricLime) }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DeviceOwnerCard(context: Context, isOwner: Boolean) {
    val command = "adb shell dpm set-device-owner " +
        "com.lockdown.strictblocker/.engine.AdminReceiver"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Charcoal, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text("Device Owner setup", fontWeight = FontWeight.Bold)
        Text(
            if (isOwner) "Active — LOCKDOWN has maximum protection."
            else "Connect the phone to a computer with ADB and run:",
            color = MutedWhite,
            style = MaterialTheme.typography.bodyMedium
        )
        if (!isOwner) {
            Spacer(Modifier.height(10.dp))
            Text(command, fontFamily = MonoFont, color = ElectricLime)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("adb command", command))
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CharcoalLight,
                    contentColor = PureWhite
                )
            ) { Text("Copy ADB command") }
        }
    }
}

@Composable
private fun ChangePinFlow(onFinished: (Boolean) -> Unit) {
    val context = LocalContext.current
    val pinManager = remember { PinManager.get(context) }
    var stage by remember { mutableStateOf(1) } // 1 current, 2 new, 3 confirm
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var resetKey by remember { mutableStateOf(0) }

    val title = when (stage) {
        1 -> "Enter current PIN"
        2 -> "Enter new PIN"
        else -> "Confirm new PIN"
    }
    Column(Modifier.fillMaxSize().background(VaultBlack)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PinPad(
                title = title,
                subtitle = null,
                error = error,
                resetKey = resetKey,
                onComplete = { pin ->
                    when (stage) {
                        1 -> when (val r = pinManager.verify(pin)) {
                            PinManager.VerifyResult.Success -> {
                                error = null
                                stage = 2
                                resetKey++
                            }
                            is PinManager.VerifyResult.Wrong -> {
                                error = "Wrong code — ${r.attemptsLeft} attempts left"
                                resetKey++
                            }
                            is PinManager.VerifyResult.Locked -> {
                                error = "Locked. Try again in ${(r.remainingMillis / 1000) + 1}s"
                                resetKey++
                            }
                        }
                        2 -> {
                            newPin = pin
                            error = null
                            stage = 3
                            resetKey++
                        }
                        else -> {
                            if (pin == newPin) {
                                pinManager.setPin(pin)
                                onFinished(true)
                            } else {
                                error = "Codes don't match"
                                stage = 2
                                resetKey++
                            }
                        }
                    }
                }
            )
        }
        Box(
            Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            TextButton(onClick = { onFinished(false) }) { Text("Cancel", color = MutedWhite) }
        }
    }
}

@Composable
private fun RecoveryQuestionSection(context: Context) {
    val pinManager = remember { PinManager.get(context) }
    var editing by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    if (!editing) {
        Button(
            onClick = { editing = true; saved = false },
            colors = ButtonDefaults.buttonColors(containerColor = Charcoal, contentColor = PureWhite),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (pinManager.securityQuestion() != null) "Change recovery question"
                else "Set recovery question"
            )
        }
        if (saved) {
            Spacer(Modifier.height(8.dp))
            Text("Recovery question saved.", color = ElectricLime, fontWeight = FontWeight.Bold)
        }
        return
    }
    Column {
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Question") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            label = { Text("Answer") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Row {
            Button(
                onClick = {
                    pinManager.setSecurityQuestion(question.trim(), answer.trim())
                    editing = false
                    saved = true
                },
                enabled = question.isNotBlank() && answer.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricLime,
                    contentColor = VaultBlack
                )
            ) { Text("Save", fontWeight = FontWeight.Bold) }
            TextButton(onClick = { editing = false }) { Text("Cancel", color = MutedWhite) }
        }
    }
}
