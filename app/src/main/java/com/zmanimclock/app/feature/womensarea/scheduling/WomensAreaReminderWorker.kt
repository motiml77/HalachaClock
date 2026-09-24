package com.zmanimclock.app.feature.womensarea.scheduling

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zmanimclock.app.feature.womensarea.model.WomensAreaNotificationText
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Posts one Women's Area reminder — a clean day's, or the tevila evening's.
 * See [WomensAreaReminderScheduler].
 */
@HiltWorker
class WomensAreaReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: WomensAreaNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, -1)
        if (notificationId < 0) return Result.failure()
        val content = when (inputData.getString(KEY_KIND)) {
            KIND_TEVILA -> WomensAreaNotificationText.tevilaEvening()
            KIND_CLEAN -> {
                val day = inputData.getInt(KEY_DAY_NUMBER, -1)
                if (day !in 1..7) return Result.failure()
                WomensAreaNotificationText.cleanDay(day)
            }
            else -> return Result.failure()
        }
        notifier.show(notificationId, content)
        return Result.success()
    }

    companion object {
        const val KEY_KIND = "kind"
        const val KEY_DAY_NUMBER = "day_number"
        const val KEY_NOTIFICATION_ID = "notification_id"

        const val KIND_CLEAN = "clean"
        const val KIND_TEVILA = "tevila"
    }
}
