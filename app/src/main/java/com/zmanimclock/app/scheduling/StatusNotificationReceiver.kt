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
import com.zmanimclock.app.feature.zmanim.format.asZmanTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
 *
 * IT MUST ALSO HEAL. Since Android 14 a notification that is not tied to a
 * foreground service can be swiped away, and this one is not. Two ways it
 * comes back, both without the user opening the app:
 *  - the notification's delete intent points here ([ACTION_DISMISSED]), so a
 *    swipe re-posts it at once;
 *  - the chain never sleeps longer than [STATUS_HEAL_INTERVAL] ([statusNextWake]),
 *    so anything that removes the line WITHOUT a delete intent — an OEM
 *    cleaner, "clear all" — is undone within minutes instead of at the next
 *    zman, which can be hours away.
 * The Settings switch stays the way to turn the line off for good.
 */
@AndroidEntryPoint
class StatusNotificationReceiver : BroadcastReceiver() {

    @Inject lateinit var prefsRepository: UserPreferencesRepository
    @Inject lateinit var zmanimRepository: ZmanimRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var entitlementStore: com.zmanimclock.app.feature.subscription.EntitlementStore

    override fun onReceive(context: Context, intent: Intent) {
        // Android 14+ lets the user swipe an ongoing notification away. The
        // delete intent lands here, and the refresh below posts it straight
        // back — that IS the recovery, so there is nothing else to do.
        if (intent.action == ACTION_DISMISSED) Log.d(TAG, "Status line dismissed — restoring")
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
        // THE STATUS LINE IS PART OF THE PAID APP too — the next zman and the
        // next alarm, always on the lock screen. While the app is locked it is
        // withdrawn (the subscription-lapsed notification is what the user
        // sees instead), and the widget refresh below draws its locked state.
        val unlocked = com.zmanimclock.app.feature.subscription.AccessPolicy.appAccess(
            com.zmanimclock.app.BuildConfig.PAYWALL_ENABLED,
            entitlementStore.cached(),
            offers = null,
            firstCheckDone = true,
        ) == com.zmanimclock.app.feature.subscription.AppAccess.Allowed
        val showNotification = prefs.persistentNotification && unlocked

        val location = prefsRepository.prefsToGeoLocation(prefs)
        val cityId = if (prefs.useGps) null else prefs.cityId
        val zone = ZoneId.of(prefs.timeZoneId)
        val now = Instant.now()

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
            com.zmanimclock.app.feature.zmanim.model.nextRelevantZman(
                dayToday, today, now, dayYesterday, prefs.nextZmanFilter,
            )
        } ?: nextZman(location, cityId, today.plusDays(1), now, candle, tzeitShabbat, prefs.nextZmanFilter)
        val zmanName = next?.first?.shortName ?: "—"
        val zmanTime = next?.let { (_, instant) -> instant.asZmanTime(zone) } ?: ""

        // Next armed alarm
        // cacheOnly: a receiver has a ~10s goAsync budget — the zman lookups
        // behind this must never reach the network (a cold city could issue
        // dozens of HTTP requests and ANR the broadcast).
        val nextAlarm = alarmScheduler.nextAlarmOccurrence(cacheOnly = true)
        val nextAlarmText = nextAlarm?.let { (alarm, fire) ->
            val time = fire.asZmanTime(zone)
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
        val wakeAt = statusNextWake(next?.second, Instant.now())
        am.setAndAllowWhileIdle(
            AlarmManager.RTC,
            wakeAt.toEpochMilli(),
            refreshPendingIntent(context),
        )
    }

    companion object {
        private const val TAG = "StatusNotification"
        private const val REQUEST_CODE = 7001

        /**
         * Carried by the notification's delete intent: the user swiped the line
         * away. [onReceive] treats it like any other ping — which re-posts it —
         * but a distinct action keeps that PendingIntent separate from the
         * AlarmManager one and says what happened in a log.
         */
        const val ACTION_DISMISSED = "com.zmanimclock.app.status.DISMISSED"

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
        filter: Set<String>,
    ): Pair<ZmanKind, Instant>? {
        // cacheOnly: a receiver must not hit the network (goAsync ~10s budget)
        val day = zmanimRepository.getDayZmanim(
            location, cityId, date, cacheOnly = true,
            candleLightingOffsetMinutes = candleLightingMinutes,
            tzeitShabbatMinutes = tzeitShabbatMinutes,
        )
        return day.relevantTimedZmanim(date)
            // The same filter as nextRelevantZman's own tomorrow fallback
            // (see WidgetRenderer) — otherwise the status line reverts to
            // announcing every zman, unfiltered, the moment today's selected
            // ones have all passed. That silent revert is exactly the "it
            // just shows everything again" report.
            .filter { (kind, instant) -> instant.isAfter(now) && (filter.isEmpty() || kind.name in filter) }
            .minByOrNull { (_, instant) -> instant }
    }
}

/**
 * The longest the status chain may sleep before it looks again. Long enough to
 * cost nothing (a cache-only read and one `notify`), short enough that a line
 * something removed comes back before anyone has missed it. 15 minutes is also
 * about the floor Android grants an inexact alarm while the device dozes, so a
 * shorter cap would only be rounded up.
 */
internal val STATUS_HEAL_INTERVAL: Duration = Duration.ofMinutes(15)

/** How long after a zman passes the line is re-drawn — after, so it reads the NEXT one. */
private const val BOUNDARY_SLACK_SECONDS = 30L

/**
 * When the status chain should next run: 30 seconds after [nextZman] passes so
 * the line flips right on time, but never later than [STATUS_HEAL_INTERVAL]
 * from [now]. With no upcoming zman (no cached table yet, a failed lookup) it
 * is simply the heal interval — the retry a transient failure needs.
 */
internal fun statusNextWake(nextZman: Instant?, now: Instant): Instant {
    val healBy = now.plus(STATUS_HEAL_INTERVAL)
    val atBoundary = nextZman?.plusSeconds(BOUNDARY_SLACK_SECONDS) ?: return healBy
    return if (atBoundary.isBefore(healBy)) atBoundary else healBy
}
