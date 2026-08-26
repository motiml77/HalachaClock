package com.zmanimclock.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zmanimclock.app.feature.calendar.model.CalendarDayMeta
import com.zmanimclock.app.feature.calendar.model.HebrewMonthSequence
import com.zmanimclock.app.feature.calendar.model.MonthGrid
import com.zmanimclock.app.feature.calendar.model.MonthGridBuilder
import com.zmanimclock.app.feature.location.CityCatalog
import com.zmanimclock.app.feature.location.CityInfo
import com.zmanimclock.app.feature.zmanim.engine.DayZmanim
import com.zmanimclock.app.feature.zmanim.engine.EngineLocation
import com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine
import com.zmanimclock.app.feature.zmanim.format.asZmanTime
import com.zmanimclock.app.feature.zmanim.format.asZmanTimeOrNull
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.feature.zmanim.model.instantOf
import com.zmanimclock.app.feature.zmanim.model.nextRelevantZman
import com.zmanimclock.app.feature.zmanim.model.hebrewNameOf
import com.zmanimclock.app.feature.zmanim.model.relevantTimedZmanim
import com.zmanimclock.desktop.ui.AlertRowData
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One row of the zmanim list, ready to draw. */
data class ZmanRow(
    val kind: ZmanKind,
    val name: String,
    val time: String,
    val isNext: Boolean,
    val isPast: Boolean,
)

/** Everything the UI needs about one day. */
data class DayView(
    val date: LocalDate,
    val isToday: Boolean,
    val hebrewDate: String,
    val gregorianDate: String,
    val weekdayName: String,
    val cityName: String,
    val basedOnVisibleSunrise: Boolean,
    val headlineLabel: String?,
    val headlineTime: String?,
    val rows: List<ZmanRow>,
    val notes: List<String>,
    val undefined: Boolean,
)

/**
 * The desktop's single source of zmanim.
 *
 * Everything here delegates to :zmanim-engine — the same module the Android
 * app uses, including the same asZmanTime formatter — so the two builds cannot
 * show different times for the same city and day. The only logic that lives
 * here is presentation: which datum headlines a day, and how notes are worded.
 *
 * CURRENT LIMITATION, STATED RATHER THAN HIDDEN: the ChaiTables visible-sunrise
 * layer is not wired up yet, so this build computes on the mishor (sea-level)
 * day and reports basedOnVisibleSunrise = false. The UI shows the same muted
 * "מישור" tag Android shows when it has no terrain data, so the difference is
 * visible to the user rather than silent. Only the הנץ row is affected — the
 * seasonal-hour grid runs on the mishor day on both platforms regardless.
 */
class DesktopZmanimService(initialPrefs: DesktopPrefs) {

    var prefs: DesktopPrefs by mutableStateOf(initialPrefs)
        private set

    private val engine = MaranZmanimEngine()
    private val dayCache = object : LinkedHashMap<LocalDate, DayZmanim>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LocalDate, DayZmanim>) = size > 90
    }
    private val gridCache = object : LinkedHashMap<Int, MonthGrid>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, MonthGrid>) = size > 24
    }

    val city: CityInfo
        get() = CityCatalog.byId(prefs.cityId)
            ?: CityCatalog.byId(DesktopPrefs.DEFAULT_CITY_ID)
            ?: CityCatalog.cities.first()

    val zone: ZoneId get() = ZoneId.of(city.timeZoneId)

    val allCities: List<CityInfo> get() = CityCatalog.cities

    fun searchCities(q: String): List<CityInfo> = CityCatalog.search(q)

    /** Applies a change, persists it, and drops anything it invalidates. */
    fun update(block: (DesktopPrefs) -> DesktopPrefs) {
        val next = block(prefs.copy())
        val locationChanged = next.cityId != prefs.cityId ||
            next.candleLightingMinutes != prefs.candleLightingMinutes ||
            next.tzeitShabbatMinutes != prefs.tzeitShabbatMinutes
        prefs = next
        next.save()
        if (locationChanged) synchronized(dayCache) { dayCache.clear() }
        // The month grid is location-independent, so it is deliberately NOT
        // cleared here — a common reflex, and wasted work.
    }

    private fun location() = EngineLocation(
        name = city.nameEnglish,
        latitude = city.latitude,
        longitude = city.longitude,
        // Zero on purpose: the mishor doctrine. Terrain is ChaiTables' job,
        // never elevation math. See MaranZmanimEngine.
        elevationMeters = 0.0,
        timeZoneId = city.timeZoneId,
    )

    fun day(date: LocalDate): DayZmanim = synchronized(dayCache) {
        dayCache.getOrPut(date) {
            engine.calculate(
                location = location(),
                date = date,
                candleLightingOffsetMinutes = prefs.candleLightingMinutes,
                tzeitShabbatMinutes = prefs.tzeitShabbatMinutes,
            )
        }
    }

    /** The Hebrew-calendar month at [index] in [HebrewMonthSequence]. */
    // ---- alerts ----------------------------------------------------------

    /** Adds or replaces by id — one call for both "new" and "edited". */
    fun putAlert(alert: ZmanAlert) = update { p ->
        val without = p.alerts.filterNot { it.id == alert.id }
        p.copy(alerts = without + alert)
    }

    fun removeAlert(id: String) = update { p ->
        p.copy(alerts = p.alerts.filterNot { it.id == id })
    }

    /**
     * The zmanim offered when choosing what an alert hangs on.
     *
     * Exactly what the זמנים tab lists for the day, PLUS the חצות לילה that
     * actually falls in tonight's small hours — which belongs to yesterday's
     * DayZmanim, not today's, for roughly half the year. The scheduler already has
     * to know this; the picker must agree with it or it would offer a zman the
     * scheduler then cannot resolve.
     */
    fun zmanimForPicker(date: LocalDate): List<Pair<ZmanKind, Instant>> = buildList {
        addAll(day(date).relevantTimedZmanim(date))
        if (none { it.first == ZmanKind.CHATZOT_LAYLA }) {
            day(date.minusDays(1)).chatzotLayla?.let { add(ZmanKind.CHATZOT_LAYLA to it) }
        }
    }.sortedBy { it.second }

    /**
     * Every alert with the clock time it resolves to on [date], ordered the
     * way the day happens. Alerts whose zman does not occur that day keep
     * their place in the list but carry no time, and sort last.
     */
    fun alertsInFiringOrder(date: LocalDate): List<AlertRowData> {
        val zmanim = zmanimForPicker(date).toMap()
        return prefs.alerts
            .map { alert ->
                val at = zmanim[alert.kind]?.plus(Duration.ofMinutes(alert.offsetMinutes.toLong()))
                AlertRowData(alert, at?.asZmanTime(zone))
            }
            .sortedWith(
                compareBy(
                    { it.firesAt == null },
                    { it.firesAt ?: "" },
                ),
            )
    }

    fun monthGrid(index: Int): MonthGrid = synchronized(gridCache) {
        gridCache.getOrPut(index) { MonthGridBuilder.build(HebrewMonthSequence.refAt(index)) }
    }

    fun meta(date: LocalDate): CalendarDayMeta = MonthGridBuilder.metaFor(date)

    /** The next zman today, honouring the user's filter. Null once the day is done. */
    fun nextZman(now: Instant = Instant.now()): Pair<ZmanKind, Instant>? {
        val today = LocalDate.now(zone)
        return nextRelevantZman(day(today), today, now, day(today.minusDays(1)), prefs.nextZmanFilter)
    }

    fun view(date: LocalDate, now: Instant = Instant.now()): DayView {
        val d = day(date)
        val m = meta(date)
        val today = LocalDate.now(zone)
        val isToday = date == today
        val timed = d.relevantTimedZmanim(date)
        val next = if (isToday) nextZman(now) else null
        val headline = headlineFor(isToday, next, timed, m)

        return DayView(
            date = date,
            isToday = isToday,
            hebrewDate = hebrewDateOf(date, m),
            gregorianDate = "${date.dayOfMonth}.${date.monthValue}.${date.year}",
            weekdayName = WEEKDAYS[date.dayOfWeek.value % 7],
            cityName = city.nameHebrew,
            basedOnVisibleSunrise = d.basedOnVisibleSunrise,
            headlineLabel = headline?.first,
            headlineTime = headline?.second,
            rows = timed.map { (kind, instant) ->
                ZmanRow(
                    kind = kind,
                    name = d.hebrewNameOf(kind),
                    time = instant.asZmanTime(zone),
                    // Only meaningful for today; on any other day every row
                    // would read as "coming up shortly", which misleads.
                    isNext = isToday && kind == next?.first,
                    isPast = isToday && instant.isBefore(now),
                )
            },
            notes = notesFor(m),
            undefined = timed.isEmpty(),
        )
    }

    private fun headlineFor(
        isToday: Boolean,
        next: Pair<ZmanKind, Instant>?,
        timed: List<Pair<ZmanKind, Instant>>,
        meta: CalendarDayMeta,
    ): Pair<String, String>? {
        fun timeOf(kind: ZmanKind) =
            timed.firstOrNull { it.first == kind }?.second?.asZmanTimeOrNull(zone)

        if (isToday) {
            next?.let { (kind, instant) ->
                return "הזמן הבא — ${kind.hebrewName}" to instant.asZmanTime(zone)
            }
        }
        // "The next zman" is meaningless three weeks out, so a non-today day
        // headlines with whatever actually defines it.
        timeOf(ZmanKind.CANDLE_LIGHTING)?.let { return "הדלקת נרות" to it }
        timeOf(ZmanKind.TZEIT_SHABBAT)?.let { return "צאת שבת" to it }
        meta.fast?.let { fast -> timeOf(ZmanKind.TZEIT_LECHUMRA)?.let { return "סיום ${fast.name}" to it } }
        timeOf(ZmanKind.HANETZ)?.let { return "הנץ החמה" to it }
        return null
    }

    private fun notesFor(meta: CalendarDayMeta): List<String> = buildList {
        meta.parshaName?.let { add("פרשת $it") }
        meta.specialShabbatName?.let { add("שבת $it") }
        meta.yomTovName?.let { add(it) }
        meta.fast?.let { add(it.name) }
        meta.dayOfChanukah?.let { add("נר ${ORDINALS.getOrElse(it - 1) { "$it" }} של חנוכה") }
        // Neutral wording: the omer is counted at night, so the number
        // "belonging" to a Hebrew day is announced the evening before, and this
        // app is Gregorian-day anchored.
        meta.omerDay?.let { add("העומר: $it") }
    }

    private fun hebrewDateOf(date: LocalDate, meta: CalendarDayMeta): String {
        val idx = HebrewMonthSequence.indexOf(date)
        if (idx < 0) return meta.hebrewDayLabel
        val g = monthGrid(idx)
        return "${meta.hebrewDayLabel} ${g.hebrewMonthLabel} ${g.hebrewYearLabel}"
    }

    companion object {
        private val WEEKDAYS = listOf(
            "יום ראשון", "יום שני", "יום שלישי", "יום רביעי",
            "יום חמישי", "יום שישי", "שבת",
        )
        private val ORDINALS = listOf(
            "ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שביעי", "שמיני",
        )
    }
}
