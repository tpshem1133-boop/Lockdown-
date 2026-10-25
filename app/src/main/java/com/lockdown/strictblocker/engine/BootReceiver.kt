package com.lockdown.strictblocker.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lockdown.strictblocker.core.Prefs

/**
 * After a reboot: if setup was completed, restart the monitor (and the VPN
 * if the user had website blocking on) so a locked Strict Mode — stored as
 * a persistent flag, with no timer — simply stays locked.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        val prefs = Prefs.get(context)
        if (!prefs.onboardingComplete) return
        MonitorService.start(context)
        if (prefs.vpnEnabled) {
            runCatching {
                context.startService(Intent(context, LockdownVpnService::class.java))
            }
        }
    }
}
