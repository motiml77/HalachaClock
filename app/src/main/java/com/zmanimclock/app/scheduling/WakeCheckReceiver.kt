package com.zmanimclock.app.scheduling

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService

/**
 * Wake-up check (B1): after the user acknowledges an alarm, if the alarm has
 * wakeCheckMinutes > 0 we schedule this receiver. It posts a high-priority
 * "are you awake?" notification AND arms a full re-ring in 2 minutes. Tapping
 * "אני ער" ([WakeCheckConfirmReceiver]) cancels the re-ring — otherwise the
 * alarm rings again, so a heavy sleeper who dozed off is caught.
 */
class WakeCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        if (alarmId < 0) return
        Log.i(TAG, "Wake-up check for alarm $alarmId")

        ensureChannel(context)

        // Arm the re-ring (full alarm pipeline) in RE_RING_MINUTES.
        // Its own request-code slot: sharing the scheduler's slot made this
        // re-ring REPLACE the alarm's next occurrence, and cancelling it on
        // "אני ער" then left the alarm completely unarmed.
        val am = context.getSystemService<AlarmManager>() ?: return
        val reRingPi = PendingIntent.getBroadcast(
            context,
            AlarmScheduler.requestCode(alarmId, AlarmScheduler.SLOT_WAKE_CHECK),
            Intent(context, AlarmTriggerReceiver::class.java)
                .putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val reRingAt = System.currentTimeMillis() + RE_RING_MINUTES * 60_000L
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reRingAt, reRingPi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reRingAt, reRingPi)
        }

        // "אני ער" action cancels the re-ring
        val confirmPi = PendingIntent.getBroadcast(
            context,
            alarmId.toInt() * 10 + 6,
            Intent(context, WakeCheckConfirmReceiver::class.java)
                .putExtra(EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_WAKE_CHECK)
            .setSmallIcon(com.zmanimclock.app.R.drawable.ic_stat_zman)
            .setColor(0xFF123A8B.toInt())
            .setContentTitle("אתה ער?")
            .setContentText("הקש לאישור — אחרת השעון יצלצל שוב בעוד $RE_RING_MINUTES דק'")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(confirmPi)
            .addAction(0, "אני ער", confirmPi)
            .build()
        context.getSystemService<NotificationManager>()?.notify(wakeCheckNotifId(alarmId), notification)
    }

    companion object {
        private const val TAG = "WakeCheckReceiver"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val CHANNEL_WAKE_CHECK = "zmanim_wake_check"
        private const val RE_RING_MINUTES = 2L

        fun wakeCheckNotifId(alarmId: Long): Int = 2000 + alarmId.toInt()

        fun schedule(context: Context, alarmId: Long, minutes: Int) {
            val am = context.getSystemService<AlarmManager>() ?: return
            val pi = PendingIntent.getBroadcast(
                context,
                alarmId.toInt() * 10 + 7,
                Intent(context, WakeCheckReceiver::class.java).putExtra(EXTRA_ALARM_ID, alarmId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val at = System.currentTimeMillis() + minutes * 60_000L
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
            Log.i(TAG, "Wake-up check for alarm $alarmId in $minutes min")
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService<NotificationManager>() ?: return
            if (manager.getNotificationChannel(CHANNEL_WAKE_CHECK) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_WAKE_CHECK,
                    "בדיקת ערות",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "התראה שמוודאת שהתעוררת אחרי אישור השעון"
                    setBypassDnd(true)
                }
            )
        }
    }
}

/** Cancels the pending wake-up re-ring when the user confirms they're awake. */
class WakeCheckConfirmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(WakeCheckReceiver.EXTRA_ALARM_ID, -1)
        if (alarmId < 0) return
        val am = context.getSystemService<AlarmManager>()
        // Cancel ONLY the wake-check slot — never the alarm's own occurrence
        val reRingPi = PendingIntent.getBroadcast(
            context,
            AlarmScheduler.requestCode(alarmId, AlarmScheduler.SLOT_WAKE_CHECK),
            Intent(context, AlarmTriggerReceiver::class.java)
                .putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        reRingPi?.let { am?.cancel(it) }
        context.getSystemService<NotificationManager>()
            ?.cancel(WakeCheckReceiver.wakeCheckNotifId(alarmId))
        // Belt and braces: re-arm the normal schedule in case anything above
        // ever disturbs it.
        runCatching {
            androidx.work.WorkManager.getInstance(context).enqueue(
                androidx.work.OneTimeWorkRequestBuilder<RescheduleWorker>().build()
            )
        }
    }
}
