package com.lockdown.strictblocker.core

import android.content.Context
import android.content.SharedPreferences

enum class ListMode { BLACKLIST, WHITELIST }

/**
 * Plain (non-sensitive) app state: list modes, onboarding progress and the
 * Strict Mode lock flag. Read synchronously by the blocking services, so this
 * deliberately uses commit-free apply() writes and cached reads.
 *
 * The PIN itself never lives here — see PinManager (encrypted storage).
 */
class Prefs private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(v) = prefs.edit().putBoolean(KEY_ONBOARDING, v).apply()

    var appMode: ListMode
        get() = runCatching { ListMode.valueOf(prefs.getString(KEY_APP_MODE, ListMode.BLACKLIST.name)!!) }
            .getOrDefault(ListMode.BLACKLIST)
        set(v) = prefs.edit().putString(KEY_APP_MODE, v.name).apply()

    var webMode: ListMode
        get() = runCatching { ListMode.valueOf(prefs.getString(KEY_WEB_MODE, ListMode.BLACKLIST.name)!!) }
            .getOrDefault(ListMode.BLACKLIST)
        set(v) = prefs.edit().putString(KEY_WEB_MODE, v.name).apply()

    /** Whether the user turned on website blocking (VPN) in the Block List tab. */
    var vpnEnabled: Boolean
        get() = prefs.getBoolean(KEY_VPN, false)
        set(v) = prefs.edit().putBoolean(KEY_VPN, v).apply()

    /** Whether the website-blocking VPN is actually established right now. */
    var vpnRunning: Boolean
        get() = prefs.getBoolean(KEY_VPN_RUNNING, false)
        set(v) = prefs.edit().putBoolean(KEY_VPN_RUNNING, v).apply()

    /**
     * PIN-only Strict Mode lock. True from activation until the vault PIN
     * unlocks it — there is no timer and no expiry.
     */
    var strictLocked: Boolean
        get() = prefs.getBoolean(KEY_STRICT_LOCKED, false)
        set(v) = prefs.edit().putBoolean(KEY_STRICT_LOCKED, v).apply()

    /** True while Strict Mode is locked. */
    fun isStrictActive(): Boolean = strictLocked

    companion object {
        private const val FILE = "lockdown_prefs"
        private const val KEY_ONBOARDING = "onboarding_complete"
        private const val KEY_APP_MODE = "app_mode"
        private const val KEY_WEB_MODE = "web_mode"
        private const val KEY_STRICT_LOCKED = "strict_locked"
        private const val KEY_VPN = "vpn_enabled"
        private const val KEY_VPN_RUNNING = "vpn_running"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }
    }
}
