package com.lockdown.strictblocker.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.lockdown.strictblocker.MainActivity
import com.lockdown.strictblocker.R
import com.lockdown.strictblocker.core.Prefs
import com.lockdown.strictblocker.core.formatRemaining

/**
 * Always-on enforcement service (once setup is complete):
 *
 *  - Backup foreground-app detection via UsageStats, in case the
 *    Accessibility Service is off or delayed.
 *  - Posts the persistent, non-dismissible notification:
 *    "LOCKDOWN is Active — Xh Ym remaining" while Strict Mode runs.
 *  - Watches the Strict Mode deadline and refreshes state when it expires.
 */
class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastPackage: String? = null
    private var lastNotificationText: String? = null
    private var wasStrictActive: Boolean = false

    private val tick = object : Runnable {
        override fun run() {
            pollForegroundApp()
            refreshNotificationIfChanged()
            checkStrictTransition()
            handler.postDelayed(this, TICK_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat(buildNotification())
        BlockEngine.refreshLaunchableCache(this)
        wasStrictActive = Prefs.get(this).isStrictActive()
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---- foreground app polling (backup path) ----

    private fun pollForegroundApp() {
        if (!Permissions.hasUsageAccess(this)) return
        val pkg = currentForegroundPackage() ?: return
        if (pkg == lastPackage) return
        lastPackage = pkg
        evaluate(pkg)
    }

    private fun evaluate(pkg: String) {
        if (pkg == BlockEngine.OWN_PACKAGE) return
        val prefs = Prefs.get(this)
        if (prefs.isStrictActive() && pkg == "com.android.settings") {
            OverlayManager.show(this, "Settings are locked while Strict Mode is active")
            return
        }
        if (BlockEngine.isAppBlocked(this, pkg)) {
            OverlayManager.show(this, "App blocked")
        } else if (pkg !in BlockingAccessibilityService.BROWSER_PACKAGES) {
            OverlayManager.hide()
        }
    }

    private fun currentForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = runCatching { usm.queryEvents(now - 10_000, now) }.getOrNull() ?: return null
        val event = UsageEvents.Event()
        var foreground: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                foreground = event.packageName
            }
        }
        return foreground
    }

    // ---- strict mode bookkeeping ----

    private fun checkStrictTransition() {
        val active = Prefs.get(this).isStrictActive()
        if (wasStrictActive && !active) {
            // Timer just expired: re-evaluate whatever is in front of the
            // user (e.g. Settings behind the guard overlay is free again).
            lastPackage?.let { evaluate(it) }
        }
        wasStrictActive = active
    }

    // ---- notification ----

    private fun refreshNotificationIfChanged() {
        val text = notificationText()
        if (text == lastNotificationText) return
        lastNotificationText = text
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun notificationText(): String {
        val prefs = Prefs.get(this)
        return if (prefs.isStrictActive()) {
            "LOCKDOWN is Active — ${formatRemaining(prefs.strictRemainingMillis())} remaining"
        } else {
            "LOCKDOWN protection is on"
        }
    }

    private fun buildNotification(): Notification {
        val prefs = Prefs.get(this)
        val strict = prefs.isStrictActive()
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle(if (strict) "LOCKDOWN is Active" else "LOCKDOWN")
            .setContentText(
                if (strict) {
                    "${formatRemaining(prefs.strictRemainingMillis())} remaining — no way out"
                } else {
                    "Blocking engine is running"
                }
            )
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            runCatching { startForeground(NOTIFICATION_ID, notification) }
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "strict_mode"
        private const val NOTIFICATION_ID = 1001
        private const val TICK_MILLIS = 800L

        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, MonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, MonitorService::class.java)) }
        }
    }
}
