package com.zmanimclock.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.zmanimclock.app.feature.alerts.data.local.AlertDao
import com.zmanimclock.app.feature.alerts.data.local.AlertEntity
import com.zmanimclock.app.feature.zmanim.data.ZmanimCalculator
import com.zmanimclock.app.feature.zmanim.data.model.ZmanId
import com.zmanimclock.app.location.model.AppGeoLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZmanAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val zmanimCalculator: ZmanimCalculator,
    private val alertDao: AlertDao,
) {
    private val alarmManager: AlarmManager? = context.getSystemService()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    suspend fun scheduleAllAlerts(location: AppGeoLocation) {
        val activeAlerts = alertDao.getActiveAlertsList()
        val dayZmanim = zmanimCalculator.calculateZmanim(location)

        activeAlerts.forEach { alert ->
            scheduleAlert(alert, dayZmanim.zmanim.associate { it.id to it.time }, location)
        }
    }

    fun scheduleAlert(
        alert: AlertEntity,
        zmanimTimes: Map<ZmanId, Date?>,
        location: AppGeoLocation,
    ) {
        val zmanId = try { ZmanId.valueOf(alert.zmanId) } catch (e: Exception) { return }
        val zmanTime = zmanimTimes[zmanId] ?: return

        val offsetMillis = alert.offsetMinutes * 60 * 1000L
        val fireTime = if (alert.offsetBefore) {
            zmanTime.time - offsetMillis
        } else {
            zmanTime.time + offsetMillis
        }

        // Don't schedule if time has passed
        if (fireTime < System.currentTimeMillis()) return

        val intent = Intent(context, AlarmTriggerReceiver::class.java).apply {
            putExtra(AlarmTriggerReceiver.EXTRA_ALERT_ID, alert.id)
            putExtra(AlarmTriggerReceiver.EXTRA_ZMAN_NAME, zmanId.name)
            putExtra(AlarmTriggerReceiver.EXTRA_ZMAN_TIME, timeFormat.format(zmanTime))
            putExtra(AlarmTriggerReceiver.EXTRA_USE_SOUND, alert.useSound)
            putExtra(AlarmTriggerReceiver.EXTRA_USE_VIBRATION, alert.useVibration)
            putExtra(AlarmTriggerReceiver.EXTRA_FULL_SCREEN, alert.isFullScreenAlarm)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alert.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        alarmManager?.let { am ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireTime, pendingIntent)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireTime, pendingIntent)
            }
        }
    }

    fun cancelAlert(alertId: Long) {
        val intent = Intent(context, AlarmTriggerReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alertId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pendingIntent?.let { alarmManager?.cancel(it) }
    }
}
