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
        val isWakeCheckRering = intent.getBooleanExtra(EXTRA_IS_WAKE_CHECK_RERING, false)
        Log.i(TAG, "Alarm fired: id=$alarmId" + if (isWakeCheckRering) " (wake-check re-ring)" else "")

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
                    putExtra(AlarmSoundService.EXTRA_IS_WAKE_CHECK_RERING, isWakeCheckRering)
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
                        // AlarmSoundService never runs on this path, so it is
                        // the ONLY source of sound/vibration here — CHANNEL_ALARM
                        // is deliberately silent for the normal path, which used
                        // to mean this fallback rang the user's phone with
                        // nothing audible or tactile at all.
                        channelId = NotificationHelper.CHANNEL_ALARM_FALLBACK,
                    ),
                )
            }.onFailure { Log.e(TAG, "FSI fallback also failed", it) }

            // The service normally re-enqueues RescheduleWorker after every
            // firing (AlarmSoundService.start()) — that never runs on this
            // path, so without this the alarm's NEXT occurrence (and any
            // repeat day after this one) is simply never armed again.
            runCatching {
                RescheduleWorker.enqueueUnique(context.applicationContext)
            }.onFailure { Log.e(TAG, "Reschedule enqueue failed on fallback path", it) }
        }
    }

    companion object {
        private const val TAG = "AlarmTriggerReceiver"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_IS_WAKE_CHECK_RERING = "is_wake_check_rering"
    }
}
