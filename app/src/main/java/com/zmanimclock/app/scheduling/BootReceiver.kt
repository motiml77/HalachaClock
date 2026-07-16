package com.zmanimclock.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Re-arms all alarms after events that wipe or invalidate AlarmManager state:
 * device boot, app update, wall-clock changes and timezone changes.
 * (AlarmManager alarms do not survive a reboot — this receiver is what makes
 * the alarm engine reliable across restarts.)
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                Log.i(TAG, "Rescheduling alarms due to ${intent.action}")
                WorkManager.getInstance(context)
                    .enqueue(OneTimeWorkRequestBuilder<RescheduleWorker>().build())
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
