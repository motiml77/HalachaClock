package com.zmanimclock.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.zmanimclock.app.MainActivity
import com.zmanimclock.app.feature.alerts.data.local.AlertDao
import com.zmanimclock.app.feature.alerts.data.local.AlertEntity
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.feature.zmanim.data.ZmanimRepository
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.feature.zmanim.model.instantOf
import com.zmanimclock.app.location.model.AppGeoLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.GregorianCalendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules exact alarms for zman alerts.
 *
 * Reliability patterns (after yuriykulikov/AlarmClock, Apache-2.0):
 *  - [AlarmManager.setAlarmClock] for ringing alarms — the strongest guarantee
 *    on Android (Doze-exempt, surfaces the alarm icon in the status bar).
 *  - setExactAndAllowWhileIdle for silent notification-only alerts.
 *  - Graceful degradation when SCHEDULE_EXACT_ALARM was revoked (Android 12+).
 *  - Everything is re-derived from Room + zmanim engine on boot / time change /
 *    daily, so a missed edge never leaves stale state behind.
 *
 * Each alert schedules only its NEXT occurrence; when it fires (or is
 * dismissed/skipped) [RescheduleWorker] runs and schedules the following one.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alertDao: AlertDao,
    private val zmanimRepository: ZmanimRepository,
    private val prefsRepository: UserPreferencesRepository,
) {
    companion object {
        private const val TAG = "AlarmScheduler"

        /** How many days ahead to search for the next valid occurrence. */
        private const val MAX_LOOKAHEAD_DAYS = 8L
    }

    private val alarmManager: AlarmManager? = context.getSystemService()

    /** Recompute and arm the next occurrence of every active alert. */
    suspend fun rescheduleAll() {
        val prefs = prefsRepository.preferences.first()
        val location = prefsRepository.prefsToGeoLocation(prefs)
        val cityId = if (prefs.useGps) null else prefs.cityId

        val alerts = alertDao.getActiveAlertsList()
        Log.i(TAG, "Rescheduling ${alerts.size} active alerts")
        alerts.forEach { alert ->
            scheduleNextOccurrence(alert, location, cityId)
        }
    }

    /** Arm the next occurrence of a single alert. */
    suspend fun scheduleNextOccurrence(
        alert: AlertEntity,
        location: AppGeoLocation,
        cityId: String?,
    ) {
        val kind = ZmanKind.fromNameOrNull(alert.zmanId) ?: run {
            Log.w(TAG, "Unknown zman id '${alert.zmanId}' for alert ${alert.id}")
            return
        }

        val zone = ZoneId.of(location.timeZone.id)
        val now = Instant.now()
        var date = LocalDate.now(zone)

        repeat(MAX_LOOKAHEAD_DAYS.toInt()) {
            if (shouldSkipDate(alert, date, zone)) {
                date = date.plusDays(1)
                return@repeat
            }
            val day = zmanimRepository.getDayZmanim(location, cityId, date)
            val zmanTime = day.instantOf(kind)
            if (zmanTime != null) {
                val offset = alert.offsetMinutes * 60_000L
                val fireTime = if (alert.offsetBefore) {
                    zmanTime.minusMillis(offset)
                } else {
                    zmanTime.plusMillis(offset)
                }
                if (fireTime.isAfter(now)) {
                    arm(alert, kind, fireTime, zmanTime, zone)
                    return
                }
            }
            date = date.plusDays(1)
        }
        Log.w(TAG, "No occurrence found for alert ${alert.id} within $MAX_LOOKAHEAD_DAYS days")
    }

    /** Arm a snooze firing for an already-ringing alert. */
    fun scheduleSnooze(alertId: Long, title: String, timeText: String, snoozeMinutes: Int) {
        val fireTime = Instant.now().plusSeconds(snoozeMinutes * 60L)
        val intent = triggerIntent(alertId, title, timeText, useSound = true, useVibration = true, snoozeMinutes = snoozeMinutes)
        val pi = triggerPendingIntent(alertId, intent)
        setExact(fireTime.toEpochMilli(), pi, userVisible = true)
        Log.i(TAG, "Snoozed alert $alertId for $snoozeMinutes minutes")
    }

    fun cancelAlert(alertId: Long) {
        val intent = Intent(context, AlarmTriggerReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            alertId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pi?.let { alarmManager?.cancel(it) }
    }

    // === internals ===

    private fun arm(
        alert: AlertEntity,
        kind: ZmanKind,
        fireTime: Instant,
        zmanTime: Instant,
        zone: ZoneId,
    ) {
        val timeText = DateTimeFormatter.ofPattern("HH:mm").format(zmanTime.atZone(zone))
        val title = buildTitle(alert, kind)
        val ringing = alert.useSound || alert.isFullScreenAlarm

        val intent = triggerIntent(
            alertId = alert.id,
            title = title,
            timeText = timeText,
            useSound = ringing,
            useVibration = alert.useVibration,
            snoozeMinutes = alert.snoozeDurationMinutes,
        )
        val pi = triggerPendingIntent(alert.id, intent)
        setExact(fireTime.toEpochMilli(), pi, userVisible = ringing)

        Log.i(TAG, "Armed alert ${alert.id} ($title) at ${fireTime.atZone(zone)}")
    }

    private fun setExact(triggerAtMillis: Long, pi: PendingIntent, userVisible: Boolean) {
        val am = alarmManager ?: return
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        when {
            userVisible && canExact -> {
                // setAlarmClock: Doze-exempt + status bar alarm indicator
                val showPi = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showPi), pi)
            }
            canExact -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            else -> {
                Log.w(TAG, "Exact alarms not permitted — falling back to inexact")
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        }
    }

    private fun triggerIntent(
        alertId: Long,
        title: String,
        timeText: String,
        useSound: Boolean,
        useVibration: Boolean,
        snoozeMinutes: Int,
    ): Intent = Intent(context, AlarmTriggerReceiver::class.java).apply {
        putExtra(AlarmTriggerReceiver.EXTRA_ALERT_ID, alertId)
        putExtra(AlarmTriggerReceiver.EXTRA_TITLE, title)
        putExtra(AlarmTriggerReceiver.EXTRA_TIME_TEXT, timeText)
        putExtra(AlarmTriggerReceiver.EXTRA_USE_SOUND, useSound)
        putExtra(AlarmTriggerReceiver.EXTRA_USE_VIBRATION, useVibration)
        putExtra(AlarmTriggerReceiver.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
    }

    private fun triggerPendingIntent(alertId: Long, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alertId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun buildTitle(alert: AlertEntity, kind: ZmanKind): String {
        if (alert.label.isNotBlank()) return alert.label
        if (alert.offsetMinutes == 0) return kind.hebrewName
        val rel = if (alert.offsetBefore) "לפני" else "אחרי"
        return "${alert.offsetMinutes} דקות $rel ${kind.hebrewName}"
    }

    private fun shouldSkipDate(alert: AlertEntity, date: LocalDate, zone: ZoneId): Boolean {
        if (!alert.skipShabbat && !alert.skipYomTov) return false
        val cal = GregorianCalendar.from(date.atStartOfDay(zone))
        val jewish = JewishCalendar(cal).apply { inIsrael = true }
        val isShabbat = date.dayOfWeek == java.time.DayOfWeek.SATURDAY
        val isYomTov = jewish.isYomTovAssurBemelacha
        return (alert.skipShabbat && isShabbat) || (alert.skipYomTov && isYomTov)
    }
}
