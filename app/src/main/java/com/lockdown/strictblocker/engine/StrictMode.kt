package com.lockdown.strictblocker.engine

import android.content.Context
import com.lockdown.strictblocker.core.Prefs

/**
 * Strict Mode state transitions.
 *
 * The deadline is stored as an absolute epoch timestamp, so reboots, process
 * death and clock-consistent countdowns all behave: nothing but the passage
 * of time can end a Strict Mode session. There is intentionally no
 * "deactivate" API — activation is one-way until the timer expires.
 */
object StrictMode {

    /** Preset durations offered on the Strict Mode tab. */
    val PRESETS: List<Pair<String, Long>> = listOf(
        "15 min" to 15 * 60_000L,
        "30 min" to 30 * 60_000L,
        "1 hour" to 60 * 60_000L,
        "6 hours" to 6 * 60 * 60_000L,
        "24 hours" to 24 * 60 * 60_000L
    )

    const val MAX_CUSTOM_HOURS = 99

    fun isActive(context: Context): Boolean = Prefs.get(context).isStrictActive()

    fun remainingMillis(context: Context): Long = Prefs.get(context).strictRemainingMillis()

    fun endMillis(context: Context): Long = Prefs.get(context).strictEndMillis

    /**
     * Start Strict Mode for [durationMillis]. Refuses to shorten an already
     * running session — extending is allowed, weakening is not.
     */
    fun activate(context: Context, durationMillis: Long): Boolean {
        require(durationMillis > 0) { "duration must be positive" }
        val prefs = Prefs.get(context)
        val newEnd = System.currentTimeMillis() + durationMillis
        if (prefs.isStrictActive() && newEnd <= prefs.strictEndMillis) return false
        prefs.strictEndMillis = newEnd
        prefs.strictTotalMillis = newEnd - System.currentTimeMillis()
        MonitorService.start(context)
        return true
    }
}
