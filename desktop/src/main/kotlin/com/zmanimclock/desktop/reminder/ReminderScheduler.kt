package com.zmanimclock.desktop.reminder

import com.zmanimclock.app.feature.zmanim.format.asZmanTime
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.feature.zmanim.model.relevantTimedZmanim
import com.zmanimclock.desktop.data.DesktopZmanimService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Silent zman reminders — the scheduling half. The surface that actually
 * appears on screen is [ReminderPopupWindow]; see that file for why it is a
 * Compose window and not a system notification.
 *
 * THIS IS NOT AN ALARM CLOCK. No sound, no looping audio, no waking a sleeping
 * machine, no notification queue that unspools when the computer comes back.
 * Everything below follows from that one sentence.
 *
 * THREE DECISIONS THAT LOOK LIKE DETAILS AND ARE NOT
 *
 * 1. A WALL-CLOCK TICK, NOT A RELATIVE DELAY.
 *    The obvious implementation — work out "the next zman is in 3h 12m" and
 *    schedule a one-shot for that — is wrong on Windows.
 *    `ScheduledThreadPoolExecutor` derives its deadlines from
 *    `System.nanoTime()`, which does NOT advance while the machine is
 *    suspended. Sleep the laptop for an hour and a delay armed for 3h 12m
 *    fires at 4h 12m of wall time; a batch of short ones instead bursts all at
 *    once on resume. So this class never asks "how long until", it only ever
 *    asks "what does the clock say now" — every [TICK] seconds, comparing
 *    `Instant.now()` against the day's table. A tick that arrives late simply
 *    reads a later clock and behaves correctly; there is no accumulated drift
 *    to correct because nothing was ever accumulated.
 *
 * 2. STALE REMINDERS ARE DROPPED, NEVER REPLAYED.
 *    The delivery window is five minutes, copied deliberately from Windows'
 *    own documented semantics for ScheduledToastNotification: if the machine
 *    was off or asleep through the scheduled moment and stayed that way for
 *    longer than five minutes, the notification is dropped as no longer
 *    relevant. A zman that passed four minutes ago is worth showing; one that
 *    passed at dawn is noise. And if several came due during a long sleep, at
 *    most ONE appears — see [sweepDue].
 *
 * 3. FIRED FLAGS LIVE ON DISK.
 *    Keeping "already shown" in memory would mean a restart, a crash, or
 *    Windows' Fast Startup (which turns "shut down" into a hybrid hibernate)
 *    could show the same reminder twice. [FiredLog] persists one flag per
 *    (local date, zman) under %LOCALAPPDATA%\HalachClock\, and the flag is
 *    written BEFORE the popup is shown, so even a hard kill mid-popup cannot
 *    produce a second showing.
 *
 * Recomputation happens on startup, on date rollover, and whenever a
 * preference that can move a zman changes — all three fall out of the cache
 * key in [scheduleFor], and [invalidate] forces it immediately.
 */
class ReminderScheduler(
    private val service: DesktopZmanimService,
    private val firedLog: FiredLog = FiredLog.default(),
    private val tick: Duration = TICK,
    private val window: Duration = DELIVERY_WINDOW,
    private val clock: () -> Instant = Instant::now,
) {

    private val _pending = MutableStateFlow<PendingReminder?>(null)

    /** The reminder currently on screen, or null. Collect with `collectAsState()`. */
    val pending: StateFlow<PendingReminder?> = _pending.asStateFlow()

    /** Called by the popup when it times out or the user clicks it. */
    fun dismiss() {
        _pending.value = null
    }

    /**
     * Drops the cached day table so the next tick rebuilds it. The cache key
     * already covers every input, so this is an optimisation for immediacy —
     * call it after the settings screen writes a change, rather than letting
     * the user wait up to one tick to see it take effect.
     */
    fun invalidate() {
        cacheKey = null
    }

    /**
     * The tick loop. Runs until its coroutine is cancelled; safe on any
     * dispatcher, since the only shared state it touches is a StateFlow and
     * the service's own synchronized caches.
     *
     * The sleep is to the next wall-clock boundary rather than a flat
     * `delay(tick)`, for the same reason the whole class exists: it makes the
     * loop self-correcting after a suspend instead of letting the phase walk.
     */
    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            try {
                sweepOnce()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                // One bad tick must never kill the loop — a transient failure
                // computing one day would otherwise silence reminders until
                // the next restart, invisibly.
                System.err.println("ReminderScheduler: tick failed — ${t.message}")
            }
            val period = tick.toMillis().coerceAtLeast(1_000L)
            delay(period - (clock().toEpochMilli() % period))
        }
    }

    /** One pass: what is due right now, what is merely stale, what is neither. */
    internal fun sweepOnce() {
        // One banner at a time, and the banner now waits for אישור rather
        // than dismissing itself — so an unacknowledged reminder holds later
        // ones back deliberately. Nothing is lost quietly: the held-back
        // zmanim are swept on the next pass after the click, and whatever has
        // aged past the five-minute delivery window is dropped as stale
        // instead of fired in a burst.
        if (_pending.value != null) return

        val now = clock()
        val zone = service.zone
        val today = LocalDate.now(zone)

        val candidates = scheduleFor(today, zone)
            .filter { (kind, at) -> !firedLog.hasFired(at.atZone(zone).toLocalDate(), kind.name) }

        val sweep = sweepDue(candidates, now, window)

        // Marked first, shown second. The reverse order would re-fire on a
        // crash between the two.
        sweep.handled.forEach { (kind, at) ->
            firedLog.markFired(at.atZone(zone).toLocalDate(), kind.name)
        }
        sweep.show?.let { (kind, at) ->
            _pending.value = PendingReminder(
                kind = kind,
                name = kind.shortName,
                time = at.asZmanTime(zone),
                at = at,
            )
        }
    }

    // ---- the day's table -------------------------------------------------

    private data class ScheduleKey(
        val date: LocalDate,
        val cityId: String,
        val candleMinutes: Long,
        val tzeitShabbatMinutes: Long,
        val wanted: Set<String>,
    )

    private var cacheKey: ScheduleKey? = null
    private var cached: List<Pair<ZmanKind, Instant>> = emptyList()

    /**
     * The zmanim the user opted into, for [today], sorted.
     *
     * Rebuilt whenever the date rolls over or any preference in the key
     * changes — that is the whole of requirement "recompute on startup, date
     * rollover, and preference change", expressed as data rather than as three
     * separate callbacks that can each be forgotten.
     *
     * YESTERDAY'S חצות לילה IS INCLUDED ON PURPOSE. Solar midnight for date D
     * is D's chatzot plus twelve hours, which lands on the CALENDAR DAY AFTER
     * D for roughly half the year (whenever chatzot itself falls after 12:00
     * wall clock, i.e. under DST). So the חצות לילה that actually occurs in
     * tonight's small hours belongs to yesterday's DayZmanim, not today's —
     * today's own row for that kind is about 24 hours out. This mirrors
     * `nextRelevantZman`, which had to learn the same thing.
     */
    private fun scheduleFor(today: LocalDate, zone: ZoneId): List<Pair<ZmanKind, Instant>> {
        val prefs = service.prefs
        val key = ScheduleKey(
            date = today,
            cityId = prefs.cityId,
            candleMinutes = prefs.candleLightingMinutes,
            tzeitShabbatMinutes = prefs.tzeitShabbatMinutes,
            wanted = prefs.reminderZmanim,
        )
        if (key == cacheKey) return cached

        val wanted = prefs.reminderZmanim
        val list = if (wanted.isEmpty()) {
            // Reminders are opt-in and default to none, so the common case
            // costs nothing at all: no engine call, no day cached.
            emptyList()
        } else {
            buildList {
                addAll(service.day(today).relevantTimedZmanim(today))
                service.day(today.minusDays(1)).chatzotLayla
                    ?.let { add(ZmanKind.CHATZOT_LAYLA to it) }
            }
                .filter { (kind, _) -> kind.name in wanted }
                .sortedBy { (_, at) -> at }
        }

        cacheKey = key
        cached = list
        return list
    }

    companion object {
        /**
         * How often the clock is read. Anything in the 15–30s band is fine:
         * the reminder is a courtesy, not a deadline, and the app is already
         * resident for the tray and the widget, so the cost is nil.
         */
        val TICK: Duration = Duration.ofSeconds(20)

        /**
         * Windows' own rule, adopted verbatim rather than invented:
         * "If the computer is turned off during the scheduled delivery time,
         * and remains off for longer than 5 minutes, the notification will be
         * dropped as no longer relevant."
         */
        val DELIVERY_WINDOW: Duration = Duration.ofMinutes(5)

    }
}

/** A reminder that is on screen right now. */
data class PendingReminder(
    val kind: ZmanKind,
    /** Already-localized Hebrew label — the popup does no naming of its own. */
    val name: String,
    /** Formatted through the shared [asZmanTime], so it matches the phone exactly. */
    val time: String,
    val at: Instant,
)

/** Where a zman sits relative to now and the delivery window. */
enum class ReminderState {
    /** Still in the future. */
    NOT_YET,

    /** Passed within the delivery window — worth showing. */
    DUE,

    /** Passed longer ago than the window — record it and stay quiet. */
    MISSED,
}

/**
 * The whole "should this reminder appear" decision, as a pure function.
 *
 * Boundary is deliberate and pinned by test: exactly [window] late still
 * counts as DUE, because Windows' wording is "longer than 5 minutes".
 */
fun reminderStateOf(
    zman: Instant,
    now: Instant,
    window: Duration = ReminderScheduler.DELIVERY_WINDOW,
): ReminderState {
    if (zman.isAfter(now)) return ReminderState.NOT_YET
    return if (Duration.between(zman, now) > window) ReminderState.MISSED else ReminderState.DUE
}

/**
 * What one pass over [candidates] concludes.
 *
 * [handled] is everything that must be flagged as fired — both what is shown
 * and what is merely written off as missed. Flagging the missed ones matters
 * as much as flagging the shown one: without it, a zman that went by while the
 * machine was asleep would be re-evaluated on every single tick forever.
 */
data class ReminderSweep(
    val show: Pair<ZmanKind, Instant>?,
    val handled: List<Pair<ZmanKind, Instant>>,
)

/**
 * Pick at most one reminder to show, and list everything to write off.
 *
 * AT MOST ONE, EVEN IF SEVERAL ARE DUE. Waking a laptop at 19:35 to a stack of
 * popups for מנחה קטנה, פלג and שקיעה is exactly the "notification queue
 * unspooling on resume" behaviour this app refuses to have. The one shown is
 * the LATEST of the due set — the most recent zman is the one the user can
 * still act on; the older ones are already history by the time they are read.
 */
fun sweepDue(
    candidates: List<Pair<ZmanKind, Instant>>,
    now: Instant,
    window: Duration = ReminderScheduler.DELIVERY_WINDOW,
): ReminderSweep {
    val handled = candidates.filter { (_, at) ->
        reminderStateOf(at, now, window) != ReminderState.NOT_YET
    }
    val show = handled
        .filter { (_, at) -> reminderStateOf(at, now, window) == ReminderState.DUE }
        .maxByOrNull { (_, at) -> at }
    return ReminderSweep(show = show, handled = handled)
}

/**
 * The "already fired" flags, on disk.
 *
 * One line per flag, `2026-08-07|SHKIA`, in
 * `%LOCALAPPDATA%\HalachClock\fired.txt`. Plain text rather than JSON or
 * Properties because the whole record is two fields and being able to open the
 * file and see why a reminder did not appear is worth more than a format.
 *
 * The key is the local date OF THE ZMAN'S OWN INSTANT, not of the solar day it
 * was derived from. That distinction is load-bearing: חצות לילה derived from
 * Wednesday occurs in Thursday's small hours, and keying it to Wednesday would
 * collide with Thursday's own חצות לילה row.
 *
 * [file] is a constructor parameter so tests can point it at a temp directory
 * instead of the real profile.
 */
class FiredLog(
    private val file: File,
    private val retentionDays: Long = 14,
) {

    private val entries: MutableSet<String> = LinkedHashSet()

    init {
        runCatching {
            if (file.isFile) {
                file.readLines(Charsets.UTF_8)
                    .map(String::trim)
                    .filter { it.isNotEmpty() && it.contains(SEP) }
                    .forEach { entries.add(it) }
            }
        }
        // A corrupt or unreadable log must not stop the app from running. The
        // cost of losing it is at worst one duplicate reminder; the cost of
        // throwing here is no reminders at all.
    }

    fun hasFired(date: LocalDate, kind: String): Boolean = key(date, kind) in entries

    /**
     * Records that (date, kind) has been dealt with, and prunes anything older
     * than [retentionDays] before [date].
     *
     * Pruning relative to the date being written, rather than to
     * `LocalDate.now()`, keeps this function total and testable — marks always
     * happen for roughly today, so the effect is identical without the hidden
     * clock dependency.
     */
    fun markFired(date: LocalDate, kind: String) {
        val added = entries.add(key(date, kind))
        val cutoff = date.minusDays(retentionDays)
        val pruned = entries.removeAll { line -> dateOf(line)?.isBefore(cutoff) == true }
        if (added || pruned) save()
    }

    /** Test/diagnostic view of what is on disk. */
    fun size(): Int = entries.size

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(entries.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        }
    }

    private fun key(date: LocalDate, kind: String) = "$date$SEP$kind"

    private fun dateOf(line: String): LocalDate? =
        runCatching { LocalDate.parse(line.substringBefore(SEP)) }.getOrNull()

    companion object {
        private const val SEP = '|'

        /**
         * The real location, next to settings.properties. Mirrors
         * [com.zmanimclock.desktop.data.DesktopPrefs]'s own resolution,
         * including its fallback for a machine with no LOCALAPPDATA.
         */
        fun default(): FiredLog {
            val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
            return FiredLog(File(File(base, "HalachClock"), "fired.txt"))
        }
    }
}
