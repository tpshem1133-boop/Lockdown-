package com.lockdown.strictblocker.ui

import android.app.Activity
import android.graphics.drawable.Drawable
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.lockdown.strictblocker.core.ListMode
import com.lockdown.strictblocker.core.Prefs
import com.lockdown.strictblocker.data.BlockType
import com.lockdown.strictblocker.engine.LockdownVpnService
import com.lockdown.strictblocker.engine.Permissions

/**
 * Tab 1 — Block List.
 * Apps (blacklist / whitelist), websites + keywords, and the VPN switch that
 * powers DNS-level website blocking. Frozen solid while Strict Mode runs.
 */
@Composable
fun BlockListScreen(vm: MainViewModel, refreshKey: Int) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val items by vm.items.collectAsState()
    val installedApps by vm.installedApps.collectAsState()
    val strictActive by vm.strictActive.collectAsState()
    @Suppress("UNUSED_EXPRESSION") refreshKey

    val appItems = items.filter { it.type == BlockType.APP }.map { it.value }.toSet()
    val webItems = items.filter { it.type == BlockType.WEBSITE }
    val keywordItems = items.filter { it.type == BlockType.KEYWORD }

    var appSearch by remember { mutableStateOf("") }
    var newSite by remember { mutableStateOf("") }
    var newKeyword by remember { mutableStateOf("") }
    var vpnOn by remember(refreshKey) { mutableStateOf(prefs.vpnEnabled) }

    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            prefs.vpnEnabled = true
            vpnOn = true
            LockdownVpnService.start(context)
        }
    }

    fun setVpn(on: Boolean) {
        if (strictActive) return
        if (on) {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                vpnPrepareLauncher.launch(prepareIntent)
            } else {
                prefs.vpnEnabled = true
                vpnOn = true
                LockdownVpnService.start(context)
            }
        } else {
            prefs.vpnEnabled = false
            vpnOn = false
            LockdownVpnService.stop(context)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultBlack)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Block List", style = MaterialTheme.typography.headlineMedium)
            if (strictActive) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Charcoal, RoundedCornerShape(12.dp))
                        .border(1.dp, ElectricLime, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        "Strict Mode is active — your lists are locked and cannot be edited.",
                        color = ElectricLime,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ---------- APPS ----------
        item {
            SectionTitle("APPS")
            ModeSwitch(
                mode = vm.appMode,
                enabled = !strictActive,
                onChange = { vm.setAppMode(it) }
            )
            Text(
                when (vm.appMode) {
                    ListMode.BLACKLIST -> "Block only the apps you pick."
                    ListMode.WHITELIST -> "Block every app except the ones you pick. Your launcher always stays allowed."
                },
                color = MutedWhite,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = appSearch,
                onValueChange = { appSearch = it },
                label = { Text("Search apps") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !strictActive
            )
        }
        val filteredApps = installedApps.filter {
            appSearch.isBlank() ||
                it.label.contains(appSearch, ignoreCase = true) ||
                it.packageName.contains(appSearch, ignoreCase = true)
        }
        items(filteredApps, key = { it.packageName }) { entry ->
            AppRow(
                entry = entry,
                listed = entry.packageName in appItems,
                enabled = !strictActive,
                onToggle = { vm.toggleApp(entry, entry.packageName in appItems) }
            )
        }

        // ---------- WEBSITES ----------
        item {
            Spacer(Modifier.height(10.dp))
            SectionTitle("WEBSITES")
            ModeSwitch(
                mode = vm.webMode,
                enabled = !strictActive,
                onChange = { vm.setWebMode(it) }
            )
            Text(
                when (vm.webMode) {
                    ListMode.BLACKLIST -> "Block the sites you list, plus any URL containing your keywords."
                    ListMode.WHITELIST -> "Only the sites you list can open. Everything else is blocked."
                },
                color = MutedWhite,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Charcoal, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Website blocking (VPN)", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Local VPN filters site lookups on-device. " +
                            if (vpnOn && prefs.vpnRunning) "Running now."
                            else if (vpnOn) "Starting…"
                            else "Off.",
                        color = MutedWhite,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = vpnOn,
                    onCheckedChange = { setVpn(it) },
                    enabled = !strictActive,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VaultBlack,
                        checkedTrackColor = ElectricLime,
                        uncheckedThumbColor = MutedWhite,
                        uncheckedTrackColor = CharcoalLight
                    )
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSite,
                    onValueChange = { newSite = it },
                    label = { Text(if (vm.webMode == ListMode.BLACKLIST) "Add website (e.g. youtube.com)" else "Add allowed website") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !strictActive
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        if (newSite.isNotBlank()) {
                            vm.addWebsite(newSite)
                            newSite = ""
                        }
                    },
                    enabled = !strictActive && newSite.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricLime,
                        contentColor = VaultBlack
                    )
                ) { Text("Add", fontWeight = FontWeight.Bold) }
            }
        }
        items(webItems, key = { it.id }) { item ->
            RemovableRow(
                label = item.value,
                enabled = !strictActive,
                onRemove = { vm.removeItem(item) }
            )
        }

        // ---------- KEYWORDS ----------
        item {
            Spacer(Modifier.height(10.dp))
            SectionTitle("KEYWORDS")
            Text(
                "If a browser address contains a keyword, the page is blocked — " +
                    "e.g. keyword \"news\" blocks cnn.com/news.",
                color = MutedWhite,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newKeyword,
                    onValueChange = { newKeyword = it },
                    label = { Text("Add keyword") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !strictActive
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        if (newKeyword.isNotBlank()) {
                            vm.addKeyword(newKeyword)
                            newKeyword = ""
                        }
                    },
                    enabled = !strictActive && newKeyword.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricLime,
                        contentColor = VaultBlack
                    )
                ) { Text("Add", fontWeight = FontWeight.Bold) }
            }
        }
        items(keywordItems, key = { it.id }) { item ->
            RemovableRow(
                label = item.value,
                enabled = !strictActive,
                onRemove = { vm.removeItem(item) }
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = ElectricLime,
        fontWeight = FontWeight.ExtraBold,
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ModeSwitch(mode: ListMode, enabled: Boolean, onChange: (ListMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Charcoal, RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        ListMode.entries.forEach { option ->
            val selected = option == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (selected) ElectricLime else Charcoal,
                        RoundedCornerShape(9.dp)
                    )
                    .clickable(enabled = enabled) { onChange(option) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (option == ListMode.BLACKLIST) "Blacklist" else "Whitelist",
                    color = if (selected) VaultBlack else MutedWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun AppRow(entry: AppEntry, listed: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Charcoal, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onToggle() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(entry.icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.label, fontWeight = FontWeight.SemiBold)
            Text(entry.packageName, color = MutedWhite, style = MaterialTheme.typography.bodyMedium)
        }
        Checkbox(
            checked = listed,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = ElectricLime,
                checkmarkColor = VaultBlack,
                uncheckedColor = MutedWhite
            )
        )
    }
}

@Composable
private fun AppIcon(icon: Drawable) {
    val bitmap = remember(icon) { icon.toBitmap(96, 96).asImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier.size(40.dp)
    )
}

@Composable
private fun RemovableRow(label: String, enabled: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Charcoal, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clickable(enabled = enabled) { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Text("✕", color = if (enabled) PureWhite else MutedWhite)
        }
    }
}
