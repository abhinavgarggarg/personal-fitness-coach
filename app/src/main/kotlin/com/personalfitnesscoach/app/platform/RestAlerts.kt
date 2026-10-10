package com.personalfitnesscoach.app.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.MainActivity

/**
 * The rest timer's alert (Phase 2 A2, NFR-05): one exact alarm at the end of each rest, so the phone buzzes even when locked; nothing
 * else runs in the background. While the app is hidden during a rest, a countdown notification shows the time left. Everything is
 * cancelled when the rest is skipped, the workout ends, or the data is erased or restored.
 */
object RestAlerts {
    const val CHANNEL = "rest_timer"
    private const val NOTIFY_ID = 7101
    private const val REQUEST = 7102
    const val ACTION = "com.personalfitnesscoach.REST_OVER"

    /** True while a screen of the app is visible; the alarm then only vibrates (the screen shows the end of the rest itself). */
    @Volatile var appVisible: Boolean = false

    /** How many end-of-rest alarms fired in this process (read by the device tests). */
    @Volatile var fired: Int = 0
        private set

    /** The rest end currently scheduled, so repeated calls with the same time do nothing. */
    @Volatile private var scheduledAt: Long? = null

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_rest), NotificationManager.IMPORTANCE_HIGH)
        ch.description = context.getString(R.string.notif_channel_rest_desc)
        ch.enableVibration(true)
        ch.vibrationPattern = PATTERN
        nm.createNotificationChannel(ch)
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(context, REQUEST, Intent(context, RestAlarmReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Schedules the end-of-rest alarm (exact where the phone allows it, otherwise as close as Android permits). */
    fun schedule(context: Context, endsAtMs: Long) {
        if (scheduledAt == endsAtMs) return
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(context)
        am.cancel(pi)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMs, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMs, pi)
        scheduledAt = endsAtMs
    }

    /** No alarm and no notification left behind. */
    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context))
        NotificationManagerCompat.from(context).cancel(NOTIFY_ID)
        scheduledAt = null
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** The app went to the background during a rest: a countdown notification until the alarm replaces it. */
    fun showCountdown(context: Context, endsAtMs: Long) {
        if (!canNotify(context) || endsAtMs <= System.currentTimeMillis()) return
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_resting_title))
            .setContentText(context.getString(R.string.notif_resting_body))
            .setWhen(endsAtMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openApp(context))
            .build()
        try { NotificationManagerCompat.from(context).notify(NOTIFY_ID, n) } catch (e: SecurityException) { /* permission revoked meanwhile */ }
    }

    fun hideCountdown(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFY_ID)
    }

    /** The rest is over: buzz, and when the app is hidden, say so in a notification. */
    fun restOver(context: Context) {
        scheduledAt = null
        fired++
        vibrate(context)
        if (appVisible || !canNotify(context)) { hideCountdown(context); return }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_rest_over_title))
            .setContentText(context.getString(R.string.notif_rest_over_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(PATTERN)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        try { NotificationManagerCompat.from(context).notify(NOTIFY_ID, n) } catch (e: SecurityException) { /* permission revoked meanwhile */ }
    }

    fun vibrate(context: Context) {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else context.getSystemService(Vibrator::class.java)
        if (v == null || !v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(PATTERN, -1))
    }

    private val PATTERN = longArrayOf(0, 400, 200, 400, 200, 600)
}

/** Fires at the end of a rest. Not exported: only this app's own alarm can reach it. */
class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RestAlerts.ACTION) RestAlerts.restOver(context.applicationContext)
    }
}
