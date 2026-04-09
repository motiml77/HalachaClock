package com.zmanimclock.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // Schedule a one-time worker to reschedule all alarms
            val request = OneTimeWorkRequestBuilder<DailyRescheduleWorker>().build()
            WorkManager.getInstance(context).enqueue(request)

            // Restart the persistent zmanim notification service if enabled
            ZmanimForegroundService.startIfEnabled(context)
        }
    }
}
