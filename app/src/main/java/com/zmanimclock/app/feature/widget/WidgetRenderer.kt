package com.zmanimclock.app.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.zmanimclock.app.MainActivity
import com.zmanimclock.app.R
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.feature.zmanim.data.ZmanimRepository
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.feature.zmanim.model.instantOf
import com.zmanimclock.app.feature.zmanim.model.relevantTimedZmanim
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders the zmanim widget. The countdown is a system [android.widget.Chronometer]
 * counting down to the next zman — the OS animates it live with zero app
 * wakeups. Content (zman times, next-zman) is re-rendered only on zman
 * boundaries (piggybacking the StatusNotificationReceiver pings) + resize.
 */
@Singleton
class WidgetRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepository: UserPreferencesRepository,
    private val zmanimRepository: ZmanimRepository,
) {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val rowIds = listOf(R.id.widget_row_0, R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3, R.id.widget_row_4)
    private val nameIds = listOf(R.id.widget_name_0, R.id.widget_name_1, R.id.widget_name_2, R.id.widget_name_3, R.id.widget_name_4)
    private val timeIds = listOf(R.id.widget_time_0, R.id.widget_time_1, R.id.widget_time_2, R.id.widget_time_3, R.id.widget_time_4)

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

        val dayToday = zmanimRepository.getDayZmanim(location, cityId, today, cacheOnly = true)
        val dayTomorrow = zmanimRepository.getDayZmanim(location, cityId, today.plusDays(1), cacheOnly = true)

        // Next zman across today→tomorrow (candle-lighting/tzeit-Shabbat only
        // surface on their relevant days — never mid-week)
        val next = dayToday.relevantTimedZmanim(today)
            .filter { it.second.isAfter(now) }
            .minByOrNull { it.second }
            ?: dayTomorrow.relevantTimedZmanim(today.plusDays(1))
                .minByOrNull { it.second }

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_zmanim)

            // Header
            if (next != null) {
                views.setTextViewText(R.id.widget_next_name, "הזמן הבא: ${next.first.hebrewName}")
                views.setTextViewText(R.id.widget_next_time, timeFmt.format(next.second.atZone(zone)))
                // Chronometer counts DOWN to the zman
                val base = SystemClock.elapsedRealtime() +
                    (next.second.toEpochMilli() - System.currentTimeMillis())
                views.setChronometer(R.id.widget_countdown, base, "עוד %s", true)
                views.setChronometerCountDown(R.id.widget_countdown, true)
                views.setViewVisibility(R.id.widget_countdown, View.VISIBLE)
            } else {
                views.setTextViewText(R.id.widget_next_name, prefs.cityNameHebrew)
                views.setTextViewText(R.id.widget_next_time, "פתח לבחירת עיר")
                views.setViewVisibility(R.id.widget_countdown, View.GONE)
            }

            // Selected zman rows
            val selection = WidgetPrefs.getSelection(context, id)
                .mapNotNull { ZmanKind.fromNameOrNull(it) }
                .take(rowIds.size)
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

            // Tap anywhere → open the app
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent())

            manager.updateAppWidget(id, views)
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
