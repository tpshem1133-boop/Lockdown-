package com.lockdown.strictblocker.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lockdown.strictblocker.core.ListMode
import com.lockdown.strictblocker.core.Prefs
import com.lockdown.strictblocker.data.BlockItem
import com.lockdown.strictblocker.data.BlockRepository
import com.lockdown.strictblocker.data.BlockType
import com.lockdown.strictblocker.engine.BlockEngine
import com.lockdown.strictblocker.engine.StrictMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppEntry(val label: String, val packageName: String, val icon: Drawable)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BlockRepository.get(app)
    private val prefs = Prefs.get(app)

    val items: StateFlow<List<BlockItem>> =
        repo.items.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var appMode by mutableStateOf(prefs.appMode)
        private set
    var webMode by mutableStateOf(prefs.webMode)
        private set

    val installedApps = MutableStateFlow<List<AppEntry>>(emptyList())

    /** Live Strict Mode countdown for the UI (services enforce it in the background). */
    val strictRemaining = MutableStateFlow(0L)
    val strictActive = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            while (true) {
                strictRemaining.value = prefs.strictRemainingMillis()
                strictActive.value = prefs.isStrictActive()
                delay(500)
            }
        }
        loadInstalledApps()
    }

    fun setAppMode(mode: ListMode) {
        if (strictActive.value) return // lists are frozen during Strict Mode
        prefs.appMode = mode
        appMode = mode
    }

    fun setWebMode(mode: ListMode) {
        if (strictActive.value) return
        prefs.webMode = mode
        webMode = mode
    }

    fun toggleApp(entry: AppEntry, listed: Boolean) {
        if (strictActive.value) return
        viewModelScope.launch {
            if (listed) {
                repo.snapshot.firstOrNull {
                    it.type == BlockType.APP && it.value == entry.packageName
                }?.let { repo.remove(it) }
            } else {
                repo.add(BlockType.APP, entry.packageName, entry.label)
            }
        }
    }

    fun addWebsite(raw: String) {
        if (strictActive.value) return
        viewModelScope.launch { repo.add(BlockType.WEBSITE, raw, raw) }
    }

    fun addKeyword(raw: String) {
        if (strictActive.value) return
        viewModelScope.launch { repo.add(BlockType.KEYWORD, raw, raw) }
    }

    fun removeItem(item: BlockItem) {
        if (strictActive.value) return
        viewModelScope.launch { repo.remove(item) }
    }

    fun activateStrict(durationMillis: Long): Boolean =
        StrictMode.activate(getApplication(), durationMillis)

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val launchable = pm.queryIntentActivities(launcherIntent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
            val entries = launchable
                .filter { it != BlockEngine.OWN_PACKAGE }
                .mapNotNull { pkg ->
                    runCatching {
                        val info = pm.getApplicationInfo(pkg, 0)
                        AppEntry(
                            label = pm.getApplicationLabel(info).toString(),
                            packageName = pkg,
                            icon = pm.getApplicationIcon(info)
                        )
                    }.getOrNull()
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
            installedApps.value = entries
            BlockEngine.refreshLaunchableCache(getApplication())
        }
    }
}
