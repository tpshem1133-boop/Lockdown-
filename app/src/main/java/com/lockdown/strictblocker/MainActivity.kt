package com.lockdown.strictblocker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lockdown.strictblocker.core.Prefs
import com.lockdown.strictblocker.engine.MonitorService
import com.lockdown.strictblocker.engine.OverlayManager
import com.lockdown.strictblocker.security.PinManager
import com.lockdown.strictblocker.ui.BlockListScreen
import com.lockdown.strictblocker.ui.CreatePinScreen
import com.lockdown.strictblocker.ui.ElectricLime
import com.lockdown.strictblocker.ui.LockdownTheme
import com.lockdown.strictblocker.ui.MainViewModel
import com.lockdown.strictblocker.ui.MutedWhite
import com.lockdown.strictblocker.ui.OnboardingScreen
import com.lockdown.strictblocker.ui.SettingsScreen
import com.lockdown.strictblocker.ui.StrictModeScreen
import com.lockdown.strictblocker.ui.VaultBlack

class MainActivity : ComponentActivity() {

    /** Bumped on every resume so permission statuses recompose after the
     *  user returns from a system Settings screen. */
    private val resumeCounter = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OverlayManager.hide() // the vault door never covers LOCKDOWN itself
        setContent {
            LockdownTheme {
                val context = LocalContext.current
                val pinManager = remember { PinManager.get(context) }
                val prefs = remember { Prefs.get(context) }
                var pinSet by remember { mutableStateOf(pinManager.isPinSet()) }
                var onboarded by remember { mutableStateOf(prefs.onboardingComplete) }
                val refreshKey = resumeCounter.intValue

                Surface(Modifier.fillMaxSize(), color = VaultBlack) {
                    when {
                        !pinSet -> CreatePinScreen(onDone = { pinSet = true })
                        !onboarded -> OnboardingScreen(
                            refreshKey = refreshKey,
                            onDone = { onboarded = true }
                        )
                        else -> MainTabs(
                            refreshKey = refreshKey,
                            onPinCleared = { pinSet = false }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCounter.intValue++
        OverlayManager.hide()
        if (Prefs.get(this).onboardingComplete) {
            MonitorService.start(this)
        }
    }
}

@Composable
private fun MainTabs(refreshKey: Int, onPinCleared: () -> Unit) {
    val vm: MainViewModel = viewModel()
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("BLOCK LIST", "STRICT MODE", "SETTINGS")

    Column(Modifier.fillMaxSize().background(VaultBlack)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> BlockListScreen(vm, refreshKey)
                1 -> StrictModeScreen(vm)
                else -> SettingsScreen(vm, refreshKey, onPinCleared)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(VaultBlack)
                .padding(vertical = 6.dp)
        ) {
            tabs.forEachIndexed { index, label ->
                val selected = tab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { tab = index }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (selected) ElectricLime else MutedWhite,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
