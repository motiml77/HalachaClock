package com.zmanimclock.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.getSystemService
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.zmanimclock.app.feature.chaitables.data.ChaiTablesPreloader
import com.zmanimclock.app.feature.chaitables.worker.ChaiTablesRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class ZmanimApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var chaiTablesPreloader: ChaiTablesPreloader

    private val appScope = CoroutineScope(Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        loadPreBundledData()
        scheduleChaiTablesRefresh()
    }

    /**
     * Load pre-bundled ChaiTables visible sunrise data from assets on first launch.
     * This makes the data available immediately without requiring network access.
     */
    private fun loadPreBundledData() {
        appScope.launch {
            chaiTablesPreloader.ensureDataLoaded()
        }
    }

    /**
     * Schedule daily background refresh of ChaiTables visible sunrise data.
     * Only runs when network is available. Uses KEEP policy to avoid duplicate work.
     */
    private fun scheduleChaiTablesRefresh() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<ChaiTablesRefreshWorker>(
            1, TimeUnit.DAYS,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ChaiTablesRefreshWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest,
        )
    }

    private fun createNotificationChannels() {
        val manager = getSystemService<NotificationManager>() ?: return

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            "התראות זמנים",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "התראות על זמני הלכה"
            enableVibration(true)
            setBypassDnd(true)
        }

        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            "תזכורות",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "תזכורות שקטות על זמנים קרבים"
        }

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "שירות רקע",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "שירות חישוב זמנים ברקע"
        }

        manager.createNotificationChannels(listOf(alarmChannel, reminderChannel, serviceChannel))
    }

    companion object {
        const val CHANNEL_ALARM = "zmanim_alarm"
        const val CHANNEL_REMINDER = "zmanim_reminder"
        const val CHANNEL_SERVICE = "zmanim_service"
    }
}
