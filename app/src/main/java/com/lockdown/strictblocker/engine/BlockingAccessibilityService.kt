package com.lockdown.strictblocker.engine

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.lockdown.strictblocker.core.Prefs

/**
 * Primary blocking method.
 *
 * Watches foreground windows: when a blocked app comes to the front (or the
 * browser's address bar shows a blocked site / keyword), the vault-door
 * overlay goes up. While Strict Mode is active it also guards the system
 * Settings app, so the user cannot reach LOCKDOWN's App info, Accessibility
 * settings or Device admin screens to tear the protection down.
 */
class BlockingAccessibilityService : AccessibilityService() {

    private var lastBrowserUrl: String? = null
    private var lastForegroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        BlockEngine.refreshLaunchableCache(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == BlockEngine.OWN_PACKAGE) return // never react to ourselves

        val prefs = Prefs.get(this)

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            lastForegroundPackage = pkg
            // Strict Mode Settings guard: the whole Settings app is a
            // teardown path (App info / Accessibility / Device admin).
            if (prefs.isStrictActive() && pkg == SETTINGS_PACKAGE) {
                OverlayManager.show(this, "Settings are locked while Strict Mode is active")
                return
            }
            if (BlockEngine.isAppBlocked(this, pkg)) {
                OverlayManager.show(this, "App blocked: ${appLabel(pkg)}")
                return
            }
            if (pkg !in BROWSER_PACKAGES) {
                OverlayManager.hide()
                return
            }
        }

        // Browser address-bar inspection for website / keyword rules.
        if (pkg in BROWSER_PACKAGES &&
            (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        ) {
            val url = readBrowserUrl()
            if (url != null && url != lastBrowserUrl) {
                lastBrowserUrl = url
                val reason = BlockEngine.checkUrl(this, url)
                if (reason != null) {
                    OverlayManager.show(this, reason)
                } else if (!BlockEngine.isAppBlocked(this, pkg)) {
                    OverlayManager.hide()
                }
            }
        }
    }

    override fun onInterrupt() = Unit

    private fun appLabel(pkg: String): String = runCatching {
        val appInfo = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    }.getOrDefault(pkg)

    /**
     * Find the browser's address-bar text. Browser view ids differ per app,
     * so scan for any node whose view id mentions "url" (url_bar,
     * mozac_browser_toolbar_url_view, location_bar_edit_text, ...).
     */
    private fun readBrowserUrl(): String? {
        val root = rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var result: String? = null
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 400) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName ?: ""
            val text = node.text?.toString()?.trim().orEmpty()
            if (result == null && text.isNotEmpty() &&
                (id.contains("url", ignoreCase = true) ||
                    node.className?.toString()?.endsWith("EditText") == true) &&
                looksLikeUrl(text)
            ) {
                result = text
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return result
    }

    private fun looksLikeUrl(text: String): Boolean {
        if (text.contains(' ')) return false
        return text.contains('.') &&
            (text.contains('/') || text.contains(".com") || text.contains(".org") ||
                text.contains(".net") || text.contains(".io") || text.contains(".co"))
    }

    companion object {
        private const val SETTINGS_PACKAGE = "com.android.settings"
        val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.brave.browser",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "com.sec.android.app.sbrowser",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.duckduckgo.mobile.android",
            "com.vivaldi.browser",
            "com.kiwibrowser.browser"
        )
    }
}
