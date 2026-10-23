package com.lockdown.strictblocker.core

import android.content.Context
import android.content.SharedPreferences

enum class ListMode { BLACKLIST, WHITELIST }

/**
 * Plain (non-sensitive) app state: list modes, onboarding progress and the
 * Strict Mode deadline. Read synchronously by the blocking services, so this
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

    /** Epoch millis when Strict Mode ends. 0 = Strict Mode not scheduled. */
    var strictEndMillis: Long
        get() = prefs.getLong(KEY_STRICT_END, 0L)
        set(v) = prefs.edit().putLong(KEY_STRICT_END, v).apply()

    /** Total length of the current Strict Mode session (for the progress ring). */
    var strictTotalMillis: Long
        get() = prefs.getLong(KEY_STRICT_TOTAL, 0L)
        set(v) = prefs.edit().putLong(KEY_STRICT_TOTAL, v).apply()

    /** True while the Strict Mode countdown is still running. */
    fun isStrictActive(now: Long = System.currentTimeMillis()): Boolean =
        strictEndMillis > now

    fun strictRemainingMillis(now: Long = System.currentTimeMillis()): Long =
        (strictEndMillis - now).coerceAtLeast(0L)

    companion object {
        private const val FILE = "lockdown_prefs"
        private const val KEY_ONBOARDING = "onboarding_complete"
        private const val KEY_APP_MODE = "app_mode"
        private const val KEY_WEB_MODE = "web_mode"
        private const val KEY_STRICT_END = "strict_end_millis"
        private const val KEY_VPN = "vpn_enabled"
        private const val KEY_VPN_RUNNING = "vpn_running"
        private const val KEY_STRICT_TOTAL = "strict_total_millis"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }
    }
}
