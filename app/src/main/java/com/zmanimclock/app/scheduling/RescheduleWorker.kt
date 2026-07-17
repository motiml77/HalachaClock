package com.zmanimclock.app.scheduling

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Recomputes and re-arms every active alert.
 *
 * Enqueued from: boot / time-set / timezone-change ([BootReceiver]), after
 * every alarm firing ([AlarmTriggerReceiver]), a daily periodic schedule
 * ([ZmanimApp]), and whenever alerts or location change in the UI.
 */
@HiltWorker
class RescheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val alarmScheduler: AlarmScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            alarmScheduler.rescheduleAll()
            StatusNotificationReceiver.ping(applicationContext)
            com.zmanimclock.app.feature.widget.ZmanWidgetProvider.refresh(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Reschedule failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "RescheduleWorker"
        const val PERIODIC_WORK_NAME = "daily_alarm_reschedule"
    }
}
