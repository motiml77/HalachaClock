package com.zmanimclock.app.feature.womensarea.scheduling

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.zmanimclock.app.feature.womensarea.model.WomensAreaCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Schedules the 7 reminders for one הפסק טהרה entry — one per clean day,
 * which start on the Hebrew day AFTER the hefsek.
 *
 * WorkManager, not AlarmManager: these are explicitly non-urgent, dismissible
 * reminders, so `setInitialDelay`'s deferred-execution model (a minimum
 * delay, not an exact fire time) is the right trade — mirrors
 * [com.zmanimclock.app.scheduling.RescheduleWorker]'s own
 * OneTimeWorkRequestBuilder + enqueueUniqueWork(REPLACE) pattern. Zero new
 * Manifest surface, and WorkManager's own persisted queue survives reboot
 * with no BootReceiver re-arm step needed, unlike the alarm system's
 * AlarmManager path.
 */
class WomensAreaReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleSevenDayCount(entryId: Long, hefsek: LocalDate) {
        val now = LocalDateTime.now()
        WomensAreaCalculator.cleanDayDates(hefsek).forEachIndexed { index, date ->
            val day = index + 1
            val fireAt = date.atTime(REMINDER_HOUR, REMINDER_MINUTE)
            val delayMs = Duration.between(now, fireAt).toMillis()
            // A day already past (a backfilled entry) simply gets no
            // reminder — "up to 7", not always exactly 7.
            if (delayMs < 0) return@forEachIndexed
            val notificationId = notificationId(entryId, day)
            val request = OneTimeWorkRequestBuilder<WomensAreaReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        WomensAreaReminderWorker.KEY_DAY_NUMBER to day,
                        WomensAreaReminderWorker.KEY_NOTIFICATION_ID to notificationId,
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WomensAreaReminderWorker.workName(entryId, day),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    /** Cancels all (up to) 7 reminders for [entryId] — used on edit (re-scheduled right after) and delete. */
    fun cancel(entryId: Long) {
        for (day in 1..7) {
            WorkManager.getInstance(context).cancelUniqueWork(WomensAreaReminderWorker.workName(entryId, day))
        }
    }

    companion object {
        private const val REMINDER_HOUR = 9
        private const val REMINDER_MINUTE = 0

        // Existing notification IDs in this app: 1001-1003 (NotificationHelper
        // constants) and 2-ish-digit alarm ids (AlarmSoundService). This base
        // keeps Women's Area reminders in their own namespace.
        private const val NOTIFICATION_ID_BASE = 92_000

        fun notificationId(entryId: Long, day: Int): Int = NOTIFICATION_ID_BASE + (entryId.toInt() * 10 + day)
    }
}
