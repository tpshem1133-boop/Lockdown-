package com.lockdown.strictblocker.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.lockdown.strictblocker.core.ListMode
import com.lockdown.strictblocker.core.Prefs
import com.lockdown.strictblocker.data.BlockRepository
import com.lockdown.strictblocker.data.BlockType

/**
 * Synchronous blocking decisions, shared by the Accessibility Service, the
 * UsageStats monitor and the VPN. Reads the repository's in-memory snapshot,
 * so it is safe to call on hot paths.
 */
object BlockEngine {

    const val OWN_PACKAGE = "com.lockdown.strictblocker"

    /** System packages that must never be overlay-blocked, whatever the mode. */
    private val NEVER_BLOCK = setOf(
        OWN_PACKAGE,
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.inputmethod.latin",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.android.emergency",
        "com.android.phone",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.server.telecom"
    )

    @Volatile
    private var launchableApps: Set<String> = emptySet()

    @Volatile
    private var launcherPackage: String? = null

    /** Refresh the set of user-launchable apps (needed for whitelist mode). */
    fun refreshLaunchableCache(context: Context) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        launchableApps = pm.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        launcherPackage = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }

    fun isAppBlocked(context: Context, packageName: String): Boolean {
        val prefs = Prefs.get(context)
        if (!prefs.onboardingComplete) return false
        if (packageName in NEVER_BLOCK || packageName == launcherPackage) return false

        val listed = BlockRepository.get(context).snapshot
            .filter { it.type == BlockType.APP }
            .map { it.value }
            .toSet()

        return when (prefs.appMode) {
            ListMode.BLACKLIST -> packageName in listed
            ListMode.WHITELIST ->
                // Only police apps a user could actually launch; system
                // dialogs, keyboards and services are never in this set.
                packageName in launchableApps && packageName !in listed
        }
    }

    /**
     * Check a URL (or bare domain) against the website + keyword rules.
     * Returns a human-readable reason when blocked, null when allowed.
     */
    fun checkUrl(context: Context, url: String): String? {
        val prefs = Prefs.get(context)
        if (!prefs.onboardingComplete) return null
        val host = BlockRepository.hostOf(url) ?: return null
        val items = BlockRepository.get(context).snapshot
        val lowerUrl = url.lowercase()

        return when (prefs.webMode) {
            ListMode.BLACKLIST -> {
                val keyword = items.firstOrNull {
                    it.type == BlockType.KEYWORD && it.value.isNotEmpty() && lowerUrl.contains(it.value)
                }
                if (keyword != null) return "Keyword blocked: ${keyword.value}"
                val site = items.firstOrNull {
                    it.type == BlockType.WEBSITE && domainMatches(host, it.value)
                }
                if (site != null) return "Website blocked: ${site.value}"
                null
            }
            ListMode.WHITELIST -> {
                val allowed = items.any {
                    it.type == BlockType.WEBSITE && domainMatches(host, it.value)
                }
                if (allowed) null else "Not on your allowlist: $host"
            }
        }
    }

    /** DNS-level decision used by the VPN service. */
    fun isDomainBlocked(context: Context, domain: String): Boolean {
        val prefs = Prefs.get(context)
        if (!prefs.onboardingComplete) return false
        val host = BlockRepository.normalizeDomain(domain)
        if (host.isEmpty()) return false
        val items = BlockRepository.get(context).snapshot
        return when (prefs.webMode) {
            ListMode.BLACKLIST -> items.any {
                it.type == BlockType.WEBSITE && domainMatches(host, it.value)
            }
            ListMode.WHITELIST -> items.none {
                it.type == BlockType.WEBSITE && domainMatches(host, it.value)
            }
        }
    }

    /** "m.youtube.com" matches a rule for "youtube.com". */
    fun domainMatches(host: String, rule: String): Boolean =
        host == rule || host.endsWith(".$rule")
}
