package com.zmanimclock.app.scheduling

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * Fires at the exact alarm time. Deliberately thin: hands the alarm id to
 * [AlarmSoundService], which loads the alarm from Room, rings, deactivates
 * one-time alarms and re-enqueues the reschedule chain.
 */
class AlarmTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        if (alarmId < 0) return
        Log.i(TAG, "Alarm fired: id=$alarmId")

        // Android 12+ only lets an app start a foreground service from the
        // background under an exemption — the relevant one being "an exact
        // alarm fired". When the exact-alarm permission was revoked the
        // scheduler degrades to an INEXACT alarm, and then this start throws
        // ForegroundServiceStartNotAllowedException, which would crash the
        // process at the exact moment the user needed the alarm. Never let
        // that happen: fall back to a full-screen-intent notification, which
        // still wakes the screen and opens the ringing UI.
        val started = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmSoundService::class.java).apply {
                    action = AlarmSoundService.ACTION_START
                    putExtra(AlarmSoundService.EXTRA_ALARM_ID, alarmId)
                },
            )
        }.isSuccess

        if (!started) {
            Log.e(TAG, "FGS start blocked for alarm $alarmId — posting FSI notification instead")
            runCatching {
                val helper = NotificationHelper(context.applicationContext)
                helper.createChannels()
                context.getSystemService<NotificationManager>()?.notify(
                    NotificationHelper.ALARM_NOTIFICATION_ID,
                    helper.buildAlarmNotification(
                        alertId = alarmId,
                        title = "שעון מעורר",
                        timeText = "",
                        snoozeMinutes = 0,
                        snoozesLeft = 0,
                    ),
                )
            }.onFailure { Log.e(TAG, "FSI fallback also failed", it) }
        }
    }

    companion object {
        private const val TAG = "AlarmTriggerReceiver"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
