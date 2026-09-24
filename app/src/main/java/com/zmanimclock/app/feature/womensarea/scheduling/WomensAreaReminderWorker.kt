package com.zmanimclock.app.feature.womensarea.scheduling

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zmanimclock.app.scheduling.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Posts one day's reminder of the 7-day clean-count. See [WomensAreaReminderScheduler]. */
@HiltWorker
class WomensAreaReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dayNumber = inputData.getInt(KEY_DAY_NUMBER, -1)
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, -1)
        if (dayNumber !in 1..7 || notificationId < 0) return Result.failure()
        notificationHelper.showWomensAreaReminder(notificationId, dayNumber)
        return Result.success()
    }

    companion object {
        const val KEY_DAY_NUMBER = "day_number"
        const val KEY_NOTIFICATION_ID = "notification_id"

        /** One unique work name per (entry, day, time slot). */
        fun workName(entryId: Long, dayNumber: Int, slot: Int) = "womens_area_reminder_${entryId}_${dayNumber}_$slot"

        /** The name used before there could be several times a day — only for cancelling those. */
        fun legacyWorkName(entryId: Long, dayNumber: Int) = "womens_area_reminder_${entryId}_$dayNumber"
    }
}
