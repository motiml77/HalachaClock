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
import com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.feature.zmanim.model.instantOf
import com.zmanimclock.app.feature.zmanim.model.isZmanRelevantOn
import com.zmanimclock.app.location.model.AppGeoLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import com.zmanimclock.app.feature.zmanim.format.ZmanTime
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

        /**
         * PendingIntent request-code slots. AlarmManager keys pending alarms
         * by PendingIntent equality, and equality IGNORES extras — so every
         * purpose must get its own request code or one silently replaces (and
         * cancelling one silently disarms) the other.
         *   0 — the alarm's regular next occurrence
         *   1 — a snooze re-ring
         *   8 — the wake-check re-ring (see WakeCheckReceiver)
         */
        const val SLOT_MAIN = 0
        const val SLOT_SNOOZE = 1
        const val SLOT_WAKE_CHECK = 8

        fun requestCode(alarmId: Long, slot: Int): Int = alarmId.toInt() * 10 + slot
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
            // Isolate each alarm: one bad row (missing zman, bad city data…) must
            // never stop every later alarm from being armed.
            runCatching {
                // DELIBERATELY does not touch snoozeCount here any more. This
                // used to blindly zero it for every active alarm on every
                // reschedule — and rescheduleAll runs from a dozen unrelated
                // triggers (midnight, any alarm edit, any toggle, boot…),
                // including while THIS alarm is mid-ring after the user just
                // snoozed. That raced the snooze's own increment and could
                // silently restore a spent snooze budget. The budget is now
                // reset only where an occurrence genuinely ends: on dismiss,
                // and when autoSilence gives up unattended — see
                // AlarmSoundService.
                scheduleNextOccurrence(alarm, location, cityId, ZmanOffsets.from(prefs))
            }.onFailure { Log.e(TAG, "Failed to schedule alarm ${alarm.id}", it) }
        }
    }

    /** B2: skip the alarm's next occurrence (keeps later repeats). */
    suspend fun skipNext(alarmId: Long) {
        val alarm = alarmDao.getAlarmById(alarmId) ?: return
        val prefs = prefsRepository.schedulingPreferences()
        val location = prefsRepository.prefsToGeoLocation(prefs)
        val cityId = if (prefs.useGps) null else prefs.cityId
        val offsets = ZmanOffsets.from(prefs)
        // The watermark MUST be computed with the user's real offsets. With the
        // defaults it could land before the occurrence it was meant to
        // suppress, and the alarm would ring despite the card saying it was
        // skipped.
        val next = computeNextOccurrence(alarm, location, cityId, offsets = offsets) ?: return
        alarmDao.setSkipUntil(alarmId, next.toEpochMilli() + 60_000L)
        scheduleNextOccurrence(
            alarm.copy(skipUntilEpochMs = next.toEpochMilli() + 60_000L),
            location, cityId, offsets,
        )
    }

    suspend fun undoSkip(alarmId: Long) {
        alarmDao.setSkipUntil(alarmId, 0)
        val alarm = alarmDao.getAlarmById(alarmId) ?: return
        val prefs = prefsRepository.schedulingPreferences()
        val location = prefsRepository.prefsToGeoLocation(prefs)
        val cityId = if (prefs.useGps) null else prefs.cityId
        scheduleNextOccurrence(
            alarm.copy(skipUntilEpochMs = 0), location, cityId, ZmanOffsets.from(prefs),
        )
    }

    /** Arm the next occurrence of a single alarm. */
    suspend fun scheduleNextOccurrence(
        alarm: AlarmEntity,
        location: AppGeoLocation,
        cityId: String?,
        offsets: ZmanOffsets,
    ) {
        val zone = zoneFor(alarm, location)
        val fireTime = computeNextOccurrence(alarm, location, cityId, offsets = offsets)
        if (fireTime == null) {
            // Disarm rather than leaving a stale alarm armed from a previous
            // configuration — otherwise it fires at the OLD time.
            Log.w(TAG, "No occurrence for alarm ${alarm.id} within lookahead — disarming")
            cancelAlarm(alarm.id)
            return
        }
        arm(alarm, fireTime, zone)
    }

    /**
     * The zone an alarm's clock fields actually mean.
     *
     * A FIXED alarm is a WALL-CLOCK promise: "wake me at 06:00" means 06:00 on
     * the clock the user is looking at, which is the DEVICE's zone — never the
     * selected city's. The two coincide for a user in Israel with an Israeli
     * city, which is why this went unnoticed, but the city default is
     * Asia/Jerusalem (UserPreferencesRepository:31) and nothing ever seeds it
     * from the device. So on a phone anywhere else every fixed alarm silently
     * resolved in Jerusalem time — an alarm set for 06:00 in New York armed for
     * 23:00 the previous evening — while AlarmsScreen kept rendering the raw
     * "06:00" digits, so the list looked right and the alarm simply never rang
     * when expected. That is precisely what an overseas closed-tester, or a
     * Play reviewer, does first.
     *
     * A ZMAN alarm is the opposite and unchanged: it is anchored to the sun
     * over the selected city, so it must keep resolving in that city's zone
     * no matter where the device happens to be.
     */
    private fun zoneFor(alarm: AlarmEntity, location: AppGeoLocation): ZoneId =
        when (alarm.type) {
            AlarmType.FIXED -> ZoneId.systemDefault()
            AlarmType.ZMAN -> ZoneId.of(location.timeZone.id)
        }

    /** The next fire time of one alarm (no side effects). Honors skip-next. */
    suspend fun computeNextOccurrence(
        alarm: AlarmEntity,
        location: AppGeoLocation,
        cityId: String?,
        cacheOnly: Boolean = false,
        offsets: ZmanOffsets,
    ): Instant? {
        val zone = zoneFor(alarm, location)
        // Skip-next (B2): treat occurrences up to skipUntil as already past
        val now = maxOf(Instant.now(), Instant.ofEpochMilli(alarm.skipUntilEpochMs))
        return when (alarm.type) {
            AlarmType.FIXED -> AlarmTimeCalculator.nextFixedOccurrence(alarm, zone, now)
            AlarmType.ZMAN ->
                nextZmanOccurrence(alarm, location, cityId, zone, now, cacheOnly, offsets)
        }
    }

    /** The earliest upcoming firing across ALL active alarms (for the status bar). */
    suspend fun nextAlarmOccurrence(cacheOnly: Boolean = false): Pair<AlarmEntity, Instant>? {
        val prefs = prefsRepository.schedulingPreferences()
        val location = prefsRepository.prefsToGeoLocation(prefs)
        val cityId = if (prefs.useGps) null else prefs.cityId
        return alarmDao.getActiveAlarmsList()
            .mapNotNull { alarm ->
                computeNextOccurrence(alarm, location, cityId, cacheOnly, ZmanOffsets.from(prefs))?.let { alarm to it }
            }
            .minByOrNull { (_, fire) -> fire }
    }

    /** Compute the next firing of a snoozed alarm. */
    fun scheduleSnooze(alarmId: Long, snoozeMinutes: Int) {
        val fireTime = Instant.now().plusSeconds(snoozeMinutes * 60L)
        // Own slot: a snooze must never replace the alarm's next occurrence
        setExact(fireTime.toEpochMilli(), triggerPendingIntent(alarmId, SLOT_SNOOZE))
        Log.i(TAG, "Snoozed alarm $alarmId for $snoozeMinutes minutes")
    }

    /** Cancels every slot of this alarm (main occurrence, snooze, wake-check). */
    fun cancelAlarm(alarmId: Long) {
        listOf(SLOT_MAIN, SLOT_SNOOZE, SLOT_WAKE_CHECK).forEach { cancelSlot(alarmId, it) }
    }

    /**
     * Cancels only a pending SNOOZE re-ring, leaving the alarm's regular
     * occurrence and wake-check untouched.
     *
     * Needed because dismiss() used to never call this at all: an alarm that
     * auto-snoozed (ring duration elapsed, unattended) armed SLOT_SNOOZE: if
     * the user then acknowledged the alarm from a stale ringing screen — one
     * left over from that auto-silence — dismiss() stopped the (already
     * stopped) sound and returned, but the armed snooze survived and rang
     * again five minutes later regardless of the "acknowledgement".
     */
    fun cancelSnooze(alarmId: Long) = cancelSlot(alarmId, SLOT_SNOOZE)

    private fun cancelSlot(alarmId: Long, slot: Int) {
        PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, slot),
            Intent(context, AlarmTriggerReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { alarmManager?.cancel(it) }
    }

    /** The zman instant an alarm points at, for a given date (UI preview too). */
    suspend fun zmanInstantFor(
        alarm: AlarmEntity,
        location: AppGeoLocation,
        cityId: String?,
        date: LocalDate,
        cacheOnly: Boolean = false,
        offsets: ZmanOffsets,
    ): Instant? {
        val kind = ZmanKind.fromNameOrNull(alarm.zmanId) ?: return null
        val day = zmanimRepository.getDayZmanim(
            location, cityId, date,
            cacheOnly = cacheOnly,
            candleLightingOffsetMinutes = offsets.candleLightingMinutes,
            tzeitShabbatMinutes = offsets.tzeitShabbatMinutes,
        )
        // Read directly, not through instantOf, so the 16.1°-degree fallback
        // below only applies to SCHEDULING — the display screens still show a
        // genuine blank when the sun never reaches 16.1° that day, which is
        // the honest answer. An ALARM anchored to it must never simply stop
        // firing: in London/Manchester/Antwerp latitudes 16.1° depression does
        // not occur at all for weeks around midsummer, and the old code left
        // the alarm silently disarmed — still shown as ON — for up to 47
        // consecutive mornings. Fall back to the luach's own MGA (always
        // defined whenever sunrise/sunset exist) so the alarm still rings,
        // just not anchored to the shita that happens to be undefined today.
        val zmanTime = day.instantOf(kind) ?: mgaFallback(day, kind) ?: return null
        val offset = alarm.offsetMinutes * 60_000L
        return if (alarm.offsetBefore) zmanTime.minusMillis(offset) else zmanTime.plusMillis(offset)
    }

    private fun mgaFallback(day: com.zmanimclock.app.feature.zmanim.engine.DayZmanim, kind: ZmanKind): Instant? =
        when (kind) {
            ZmanKind.SOF_ZMAN_SHMA_MGA_16_1_DEG -> day.sofZmanShmaMga
            ZmanKind.SOF_ZMAN_TFILA_MGA_16_1_DEG -> day.sofZmanTfilaMga
            else -> null
        }

    // === internals ===

    private suspend fun nextZmanOccurrence(
        alarm: AlarmEntity,
        location: AppGeoLocation,
        cityId: String?,
        zone: ZoneId,
        now: Instant,
        cacheOnly: Boolean,
        offsets: ZmanOffsets,
    ): Instant? {
        val kind = ZmanKind.fromNameOrNull(alarm.zmanId) ?: return null
        var date = LocalDate.now(zone)
        repeat(AlarmTimeCalculator.MAX_LOOKAHEAD_DAYS) {
            // Two SEPARATE gates, deliberately not merged:
            //  1. Does this zman even exist on the ANCHOR date (הדלקת נרות /
            //     צאת שבת only some days) — cheap, skips a real computation
            //     for the days that plainly do not apply.
            //  2. Is the user's day-of-week / skip-Shabbat / skip-Yom-Tov
            //     selection satisfied on the date the alarm ACTUALLY FIRES —
            //     which can differ from the anchor date. חצות לילה is the
            //     standing example: it is defined as chatzot + 12h, so for
            //     roughly half the year (whenever DST pushes chatzot past
            //     12:00) the anchor date's midnight lands on the CALENDAR DAY
            //     AFTER. Testing the day-of-week/Shabbat gate against the
            //     anchor date used to silently fire the alarm a day early (or
            //     late) around every DST boundary, and inverted skipShabbat/
            //     skipYomTov to the wrong night. Gating on the fire instant's
            //     own local date is correct for every zman, including the
            //     ordinary daytime ones where the two dates always coincide.
            if (isZmanRelevantOn(kind, date, zone)) {
                val fire = zmanInstantFor(alarm, location, cityId, date, cacheOnly, offsets)
                if (fire != null && fire.isAfter(now)) {
                    val fireDate = LocalDate.ofInstant(fire, zone)
                    if (AlarmTimeCalculator.isDayAllowed(alarm, fireDate, zone)) return fire
                }
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun arm(alarm: AlarmEntity, fireTime: Instant, zone: ZoneId) {
        setExact(fireTime.toEpochMilli(), triggerPendingIntent(alarm.id))
        val display = DateTimeFormatter.ofPattern("dd/MM ${ZmanTime.PATTERN_24H}").format(fireTime.atZone(zone))
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

    private fun triggerPendingIntent(alarmId: Long, slot: Int = SLOT_MAIN): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, slot),
            Intent(context, AlarmTriggerReceiver::class.java).apply {
                putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, alarmId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
