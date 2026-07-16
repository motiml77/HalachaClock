package com.zmanimclock.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fires at the exact alarm time.
 *
 * Ringing alerts hand off to [AlarmSoundService] (a foreground service that
 * owns sound/vibration/full-screen notification). Notification-only alerts
 * post a reminder directly. Either way, a [RescheduleWorker] is enqueued to
 * arm the alert's NEXT occurrence — the chain never breaks.
 */
@AndroidEntryPoint
class AlarmTriggerReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val alertId = intent.getLongExtra(EXTRA_ALERT_ID, -1)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "זמן הלכתי"
        val timeText = intent.getStringExtra(EXTRA_TIME_TEXT) ?: ""
        val useSound = intent.getBooleanExtra(EXTRA_USE_SOUND, true)
        val useVibration = intent.getBooleanExtra(EXTRA_USE_VIBRATION, true)
        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)

        Log.i(TAG, "Alarm fired: alert=$alertId '$title'")

        if (useSound) {
            val serviceIntent = Intent(context, AlarmSoundService::class.java).apply {
                action = AlarmSoundService.ACTION_START
                putExtra(AlarmSoundService.EXTRA_ALERT_ID, alertId)
                putExtra(AlarmSoundService.EXTRA_TITLE, title)
                putExtra(AlarmSoundService.EXTRA_TIME_TEXT, timeText)
                putExtra(AlarmSoundService.EXTRA_USE_VIBRATION, useVibration)
                putExtra(AlarmSoundService.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            notificationHelper.showReminder(alertId, title, timeText, useVibration)
        }

        // Arm the next occurrence in the background
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<RescheduleWorker>().build())
    }

    companion object {
        private const val TAG = "AlarmTriggerReceiver"
        const val EXTRA_ALERT_ID = "alert_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TIME_TEXT = "time_text"
        const val EXTRA_USE_SOUND = "use_sound"
        const val EXTRA_USE_VIBRATION = "use_vibration"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
    }
}
