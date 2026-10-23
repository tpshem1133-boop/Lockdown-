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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lockdown.strictblocker.core.Prefs
import com.lockdown.strictblocker.engine.AdminReceiver
import com.lockdown.strictblocker.engine.MonitorService
import com.lockdown.strictblocker.engine.Permissions

/**
 * Mandatory step-by-step setup, shown after the vault PIN is created.
 * Each step explains one permission, deep-links to the exact system screen,
 * and shows live granted status (refreshKey changes when the app resumes).
 */
@Composable
fun OnboardingScreen(refreshKey: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }

    val steps = listOf("Usage Access", "Accessibility", "Display Over Apps", "Device Admin", "Device Owner", "Ready")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultBlack)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("LOCKDOWN", style = MaterialTheme.typography.headlineMedium, color = ElectricLime)
        Text(
            "Step ${step + 1} of ${steps.size} — ${steps[step]}",
            color = MutedWhite,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(28.dp))

        when (step) {
            0 -> PermissionStep(
                title = "See which app is open",
                body = "Usage Access lets LOCKDOWN notice the moment a blocked app " +
                    "comes to the front. Tap the button, find LOCKDOWN in the list, " +
                    "and allow usage access.",
                granted = Permissions.hasUsageAccess(context),
                buttonLabel = "Open Usage Access settings",
                onOpen = {
                    context.startActivity(
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                refreshKey = refreshKey,
                onContinue = { step++ }
            )
            1 -> PermissionStep(
                title = "The blocking engine",
                body = "The Accessibility Service is what actually slams the vault door: " +
                    "it watches app switches and browser address bars against your list. " +
                    "Tap the button, open 'Installed services', pick 'LOCKDOWN Blocking " +
                    "Engine' and turn it on.",
                granted = Permissions.isAccessibilityEnabled(context),
                buttonLabel = "Open Accessibility settings",
                onOpen = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                refreshKey = refreshKey,
                onContinue = { step++ }
            )
            2 -> PermissionStep(
                title = "Draw the vault door",
                body = "'Display over other apps' lets LOCKDOWN cover a blocked app with " +
                    "the full-screen blocking screen. Without it, there is no door.",
                granted = Permissions.canDrawOverlays(context),
                buttonLabel = "Open overlay settings",
                onOpen = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                refreshKey = refreshKey,
                onContinue = { step++ }
            )
            3 -> PermissionStep(
                title = "Uninstall protection",
                body = "Device Administrator status makes LOCKDOWN harder to remove and " +
                    "lets it warn you before anyone tries. During Strict Mode the " +
                    "Settings app itself is guarded, so this cannot be switched off " +
                    "mid-session.",
                granted = Permissions.isAdminActive(context),
                buttonLabel = "Activate Device Admin",
                onOpen = {
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
                },
                refreshKey = refreshKey,
                onContinue = { step++ }
            )
            4 -> DeviceOwnerStep(
                isOwner = Permissions.isDeviceOwner(context),
                onContinue = { step++ }
            )
            else -> ReadyStep(refreshKey = refreshKey, onDone = onDone)
        }
    }
}

@Composable
private fun PermissionStep(
    title: String,
    body: String,
    granted: Boolean,
    buttonLabel: String,
    onOpen: () -> Unit,
    refreshKey: Int,
    onContinue: () -> Unit
) {
    // refreshKey is read so statuses recompose when the user returns.
    @Suppress("UNUSED_EXPRESSION") refreshKey
    Text(title, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(10.dp))
    Text(body, color = MutedWhite, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(20.dp))
    Text(
        if (granted) "✓ Granted" else "Not granted yet",
        color = if (granted) ElectricLime else PureWhite,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onOpen,
        colors = ButtonDefaults.buttonColors(containerColor = Charcoal, contentColor = PureWhite),
        modifier = Modifier.fillMaxWidth()
    ) { Text(buttonLabel) }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onContinue,
        colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = VaultBlack),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Continue", fontWeight = FontWeight.Bold) }
}

@Composable
private fun DeviceOwnerStep(isOwner: Boolean, onContinue: () -> Unit) {
    val context = LocalContext.current
    val command = "adb shell dpm set-device-owner " +
        "com.lockdown.strictblocker/.engine.AdminReceiver"
    Text("Make it 100% unbreakable (optional)", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(10.dp))
    Text(
        "Device Owner is Android's deepest level of control. With it, LOCKDOWN can " +
            "hard-block uninstalls and survive tricks that defeat normal apps. " +
            "Android only allows it via a computer command, on a device with no " +
            "accounts signed in (typical for a fresh or dedicated device).",
        color = MutedWhite,
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(16.dp))
    Text(
        if (isOwner) "✓ LOCKDOWN is Device Owner" else "Not Device Owner — admin protection still applies.",
        color = if (isOwner) ElectricLime else PureWhite,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(16.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Charcoal, RoundedCornerShape(12.dp))
            .border(1.dp, CharcoalLight, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(command, fontFamily = MonoFont, color = ElectricLime)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("adb command", command))
            },
            colors = ButtonDefaults.buttonColors(containerColor = CharcoalLight, contentColor = PureWhite)
        ) { Text("Copy ADB command") }
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onContinue,
        colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = VaultBlack),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Continue", fontWeight = FontWeight.Bold) }
}

@Composable
private fun ReadyStep(refreshKey: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    @Suppress("UNUSED_EXPRESSION") refreshKey
    Text("Your vault is ready", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(10.dp))
    Text(
        "One last thing: exempt LOCKDOWN from battery optimization so Android " +
            "does not put the guard to sleep.",
        color = MutedWhite,
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(16.dp))
    val ignoring = Permissions.isIgnoringBatteryOptimizations(context)
    Text(
        if (ignoring) "✓ Battery exemption granted" else "Battery exemption not granted yet",
        color = if (ignoring) ElectricLime else PureWhite,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(12.dp))
    if (!ignoring) {
        Button(
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = Charcoal, contentColor = PureWhite),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Request battery exemption") }
        Spacer(Modifier.height(12.dp))
    }
    Button(
        onClick = {
            Prefs.get(context).onboardingComplete = true
            MonitorService.start(context)
            onDone()
        },
        colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = VaultBlack),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Enter LOCKDOWN", fontWeight = FontWeight.Bold) }
    TextButton(onClick = {
        Prefs.get(context).onboardingComplete = true
        MonitorService.start(context)
        onDone()
    }, modifier = Modifier.fillMaxWidth()) {
        Text("Skip for now", color = MutedWhite)
    }
}
