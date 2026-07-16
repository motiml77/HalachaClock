package com.zmanimclock.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

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

        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmSoundService::class.java).apply {
                action = AlarmSoundService.ACTION_START
                putExtra(AlarmSoundService.EXTRA_ALARM_ID, alarmId)
            },
        )
    }

    companion object {
        private const val TAG = "AlarmTriggerReceiver"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
