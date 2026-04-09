package com.zmanimclock.app.scheduling

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zmanimclock.app.location.LocationProvider
import com.zmanimclock.app.location.model.AppGeoLocation
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.TimeZone

@HiltWorker
class DailyRescheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val alarmScheduler: ZmanAlarmScheduler,
    private val locationProvider: LocationProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val location = locationProvider.getCurrentLocation() ?: getDefaultLocation()
        alarmScheduler.scheduleAllAlerts(location)
        return Result.success()
    }

    private fun getDefaultLocation(): AppGeoLocation {
        return AppGeoLocation(
            cityNameHebrew = "ירושלים",
            cityNameEnglish = "Jerusalem",
            latitude = 31.778,
            longitude = 35.235,
            elevation = 800.0,
            timeZone = TimeZone.getTimeZone("Asia/Jerusalem"),
        )
    }
}
