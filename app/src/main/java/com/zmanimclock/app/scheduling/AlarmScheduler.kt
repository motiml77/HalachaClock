package com.zmanimclock.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import com.zmanimclock.app.MainActivity
import com.zmanimclock.app.feature.alarms.data.AlarmDao
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.alarms.data.AlarmType
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules exact alarms for both alarm types.
 *
 * Reliability patterns (after yuriykulikov/AlarmClock, Apache-2.0):
 *  - [AlarmManager.setAlarmClock] — Doze-exempt, status-bar alarm indicator.
 *  - Graceful degradation when SCHEDULE_EXACT_ALARM was revoked (Android 12+).
 *  - State is always re-derivable: boot / time-change / daily worker call
 *    [rescheduleAll], which recomputes every next occurrence from Room.
 *
 * Each alarm arms only its NEXT occurrence; after it rings,
 * [AlarmSoundService] re-enqueues a [RescheduleWorker] — the chain never
 * breaks. ZMAN alarms recompute against the location's zmanim every day.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmDao: AlarmDao,
    private val zmanimRepository: ZmanimRepository,
    private val prefsRepository: UserPreferencesRepository,
) {
    companion object {
        private const val TAG = "AlarmScheduler"
    }

    private val alarmManager: AlarmManager? = context.getSystemService()

    /** Recompute and arm the next occurrence of every active alarm. */
    suspend fun rescheduleAll() {
        val prefs = prefsRepository.schedulingPreferences()
        val location = prefsRepository.prefsToGeoLocation(prefs)
        val cityId = if (prefs.useGps) null else prefs.cityId

        val alarms = alarmDao.getActiveAlarmsList()
        Log.i(TAG, "Rescheduling ${alarms.size} active alarms")
        alarms.forEach { alarm ->
            // A fresh occurrence gets a fresh snooze budget (B3)
            if (alarm.snoozeCount != 0) alarmDao.setSnoozeCount(alarm.id, 0)
            scheduleNextOccurrence(alarm, location, cityId)
        }
    }

    /** B2: skip the alarm's next occurrence (keeps later repeats). */
    suspend fun skipNext(alarmId: Long) {
        val alarm = alarmDao.getAlarmById(alarmId) ?: return
        val prefs = prefsRepository.schedulingPreferences()
        val location = prefsRepository.prefsToGeoLocation(prefs)
        val cityId = if (prefs.useGps) null else prefs.cityId
        val next = computeNextOccurrence(alarm, location, cityId) ?: return
        alarmDao.setSkipUntil(alarmId, next.toEpochMilli() + 60_000L)
        scheduleNextOccurrence(alarm.copy(skipUntilEpochMs = next.toEpochMilli() + 60_000L), location, cityId)
    }

    suspend fun undoSkip(alarmId: Long) {
        alarmDao.setSkipUntil(alarmId, 0)
        val alarm = alarmDao.getAlarmById(alarmId) ?: return
        val prefs = prefsRepository.schedulingPreferences()
        val location = prefsRepository.prefsToGeoLocation(prefs)
        val cityId = if (prefs.useGps) null else prefs.cityId
        scheduleNextOccurrence(alarm.copy(skipUntilEpochMs = 0), location, cityId)
    }

    /** Arm the next occurrence of a single alarm. */
    suspend fun scheduleNextOccurrence(
        alarm: AlarmEntity,
        location: AppGeoLocation,
        cityId: String?,
    ) {
        val zone = ZoneId.of(location.timeZone.id)
        val fireTime = computeNextOccurrence(alarm, location, cityId)
        if (fireTime == null) {
            Log.w(TAG, "No occurrence for alarm ${alarm.id} within lookahead")
            return
        }
        arm(alarm, fireTime, zone)
    }

    /** The next fire time of one alarm (no side effects). Honors skip-next. */
    suspend fun computeNextOccurrence(
        alarm: AlarmEntity,
        location: AppGeoLocation,
        cityId: String?,
    ): Instant? {
        val zone = ZoneId.of(location.timeZone.id)
        // Skip-next (B2): treat occurrences up to skipUntil as already past
        val now = maxOf(Instant.now(), Instant.ofEpochMilli(alarm.skipUntilEpochMs))
        return when (alarm.type) {
            AlarmType.FIXED -> AlarmTimeCalculator.nextFixedOccurrence(alarm, zone, now)
            AlarmType.ZMAN -> nextZmanOccurrence(alarm, location, cityId, zone, now)
        }
    }

    /** The earliest upcoming firing across ALL active alarms (for the status bar). */
    suspend fun nextAlarmOccurrence(): Pair<AlarmEntity, Instant>? {
        val prefs = prefsRepository.schedulingPreferences()
        val location = prefsRepository.prefsToGeoLocation(prefs)
        val cityId = if (prefs.useGps) null else prefs.cityId
        return alarmDao.getActiveAlarmsList()
            .mapNotNull { alarm ->
                computeNextOccurrence(alarm, location, cityId)?.let { alarm to it }
            }
            .minByOrNull { (_, fire) -> fire }
    }

    /** Compute the next firing of a snoozed alarm. */
    fun scheduleSnooze(alarmId: Long, snoozeMinutes: Int) {
        val fireTime = Instant.now().plusSeconds(snoozeMinutes * 60L)
        val pi = triggerPendingIntent(alarmId)
        setExact(fireTime.toEpochMilli(), pi)
        Log.i(TAG, "Snoozed alarm $alarmId for $snoozeMinutes minutes")
    }

    fun cancelAlarm(alarmId: Long) {
        val intent = Intent(context, AlarmTriggerReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pi?.let { alarmManager?.cancel(it) }
    }

    /** The zman instant an alarm points at, for a given date (UI preview too). */
    suspend fun zmanInstantFor(
        alarm: AlarmEntity,
        location: AppGeoLocation,
        cityId: String?,
        date: LocalDate,
    ): Instant? {
        val kind = ZmanKind.fromNameOrNull(alarm.zmanId) ?: return null
        val day = zmanimRepository.getDayZmanim(location, cityId, date)
        val zmanTime = day.instantOf(kind) ?: return null
        val offset = alarm.offsetMinutes * 60_000L
        return if (alarm.offsetBefore) zmanTime.minusMillis(offset) else zmanTime.plusMillis(offset)
    }

    // === internals ===

    private suspend fun nextZmanOccurrence(
        alarm: AlarmEntity,
        location: AppGeoLocation,
        cityId: String?,
        zone: ZoneId,
        now: Instant,
    ): Instant? {
        var date = LocalDate.now(zone)
        repeat(AlarmTimeCalculator.MAX_LOOKAHEAD_DAYS) {
            if (AlarmTimeCalculator.isDayAllowed(alarm, date, zone)) {
                val fire = zmanInstantFor(alarm, location, cityId, date)
                if (fire != null && fire.isAfter(now)) return fire
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun arm(alarm: AlarmEntity, fireTime: Instant, zone: ZoneId) {
        setExact(fireTime.toEpochMilli(), triggerPendingIntent(alarm.id))
        val display = DateTimeFormatter.ofPattern("dd/MM HH:mm").format(fireTime.atZone(zone))
        Log.i(TAG, "Armed alarm ${alarm.id} (${alarm.type}) at $display")
    }

    private fun setExact(triggerAtMillis: Long, pi: PendingIntent) {
        val am = alarmManager ?: return
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        when {
            canExact -> {
                val showPi = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showPi), pi)
            }
            else -> {
                Log.w(TAG, "Exact alarms not permitted — falling back to inexact")
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        }
    }

    private fun triggerPendingIntent(alarmId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            Intent(context, AlarmTriggerReceiver::class.java).apply {
                putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, alarmId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
