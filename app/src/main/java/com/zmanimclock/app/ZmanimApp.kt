package com.zmanimclock.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Provides the WorkManager configuration manually (the default initializer is
 * disabled in the manifest) so that Hilt can inject dependencies into Workers —
 * needed by the ChaiTables refresh worker and the daily alarm reschedule worker.
 */
@HiltAndroidApp
class ZmanimApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
