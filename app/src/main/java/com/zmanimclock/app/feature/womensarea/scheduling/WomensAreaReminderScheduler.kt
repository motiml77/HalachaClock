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
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Schedules the reminders for one הפסק טהרה entry — on each of the 7 clean
 * days (which start on the Hebrew day AFTER the hefsek), at each time she
 * chose. Only while she has reminders turned on; see WomensAreaViewModel.
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
    /**
     * One reminder per clean day per time in [times] — only for moments still
     * ahead ("up to", not always all of them: a backfilled hefsek gets only
     * what remains). Every request carries this entry's tag, so [cancel]
     * clears them however many times there were.
     */
    fun scheduleSevenDayCount(entryId: Long, hefsek: LocalDate, times: List<LocalTime>) {
        val now = LocalDateTime.now()
        WomensAreaCalculator.cleanDayDates(hefsek).forEachIndexed { index, date ->
            val day = index + 1
            times.forEachIndexed { slot, time ->
                val delayMs = Duration.between(now, date.atTime(time)).toMillis()
                if (delayMs < 0) return@forEachIndexed
                val request = OneTimeWorkRequestBuilder<WomensAreaReminderWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setInputData(
                        workDataOf(
                            WomensAreaReminderWorker.KEY_DAY_NUMBER to day,
                            // Same id for every time on one day: a later
                            // reminder replaces that day's earlier one
                            // instead of stacking up.
                            WomensAreaReminderWorker.KEY_NOTIFICATION_ID to notificationId(entryId, day),
                        )
                    )
                    .addTag(ALL_TAG)
                    .addTag(entryTag(entryId))
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    WomensAreaReminderWorker.workName(entryId, day, slot),
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        }
    }

    /** Cancels every reminder for [entryId] — used on edit (re-scheduled right after) and delete. */
    fun cancel(entryId: Long) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(entryTag(entryId))
        // Requests queued before tags existed: one 9:00 reminder per day, by name.
        for (day in 1..7) workManager.cancelUniqueWork(WomensAreaReminderWorker.legacyWorkName(entryId, day))
    }

    /** Cancels every Women's Area reminder — when she turns reminders off, or changes their times. */
    fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(ALL_TAG)
    }

    companion object {
        private const val ALL_TAG = "womens_area_reminder"
        private fun entryTag(entryId: Long) = "womens_area_reminder_entry_$entryId"

        // Existing notification IDs in this app: 1001-1003 (NotificationHelper
        // constants) and 2-ish-digit alarm ids (AlarmSoundService). This base
        // keeps Women's Area reminders in their own namespace.
        private const val NOTIFICATION_ID_BASE = 92_000

        fun notificationId(entryId: Long, day: Int): Int = NOTIFICATION_ID_BASE + (entryId.toInt() * 10 + day)
    }
}
