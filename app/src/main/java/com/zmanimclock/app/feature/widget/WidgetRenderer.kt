package com.zmanimclock.app.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.MainActivity
import com.zmanimclock.app.R
import com.zmanimclock.app.feature.alarms.data.AlarmDao
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.alarms.data.AlarmType
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.feature.zmanim.data.ZmanimRepository
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.feature.zmanim.model.instantOf
import com.zmanimclock.app.feature.zmanim.model.relevantTimedZmanim
import com.zmanimclock.app.scheduling.AlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.GregorianCalendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders the zmanim widget.
 *
 * Each widget instance carries its own [WidgetPrefs.Config], so the same
 * layout serves anything from a bare Hebrew-date strip to a full board with
 * zmanim and the user's alarms. Sections the user turned off are hidden
 * outright rather than rendered empty.
 *
 * The countdown is a system [android.widget.Chronometer] counting down to the
 * next zman — the OS animates it live with zero app wakeups. Content is
 * re-rendered on zman boundaries (piggybacking the StatusNotificationReceiver
 * pings), on resize, and on time/date/timezone changes.
 */
@Singleton
class WidgetRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepository: UserPreferencesRepository,
    private val zmanimRepository: ZmanimRepository,
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler,
) {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFmt = DateTimeFormatter.ofPattern("d.M.yyyy")

    private val hebrewFormatter = HebrewDateFormatter().apply {
        isHebrewFormat = true
        isUseGershGershayim = true
    }

    private val rowIds = listOf(
        R.id.widget_row_0, R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3,
        R.id.widget_row_4, R.id.widget_row_5, R.id.widget_row_6, R.id.widget_row_7,
    )
    private val nameIds = listOf(
        R.id.widget_name_0, R.id.widget_name_1, R.id.widget_name_2, R.id.widget_name_3,
        R.id.widget_name_4, R.id.widget_name_5, R.id.widget_name_6, R.id.widget_name_7,
    )
    private val timeIds = listOf(
        R.id.widget_time_0, R.id.widget_time_1, R.id.widget_time_2, R.id.widget_time_3,
        R.id.widget_time_4, R.id.widget_time_5, R.id.widget_time_6, R.id.widget_time_7,
    )
    private val alarmRowIds = listOf(
        R.id.widget_alarm_row_0, R.id.widget_alarm_row_1,
        R.id.widget_alarm_row_2, R.id.widget_alarm_row_3,
    )
    private val alarmNameIds = listOf(
        R.id.widget_alarm_name_0, R.id.widget_alarm_name_1,
        R.id.widget_alarm_name_2, R.id.widget_alarm_name_3,
    )
    private val alarmTimeIds = listOf(
        R.id.widget_alarm_time_0, R.id.widget_alarm_time_1,
        R.id.widget_alarm_time_2, R.id.widget_alarm_time_3,
    )

    /** Re-render every widget instance. Cache-only (may run from a receiver). */
    suspend fun renderAll() {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ZmanWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val prefs = prefsRepository.schedulingPreferences()
        val location = prefsRepository.prefsToGeoLocation(prefs)
        val cityId = if (prefs.useGps) null else prefs.cityId
        val zone = ZoneId.of(prefs.timeZoneId)
        val now = Instant.now()
        val today = LocalDate.now(zone)

        val candle = prefs.candleLightingMinutes.toLong()
        val tzeitShabbat = prefs.tzeitShabbatMinutes.toLong()
        val dayToday = zmanimRepository.getDayZmanim(
            location, cityId, today, cacheOnly = true,
            candleLightingOffsetMinutes = candle, tzeitShabbatMinutes = tzeitShabbat,
        )
        val dayTomorrow = zmanimRepository.getDayZmanim(
            location, cityId, today.plusDays(1), cacheOnly = true,
            candleLightingOffsetMinutes = candle, tzeitShabbatMinutes = tzeitShabbat,
        )

        // Next zman across today→tomorrow (candle-lighting/tzeit-Shabbat only
        // surface on their relevant days — never mid-week)
        val next = dayToday.relevantTimedZmanim(today)
            .filter { it.second.isAfter(now) }
            .minByOrNull { it.second }
            ?: dayTomorrow.relevantTimedZmanim(today.plusDays(1))
                .minByOrNull { it.second }

        // Alarms are only queried if at least one widget actually asks for
        // them — this runs from a broadcast receiver on a goAsync budget.
        val configs = ids.associateWith { WidgetPrefs.getConfig(context, it) }
        val upcomingAlarms =
            if (configs.values.any { it.showAlarms }) upcomingAlarms(zone) else emptyList()

        val hebrew = hebrewDate(today, zone)
        val gregorian = dateFmt.format(today)

        for (id in ids) {
            val config = configs.getValue(id)
            val views = RemoteViews(context.packageName, R.layout.widget_zmanim)

            // --- Hebrew date ---
            if (config.showHebrewDate) {
                views.setViewVisibility(R.id.widget_date_section, View.VISIBLE)
                views.setViewVisibility(R.id.widget_gregorian_date, View.VISIBLE)
                views.setTextViewText(R.id.widget_hebrew_date, hebrew)
                views.setTextViewText(R.id.widget_city, prefs.cityNameHebrew)
                views.setTextViewText(R.id.widget_gregorian_date, gregorian)
            } else {
                views.setViewVisibility(R.id.widget_date_section, View.GONE)
                views.setViewVisibility(R.id.widget_gregorian_date, View.GONE)
            }

            // --- Next zman + countdown ---
            if (config.showNextZman && next != null) {
                views.setViewVisibility(R.id.widget_next_section, View.VISIBLE)
                views.setTextViewText(R.id.widget_next_name, "הזמן הבא: ${next.first.hebrewName}")
                views.setTextViewText(R.id.widget_next_time, timeFmt.format(next.second.atZone(zone)))
                // Chronometer counts DOWN to the zman
                val base = SystemClock.elapsedRealtime() +
                    (next.second.toEpochMilli() - System.currentTimeMillis())
                views.setChronometer(R.id.widget_countdown, base, "עוד %s", true)
                views.setChronometerCountDown(R.id.widget_countdown, true)
                views.setViewVisibility(R.id.widget_countdown, View.VISIBLE)
            } else if (config.showNextZman) {
                // Asked for, but nothing to show (no city / no cached data yet)
                views.setViewVisibility(R.id.widget_next_section, View.VISIBLE)
                views.setTextViewText(R.id.widget_next_name, prefs.cityNameHebrew)
                views.setTextViewText(R.id.widget_next_time, "פתח לבחירת עיר")
                views.setViewVisibility(R.id.widget_countdown, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_next_section, View.GONE)
            }

            // --- Selected zman rows ---
            val selection = if (config.showZmanim) {
                config.zmanim.mapNotNull { ZmanKind.fromNameOrNull(it) }.take(rowIds.size)
            } else {
                emptyList()
            }
            views.setViewVisibility(
                R.id.widget_zmanim_section,
                if (selection.isEmpty()) View.GONE else View.VISIBLE,
            )
            rowIds.indices.forEach { i ->
                val kind = selection.getOrNull(i)
                val instant = kind?.let { dayToday.instantOf(it) }
                if (kind != null && instant != null) {
                    views.setViewVisibility(rowIds[i], View.VISIBLE)
                    views.setTextViewText(nameIds[i], kind.hebrewName)
                    views.setTextViewText(timeIds[i], timeFmt.format(instant.atZone(zone)))
                } else {
                    views.setViewVisibility(rowIds[i], View.GONE)
                }
            }

            // --- The user's alarms ---
            val alarms = if (config.showAlarms) upcomingAlarms.take(config.alarmCount) else emptyList()
            if (alarms.isEmpty()) {
                views.setViewVisibility(R.id.widget_alarms_section, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_alarms_section, View.VISIBLE)
                views.setTextViewText(R.id.widget_alarms_header, "שעונים מעוררים")
                alarmRowIds.indices.forEach { i ->
                    val entry = alarms.getOrNull(i)
                    if (entry == null) {
                        views.setViewVisibility(alarmRowIds[i], View.GONE)
                    } else {
                        views.setViewVisibility(alarmRowIds[i], View.VISIBLE)
                        views.setTextViewText(alarmNameIds[i], entry.label)
                        views.setTextViewText(alarmTimeIds[i], entry.time)
                    }
                }
            }

            // Tap anywhere → open the app
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent())

            manager.updateAppWidget(id, views)
        }
    }

    private data class AlarmLine(val label: String, val time: String)

    /**
     * Active alarms with their next fire time, soonest first.
     *
     * cacheOnly: this can run from a receiver with a ~10s goAsync budget, so
     * no network is allowed. Each alarm is isolated — one bad row must not
     * blank the whole widget.
     */
    private suspend fun upcomingAlarms(zone: ZoneId): List<AlarmLine> {
        val prefs = prefsRepository.schedulingPreferences()
        val location = prefsRepository.prefsToGeoLocation(prefs)
        val cityId = if (prefs.useGps) null else prefs.cityId
        return alarmDao.getActiveAlarmsList()
            .mapNotNull { alarm ->
                runCatching {
                    alarmScheduler.computeNextOccurrence(
                        alarm, location, cityId, cacheOnly = true,
                        offsets = com.zmanimclock.app.scheduling.ZmanOffsets.from(prefs),
                    )
                }.getOrNull()?.let { alarm to it }
            }
            .sortedBy { (_, fire) -> fire }
            .map { (alarm, fire) ->
                AlarmLine(label = alarmLabel(alarm), time = timeFmt.format(fire.atZone(zone)))
            }
    }

    private fun alarmLabel(alarm: AlarmEntity): String {
        val name = alarm.label.ifBlank {
            if (alarm.type == AlarmType.ZMAN) {
                ZmanKind.fromNameOrNull(alarm.zmanId)?.shortName ?: "זמן הלכתי"
            } else {
                "שעון מעורר"
            }
        }
        return name
    }

    private fun hebrewDate(date: LocalDate, zone: ZoneId): String =
        runCatching {
            hebrewFormatter.format(JewishDate(GregorianCalendar.from(date.atStartOfDay(zone))))
        }.getOrDefault("")

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
