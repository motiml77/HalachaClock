package com.zmanimclock.app.feature.womensarea.scheduling

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.zmanimclock.app.feature.womensarea.model.WomensAreaCalculator
import com.zmanimclock.app.feature.womensarea.security.WomensAreaReminders
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Schedules the reminders for one הפסק טהרה entry — on the 7 clean days
 * (which start on the Hebrew day AFTER the hefsek) and on ערב הטבילה, at the
 * times she chose for each, and only the kinds she turned on. None of them is
 * an app alarm: they never appear in the מעורר tab, the status line or the
 * widget — only as the notification itself, and inside the Women's Area.
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
     * Queues this hefsek's reminders under [reminders]: on each clean day at
     * each clean-day time, and on the 7th day (ערב טבילה) at each tevila time
     * — each kind only if she turned it on, and only moments still ahead (a
     * backfilled hefsek gets only what remains). Every request carries this
     * entry's tag as well as the feature's, so [cancelAll] clears them however many there were.
     */
    fun schedule(entryId: Long, hefsek: LocalDate, reminders: WomensAreaReminders) {
        if (reminders.cleanEnabled) {
            WomensAreaCalculator.cleanDayDates(hefsek).forEachIndexed { index, date ->
                val day = index + 1
                reminders.cleanTimes.forEachIndexed { slot, time ->
                    enqueue(
                        name = "womens_area_clean_${entryId}_${day}_$slot",
                        entryId = entryId,
                        fireAt = date.atTime(time),
                        data = workDataOf(
                            WomensAreaReminderWorker.KEY_KIND to WomensAreaReminderWorker.KIND_CLEAN,
                            WomensAreaReminderWorker.KEY_DAY_NUMBER to day,
                            // Same id for every time on one day: a later
                            // reminder replaces that day's earlier one
                            // instead of stacking up.
                            WomensAreaReminderWorker.KEY_NOTIFICATION_ID to notificationId(entryId, day),
                        ),
                    )
                }
            }
        }
        // No "הערב · לאחר צאת הכוכבים" on a night with no tevila (Yom Kippur / Tisha B'Av).
        if (reminders.tevilaEnabled && WomensAreaCalculator.tevilaNightBlock(hefsek) == null) {
            val tevilaDay = WomensAreaCalculator.tevilaDay(hefsek)
            reminders.tevilaTimes.forEachIndexed { slot, time ->
                enqueue(
                    name = "womens_area_tevila_${entryId}_$slot",
                    entryId = entryId,
                    fireAt = tevilaDay.atTime(time),
                    data = workDataOf(
                        WomensAreaReminderWorker.KEY_KIND to WomensAreaReminderWorker.KIND_TEVILA,
                        WomensAreaReminderWorker.KEY_NOTIFICATION_ID to notificationId(entryId, TEVILA_SLOT),
                    ),
                )
            }
        }
    }

    private fun enqueue(name: String, entryId: Long, fireAt: LocalDateTime, data: androidx.work.Data) {
        val delayMs = Duration.between(LocalDateTime.now(), fireAt).toMillis()
        if (delayMs < 0) return
        val request = OneTimeWorkRequestBuilder<WomensAreaReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(ALL_TAG)
            .addTag(entryTag(entryId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Cancels every Women's Area reminder — before re-queuing the latest
     * hefsek's under the current settings (see WomensAreaViewModel).
     * WorkManager runs operations in order, so a cancel followed by enqueues
     * never loses the new ones.
     */
    fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(ALL_TAG)
    }

    companion object {
        private const val ALL_TAG = "womens_area_reminder"
        private fun entryTag(entryId: Long) = "womens_area_reminder_entry_$entryId"

        /** The tevila evening's notification id sits after the 7 clean days' (1..7). */
        private const val TEVILA_SLOT = 8

        // Existing notification IDs in this app: 1001-1003 (NotificationHelper
        // constants) and 2-ish-digit alarm ids (AlarmSoundService). This base
        // keeps Women's Area reminders in their own namespace.
        private const val NOTIFICATION_ID_BASE = 92_000

        fun notificationId(entryId: Long, day: Int): Int = NOTIFICATION_ID_BASE + (entryId.toInt() * 10 + day)
    }
}
