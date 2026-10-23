package com.lockdown.strictblocker.engine

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.lockdown.strictblocker.core.Prefs

/**
 * Device Administrator for LOCKDOWN.
 *
 * With plain Device Admin, Android still lets the user deactivate the admin —
 * so while Strict Mode is active the Accessibility guard keeps the Settings
 * pages (including this admin's page) behind the vault door, and this
 * receiver shows a warning if a deactivation is attempted. Full uninstall
 * blocking (setUninstallBlocked) only exists for Device Owner; see README.
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        return if (Prefs.get(context).isStrictActive()) {
            "LOCKDOWN Strict Mode is active. The vault cannot be opened until the timer ends."
        } else {
            "Disabling LOCKDOWN's device admin removes uninstall protection."
        }
    }
}
