package com.zmanimclock.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.getSystemService
import com.zmanimclock.app.feature.alarms.data.AlarmType
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.feature.zmanim.data.ZmanimRepository
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.feature.zmanim.model.relevantTimedZmanim
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Keeps the persistent status notification fresh: "הזמן הבא: … · השעון הבא: …".
 *
 * Deliberately NOT a foreground service (Android 15 forbids dataSync FGS from
 * boot): an ongoing notification posted from a broadcast receiver survives on
 * its own, and this receiver re-arms itself via AlarmManager for the moment
 * the current "next zman" passes — so the line flips right on time. Pinged
 * from app launch, boot/time changes, alarm reschedules and the settings
 * toggle.
 */
@AndroidEntryPoint
class StatusNotificationReceiver : BroadcastReceiver() {

    @Inject lateinit var prefsRepository: UserPreferencesRepository
    @Inject lateinit var zmanimRepository: ZmanimRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                refresh(context)
            } catch (e: Exception) {
                Log.e(TAG, "Status refresh failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun refresh(context: Context) {
        val prefs = prefsRepository.schedulingPreferences()
        // NOTE: the persistentNotification preference is applied further down,
        // around the notification call ONLY. It used to early-return here,
        // which also skipped the widget refresh and the self re-arm below —
        // so a setting that reads as being about the notification silently
        // froze the home-screen widget and killed the boundary chain that
        // drives both. Turning the notification off must not stop the clock.
        val showNotification = prefs.persistentNotification

        val location = prefsRepository.prefsToGeoLocation(prefs)
        val cityId = if (prefs.useGps) null else prefs.cityId
        val zone = ZoneId.of(prefs.timeZoneId)
        val now = Instant.now()
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

        // Next zman: first upcoming today (including yesterday's still-pending
        // חצות לילה, in the ~40min window right after midnight — see
        // nextRelevantZman), else tomorrow's first.
        val candle = prefs.candleLightingMinutes.toLong()
        val tzeitShabbat = prefs.tzeitShabbatMinutes.toLong()
        val today = LocalDate.now(zone)
        val next = run {
            val dayToday = zmanimRepository.getDayZmanim(
                location, cityId, today, cacheOnly = true,
                candleLightingOffsetMinutes = candle, tzeitShabbatMinutes = tzeitShabbat,
            )
            val dayYesterday = zmanimRepository.getDayZmanim(
                location, cityId, today.minusDays(1), cacheOnly = true,
                candleLightingOffsetMinutes = candle, tzeitShabbatMinutes = tzeitShabbat,
            )
            com.zmanimclock.app.feature.zmanim.model.nextRelevantZman(dayToday, today, now, dayYesterday)
        } ?: nextZman(location, cityId, today.plusDays(1), now, candle, tzeitShabbat)
        val zmanName = next?.first?.shortName ?: "—"
        val zmanTime = next?.let { (_, instant) -> timeFmt.format(instant.atZone(zone)) } ?: ""

        // Next armed alarm
        // cacheOnly: a receiver has a ~10s goAsync budget — the zman lookups
        // behind this must never reach the network (a cold city could issue
        // dozens of HTTP requests and ANR the broadcast).
        val nextAlarm = alarmScheduler.nextAlarmOccurrence(cacheOnly = true)
        val nextAlarmText = nextAlarm?.let { (alarm, fire) ->
            val time = timeFmt.format(fire.atZone(zone))
            val what = when {
                alarm.label.isNotBlank() -> alarm.label
                alarm.type == AlarmType.ZMAN -> {
                    val name = ZmanKind.fromNameOrNull(alarm.zmanId)?.hebrewName ?: ""
                    if (alarm.offsetMinutes > 0) {
                        "${alarm.offsetMinutes} דק' ${if (alarm.offsetBefore) "לפני" else "אחרי"} $name"
                    } else name
                }
                else -> "שעון מעורר"
            }
            "$time · $what"
        }

        if (showNotification) {
            notificationHelper.showOngoingStatus(zmanName, zmanTime, nextAlarmText)
        } else {
            notificationHelper.cancelOngoingStatus()
        }

        // Zman boundaries also refresh the home-screen widget content.
        // Deliberately outside the notification check — the widget is a
        // separate surface with its own setting (whether it is on the home
        // screen at all).
        com.zmanimclock.app.feature.widget.ZmanWidgetProvider.refresh(context)

        // Re-arm this receiver for the moment the display should change.
        //
        // This MUST happen even when there is no next zman. Previously the
        // re-arm sat inside `next?.let {}`, so a single pass with no upcoming
        // zman — no cached table for the city yet, a lookup failure, the
        // sentinel — silently ended the chain, and the notification and widget
        // then froze on that content for good, with nothing to ever restart
        // them. That is the "stuck on an old time" report at its worst,
        // because it never recovers on its own.
        val am = context.getSystemService<AlarmManager>() ?: return
        val wakeAt = next?.second?.plusSeconds(30)
            // No zman to wait for: try again within the hour so a transient
            // failure cannot become permanent.
            ?: Instant.now().plusSeconds(3600)
        am.setAndAllowWhileIdle(
            AlarmManager.RTC,
            wakeAt.toEpochMilli(),
            refreshPendingIntent(context),
        )
    }

    companion object {
        private const val TAG = "StatusNotification"
        private const val REQUEST_CODE = 7001

        /** Ask for an immediate refresh of the status notification. */
        fun ping(context: Context) {
            context.sendBroadcast(Intent(context, StatusNotificationReceiver::class.java))
        }

        private fun refreshPendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, StatusNotificationReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }

    private suspend fun nextZman(
        location: com.zmanimclock.app.location.model.AppGeoLocation,
        cityId: String?,
        date: LocalDate,
        now: Instant,
        candleLightingMinutes: Long,
        tzeitShabbatMinutes: Long,
    ): Pair<ZmanKind, Instant>? {
        // cacheOnly: a receiver must not hit the network (goAsync ~10s budget)
        val day = zmanimRepository.getDayZmanim(
            location, cityId, date, cacheOnly = true,
            candleLightingOffsetMinutes = candleLightingMinutes,
            tzeitShabbatMinutes = tzeitShabbatMinutes,
        )
        return day.relevantTimedZmanim(date)
            .filter { (_, instant) -> instant.isAfter(now) }
            .minByOrNull { (_, instant) -> instant }
    }
}
