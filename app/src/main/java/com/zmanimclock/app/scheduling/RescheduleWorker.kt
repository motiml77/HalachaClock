package com.zmanimclock.app.scheduling

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
    private val billingRepository: com.zmanimclock.app.feature.subscription.BillingRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Ask Play first, so this run arms against today's answer. Without
            // it, a subscriber who cancelled and never opens the app again
            // keeps a cached YES — and, since a stale YES deliberately still
            // rings (AccessPolicy.alarmsAllowed), keeps their alarms forever.
            // This worker runs daily whether or not the app is ever opened,
            // which bounds that to a day. Bounded by a timeout so a wedged
            // Play service can never stop alarms from being re-armed; a failed
            // query leaves the cache untouched, which is the safe direction.
            if (com.zmanimclock.app.BuildConfig.PAYWALL_ENABLED) {
                runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(BILLING_TIMEOUT_MS) { billingRepository.refresh() }
                }.onFailure { Log.w(TAG, "Entitlement refresh failed; using cache", it) }
            }
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
        private const val BILLING_TIMEOUT_MS = 15_000L

        /**
         * The unique work name every one-off reschedule request should use.
         *
         * Every caller used to do a plain `enqueue()`, and there are (or were)
         * eight of them: every alarm firing, every save/toggle/skip/delete in
         * the UI, city changes, settings changes, boot. WorkManager runs no
         * two of them in sequence — nothing stopped several from executing
         * concurrently, each against its OWN stale snapshot of
         * `getActiveAlarmsList()`. A user who toggled an alarm OFF while an
         * earlier, still-running reschedule was mid-loop (e.g. blocked on a
         * ChaiTables network fetch for an EARLIER alarm) could have that
         * earlier run re-arm the alarm the user just switched off, moments
         * after cancelAlarm() had already cleared it — silently re-enabling
         * an alarm the UI, and the DB, both agree is off.
         *
         * `enqueueUniqueWork(REPLACE)` makes this impossible: a new request
         * always cancels any run still in flight before starting, so at most
         * one reschedule is ever actually executing.
         */
        private const val UNIQUE_WORK_NAME = "alarm_reschedule"

        fun enqueueUnique(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RescheduleWorker>().build(),
            )
        }
    }
}
