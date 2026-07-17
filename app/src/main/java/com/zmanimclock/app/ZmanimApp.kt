package com.zmanimclock.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.zmanimclock.app.feature.chaitables.data.ChaiTablesPreloader
import com.zmanimclock.app.feature.chaitables.worker.ChaiTablesRefreshWorker
import com.zmanimclock.app.scheduling.NotificationHelper
import com.zmanimclock.app.scheduling.RescheduleWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Application entry point.
 *
 * WorkManager is configured manually (the default initializer is disabled in
 * the manifest) so Hilt can inject dependencies into Workers.
 */
@HiltAndroidApp
class ZmanimApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var chaiTablesPreloader: ChaiTablesPreloader

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Direct Boot (A3): before first unlock, WorkManager + credential
        // storage are unavailable. The directBootAware BootReceiver handles
        // rescheduling meanwhile; this heavy setup waits for a normal launch.
        val unlocked = getSystemService(android.os.UserManager::class.java)?.isUserUnlocked ?: true
        if (!unlocked) return

        notificationHelper.createChannels()
        loadPreBundledData()
        schedulePeriodicWork()
        // Keep the "next zman / next alarm" status line alive
        com.zmanimclock.app.scheduling.StatusNotificationReceiver.ping(this)
    }

    /** Bundle-shipped ChaiTables data → Room, so netz works offline on day one. */
    private fun loadPreBundledData() {
        appScope.launch {
            chaiTablesPreloader.ensureDataLoaded()
        }
    }

    /**
     * Two safety-net periodic jobs:
     *  - ChaiTables refresh (network-gated): repairs a failed initial fetch or
     *    a city change; effectively a no-op once the cache is full.
     *  - Daily alarm reschedule: re-derives tomorrow's zmanim and re-arms all
     *    alerts even if no alarm fired today (defense-in-depth vs OEM Doze).
     */
    private fun schedulePeriodicWork() {
        val workManager = WorkManager.getInstance(this)

        workManager.enqueueUniquePeriodicWork(
            ChaiTablesRefreshWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ChaiTablesRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build(),
        )

        workManager.enqueueUniquePeriodicWork(
            RescheduleWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RescheduleWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}
