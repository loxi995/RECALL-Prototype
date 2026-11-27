package com.recall.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

/**
 * Event-based alert system — no time-based "re-engagement" nag.
 *
 * Two independent alerts:
 *
 *  1. **Watchdog** (default ON): fires every ~15 minutes to check whether the
 *     capture service should be running (`enabled` pref = true) but has gone
 *     silent (no heartbeat in the last several minutes). Posts an immediate
 *     "RECALL stopped listening — tap to restart" notification. Solves the
 *     Samsung-killed-my-service problem.
 *
 *  2. **Weekly check-in** (default OFF, opt-in): fires daily. If the user has
 *     had listening OFF for 7+ consecutive days, posts a single gentle nudge.
 *     Does not re-notify until another 7 days pass.
 *
 * Neither alert ever fires while listening is working correctly, so this never
 * becomes a "come back" nag.
 */
object WatchdogScheduler {
    const val PREFS = "recall_prefs"

    const val KEY_WATCHDOG_ENABLED = "watchdog_enabled"
    const val KEY_WEEKLY_ENABLED = "weekly_checkin_enabled"
    const val KEY_LAST_HEARTBEAT_MS = "last_heartbeat_ms"
    const val KEY_LAST_DISABLED_AT_MS = "last_disabled_at_ms"
    const val KEY_LAST_WATCHDOG_NOTIF_MS = "last_watchdog_notif_ms"
    const val KEY_LAST_WEEKLY_NOTIF_MS = "last_weekly_notif_ms"

    const val DEFAULT_WATCHDOG_ENABLED = true
    const val DEFAULT_WEEKLY_ENABLED = false

    // How long until a missing heartbeat means the service is dead.
    // Chunk rotations happen every 2 minutes and each one writes a heartbeat,
    // so 10 minutes = 5 missed rotations worth of safety margin.
    const val HEARTBEAT_STALE_MS = 10L * 60L * 1000L

    // At most one watchdog notification per hour so we don't spam.
    const val WATCHDOG_RATE_LIMIT_MS = 60L * 60L * 1000L

    // "Weekly check-in" fires only after 7 consecutive days off, at most once a week.
    const val WEEKLY_THRESHOLD_MS = 7L * 24L * 3600L * 1000L

    const val ACTION_WATCHDOG_TICK = "com.recall.app.WATCHDOG_TICK"
    const val ACTION_WEEKLY_TICK = "com.recall.app.WEEKLY_TICK"

    private const val REQ_WATCHDOG = 7001
    private const val REQ_WEEKLY = 7002

    fun watchdogEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_WATCHDOG_ENABLED, DEFAULT_WATCHDOG_ENABLED)

    fun weeklyEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_WEEKLY_ENABLED, DEFAULT_WEEKLY_ENABLED)

    fun setWatchdogEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_WATCHDOG_ENABLED, on).apply()
        ensureSchedules(context)
    }

    fun setWeeklyEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_WEEKLY_ENABLED, on).apply()
        ensureSchedules(context)
    }

    /** Capture service calls this on start + on every chunk rotation (~2 min). */
    fun recordHeartbeat(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_HEARTBEAT_MS, System.currentTimeMillis()).apply()
    }

    /** Called when the user intentionally toggles listening OFF. */
    fun recordDisabled(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_DISABLED_AT_MS, System.currentTimeMillis()).apply()
    }

    /** Called when the user re-enables listening — resets the weekly-check-in clock. */
    fun clearDisabled(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_LAST_DISABLED_AT_MS).apply()
    }

    /** Re-registers both alarms based on current enabled flags. Idempotent. */
    fun ensureSchedules(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()

        val wpi = watchdogPendingIntent(context)
        am.cancel(wpi)
        if (watchdogEnabled(context)) {
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                now + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                wpi
            )
        }

        val wkpi = weeklyPendingIntent(context)
        am.cancel(wkpi)
        if (weeklyEnabled(context)) {
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                now + AlarmManager.INTERVAL_DAY,
                AlarmManager.INTERVAL_DAY,
                wkpi
            )
        }
    }

    private fun watchdogPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WatchdogReceiver::class.java).apply {
            action = ACTION_WATCHDOG_TICK
        }
        return PendingIntent.getBroadcast(
            context, REQ_WATCHDOG, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun weeklyPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WatchdogReceiver::class.java).apply {
            action = ACTION_WEEKLY_TICK
        }
        return PendingIntent.getBroadcast(
            context, REQ_WEEKLY, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}

/**
 * Receives the periodic alarm ticks and decides whether to post an alert.
 * Both decision paths are no-ops when there is nothing to alert about.
 */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences(
            WatchdogScheduler.PREFS, Context.MODE_PRIVATE
        )
        when (intent?.action) {
            WatchdogScheduler.ACTION_WATCHDOG_TICK -> handleWatchdog(context, prefs)
            WatchdogScheduler.ACTION_WEEKLY_TICK -> handleWeekly(context, prefs)
        }
    }

    private fun handleWatchdog(context: Context, prefs: SharedPreferences) {
        if (!WatchdogScheduler.watchdogEnabled(context)) return
        // User intentionally has listening off → nothing to alert about.
        if (!prefs.getBoolean("enabled", true)) return
        val lastHeartbeat = prefs.getLong(WatchdogScheduler.KEY_LAST_HEARTBEAT_MS, 0L)
        // Service has never run (fresh install, user hasn't started it yet) → no alert.
        if (lastHeartbeat == 0L) return
        val now = System.currentTimeMillis()
        if (now - lastHeartbeat < WatchdogScheduler.HEARTBEAT_STALE_MS) return
        // Rate limit to once per hour.
        val lastNotif = prefs.getLong(WatchdogScheduler.KEY_LAST_WATCHDOG_NOTIF_MS, 0L)
        if (now - lastNotif < WatchdogScheduler.WATCHDOG_RATE_LIMIT_MS) return

        postAlert(
            context,
            "RECALL stopped listening",
            "Tap to restart — so you don't miss anything."
        )
        prefs.edit()
            .putLong(WatchdogScheduler.KEY_LAST_WATCHDOG_NOTIF_MS, now)
            .apply()
    }

    private fun handleWeekly(context: Context, prefs: SharedPreferences) {
        if (!WatchdogScheduler.weeklyEnabled(context)) return
        // Listening is on → no need to prompt.
        if (prefs.getBoolean("enabled", true)) return
        val disabledAt = prefs.getLong(WatchdogScheduler.KEY_LAST_DISABLED_AT_MS, 0L)
        if (disabledAt == 0L) return
        val now = System.currentTimeMillis()
        if (now - disabledAt < WatchdogScheduler.WEEKLY_THRESHOLD_MS) return
        // Rate limit: at most once per 7 days.
        val lastNotif = prefs.getLong(WatchdogScheduler.KEY_LAST_WEEKLY_NOTIF_MS, 0L)
        if (now - lastNotif < WatchdogScheduler.WEEKLY_THRESHOLD_MS) return

        postAlert(
            context,
            "RECALL has been off for a week",
            "Turn it back on so you stop missing audio."
        )
        prefs.edit()
            .putLong(WatchdogScheduler.KEY_LAST_WEEKLY_NOTIF_MS, now)
            .apply()
    }

    private fun postAlert(context: Context, title: String, body: String) {
        createAlertChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(context, CHANNEL_ALERT)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification_transparent)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java)
                .notify(ALERT_NOTIFICATION_ID, n)
        } catch (_: Exception) {}
    }

    private fun createAlertChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ALERT, "Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts you when RECALL unexpectedly stops listening."
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ALERT = "recall_alerts"
        const val ALERT_NOTIFICATION_ID = 42
    }
}
