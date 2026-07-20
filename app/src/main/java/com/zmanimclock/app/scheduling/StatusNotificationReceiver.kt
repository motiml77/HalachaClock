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
        if (!prefs.persistentNotification) {
            notificationHelper.cancelOngoingStatus()
            return
        }

        val location = prefsRepository.prefsToGeoLocation(prefs)
        val cityId = if (prefs.useGps) null else prefs.cityId
        val zone = ZoneId.of(prefs.timeZoneId)
        val now = Instant.now()
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

        // Next zman: first upcoming today, else tomorrow's first
        val next = nextZman(location, cityId, LocalDate.now(zone), now)
            ?: nextZman(location, cityId, LocalDate.now(zone).plusDays(1), now)
        val nextZmanText = next?.let { (kind, instant) ->
            "${kind.hebrewName} · ${timeFmt.format(instant.atZone(zone))}"
        } ?: "—"

        // Next armed alarm
        val nextAlarm = alarmScheduler.nextAlarmOccurrence()
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

        notificationHelper.showOngoingStatus(nextZmanText, nextAlarmText)

        // Zman boundaries also refresh the home-screen widget content
        com.zmanimclock.app.feature.widget.ZmanWidgetProvider.refresh(context)

        // Re-arm this receiver for the moment the display should change
        next?.let { (_, instant) ->
            val am = context.getSystemService<AlarmManager>() ?: return
            am.setAndAllowWhileIdle(
                AlarmManager.RTC,
                instant.plusSeconds(30).toEpochMilli(),
                refreshPendingIntent(context),
            )
        }
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
    ): Pair<ZmanKind, Instant>? {
        // cacheOnly: a receiver must not hit the network (goAsync ~10s budget)
        val day = zmanimRepository.getDayZmanim(location, cityId, date, cacheOnly = true)
        return day.relevantTimedZmanim(date)
            .filter { (_, instant) -> instant.isAfter(now) }
            .minByOrNull { (_, instant) -> instant }
    }
}
