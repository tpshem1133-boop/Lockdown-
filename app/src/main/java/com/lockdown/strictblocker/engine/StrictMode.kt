package com.lockdown.strictblocker.engine

import android.content.Context
import com.lockdown.strictblocker.core.Prefs

/**
 * Strict Mode state transitions.
 *
 * PIN-only lock: once activated, Strict Mode stays on indefinitely — there
 * is no timer and no expiry. The ONLY way to change anything (lists,
 * settings, deactivation) is to enter the vault PIN on the Strict Mode tab,
 * which lifts the lock. The lock flag survives reboots and process death; a
 * BOOT_COMPLETED receiver re-arms protection.
 */
object StrictMode {

    fun isActive(context: Context): Boolean = Prefs.get(context).isStrictActive()

    /** Lock Strict Mode. Returns false if it is already locked. */
    fun activate(context: Context): Boolean {
        val prefs = Prefs.get(context)
        if (prefs.isStrictActive()) return false
        prefs.strictLocked = true
        MonitorService.start(context)
        return true
    }

    /**
     * Lift the lock. Call only after the vault PIN has been verified — this
     * is the single sanctioned way out of Strict Mode.
     */
    fun deactivate(context: Context) {
        Prefs.get(context).strictLocked = false
        MonitorService.start(context) // refresh the persistent notification text
    }
}
