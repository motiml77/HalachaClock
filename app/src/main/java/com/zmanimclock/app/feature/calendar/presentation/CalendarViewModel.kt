package com.zmanimclock.app.feature.calendar.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zmanimclock.app.feature.alarms.data.AlarmDao
import com.zmanimclock.app.feature.alarms.data.AlarmType
import com.zmanimclock.app.feature.calendar.model.CalendarDayMeta
import com.zmanimclock.app.feature.calendar.model.HebrewMonthSequence
import com.zmanimclock.app.feature.calendar.model.MonthGrid
import com.zmanimclock.app.feature.calendar.model.MonthGridBuilder
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.feature.zmanim.data.ZmanimRepository
import com.zmanimclock.app.feature.zmanim.format.asZmanTime
import com.zmanimclock.app.feature.zmanim.format.asZmanTimeOrNull
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.feature.zmanim.model.nextRelevantZman
import com.zmanimclock.app.feature.zmanim.model.relevantTimedZmanim
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * The calendar tab.
 *
 * Two very different costs live here, and keeping them apart is the whole
 * performance story:
 *  - The GRID is pure Hebrew-calendar arithmetic. No astronomy, no database,
 *    no network, no location. A month is built in microseconds and cached.
 *  - Only the SELECTED DAY loads zmanim, and it does so with
 *    `cacheOnly = true` — see [loadDay].
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
    private val zmanimRepository: ZmanimRepository,
    private val savedState: SavedStateHandle,
    alarmDao: AlarmDao,
) : ViewModel() {

    data class ZmanRow(
        val kind: ZmanKind,
        val name: String,
        val time: String,
        val isNext: Boolean,
        val isPast: Boolean,
    )

    data class DayDetail(
        val date: LocalDate = LocalDate.now(),
        val isToday: Boolean = true,
        val hebrewDate: String = "",
        val gregorianDate: String = "",
        val weekdayName: String = "",
        val locationName: String = "",
        val basedOnVisibleSunrise: Boolean = false,
        /** "הזמן הבא — שקיעה" on today, "הדלקת נרות" on an erev Shabbat, … */
        val headlineLabel: String? = null,
        val headlineTime: String? = null,
        val rows: List<ZmanRow> = emptyList(),
        val notes: List<String> = emptyList(),
        val loading: Boolean = true,
        /** The engine returned nothing usable — polar day/night, or no data. */
        val undefined: Boolean = false,
    )

    data class UiState(
        val today: LocalDate = LocalDate.now(),
        val selectedDate: LocalDate = LocalDate.now(),
        val visibleMonthIndex: Int = HebrewMonthSequence.indexOf(LocalDate.now()),
    )

    /** Zman kinds with at least one active alarm — drives the bell markers. */
    val alertedKinds: StateFlow<Set<String>> =
        alarmDao.getActiveAlarms()
            .map { list -> list.filter { it.type == AlarmType.ZMAN }.map { it.zmanId }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val selected = MutableStateFlow(
        savedState.get<Long>(KEY_SELECTED)?.let(LocalDate::ofEpochDay) ?: LocalDate.now(),
    )

    private val _uiState = MutableStateFlow(
        UiState(
            selectedDate = selected.value,
            visibleMonthIndex = HebrewMonthSequence.clamp(
                savedState.get<Int>(KEY_MONTH) ?: HebrewMonthSequence.indexOf(selected.value),
            ),
        ),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * The selected day's zmanim.
     *
     * `debounce` keeps a held-down arrow or a fast swipe from firing a load per
     * intermediate day, and `mapLatest` cancels a load the user has already
     * moved past. Preferences are combined in so a city change reloads.
     */
    val dayDetail: StateFlow<DayDetail> =
        combine(selected.debounce(DEBOUNCE_MS), prefsRepository.preferences) { date, _ -> date }
            .mapLatest { date -> withContext(Dispatchers.Default) { loadDay(date) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayDetail())

    // ------------------------------------------------------------- grid

    private val gridCache = object : LinkedHashMap<Int, MonthGrid>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, MonthGrid>) = size > 24
    }

    /** The month at [index]. Cheap and cached; safe to call from composition. */
    fun monthGrid(index: Int): MonthGrid = synchronized(gridCache) {
        gridCache.getOrPut(index) { MonthGridBuilder.build(HebrewMonthSequence.refAt(index)) }
    }

    // ---------------------------------------------------------- actions

    fun select(date: LocalDate) {
        selected.value = date
        savedState[KEY_SELECTED] = date.toEpochDay()
        val month = HebrewMonthSequence.indexOf(date)
        _uiState.value = _uiState.value.copy(
            selectedDate = date,
            visibleMonthIndex = if (month >= 0) month else _uiState.value.visibleMonthIndex,
        )
        if (month >= 0) savedState[KEY_MONTH] = month
    }

    /** Called when the pager settles, so the header follows the visible page. */
    fun onMonthVisible(index: Int) {
        val clamped = HebrewMonthSequence.clamp(index)
        if (clamped != _uiState.value.visibleMonthIndex) {
            _uiState.value = _uiState.value.copy(visibleMonthIndex = clamped)
            savedState[KEY_MONTH] = clamped
        }
    }

    fun goToToday() = select(LocalDate.now())

    fun shiftSelectedDay(days: Long) = select(_uiState.value.selectedDate.plusDays(days))

    /** Re-reads the wall clock — the "today" ring must move at midnight. */
    fun onScreenResumed() {
        val now = LocalDate.now()
        if (now != _uiState.value.today) {
            _uiState.value = _uiState.value.copy(today = now)
        }
    }

    // ----------------------------------------------------------- loading

    private suspend fun loadDay(date: LocalDate): DayDetail {
        val prefs = prefsRepository.preferences.first()
        val location = prefsRepository.prefsToGeoLocation(prefs)
        val cityId = if (prefs.useGps) null else prefs.cityId
        val zone = ZoneId.of(prefs.timeZoneId)
        val today = LocalDate.now(zone)
        val isToday = date == today

        // cacheOnly: swiping through months must never trigger the ChaiTables
        // fetcher, which scrapes a whole year's table synchronously (30s
        // timeouts, and abroad up to nineteen sequential requests). Missing
        // data falls back to the mishor sunrise and is flagged in the UI.
        val day = zmanimRepository.getDayZmanim(
            location = location,
            cityId = cityId,
            date = date,
            candleLightingOffsetMinutes = prefs.candleLightingMinutes.toLong(),
            tzeitShabbatMinutes = prefs.tzeitShabbatMinutes.toLong(),
            cacheOnly = true,
        )
        val meta = MonthGridBuilder.metaFor(date)
        val timed = day.relevantTimedZmanim(date)
        val now = Instant.now()

        val next = if (isToday) {
            val yesterday = zmanimRepository.getDayZmanim(
                location = location,
                cityId = cityId,
                date = date.minusDays(1),
                candleLightingOffsetMinutes = prefs.candleLightingMinutes.toLong(),
                tzeitShabbatMinutes = prefs.tzeitShabbatMinutes.toLong(),
                cacheOnly = true,
            )
            nextRelevantZman(day, date, now, yesterday, prefs.nextZmanFilter)
        } else {
            null
        }

        val headline = headlineFor(isToday, next, timed, meta, zone)

        return DayDetail(
            date = date,
            isToday = isToday,
            hebrewDate = hebrewDateOf(meta),
            gregorianDate = GREGORIAN.format(date),
            weekdayName = hebrewWeekday(date),
            locationName = prefs.cityNameHebrew,
            basedOnVisibleSunrise = day.basedOnVisibleSunrise,
            headlineLabel = headline?.first,
            headlineTime = headline?.second,
            rows = timed.map { (kind, instant) ->
                ZmanRow(
                    kind = kind,
                    name = kind.hebrewName,
                    time = instant.asZmanTime(zone),
                    // Highlighting only makes sense for today. On another day
                    // every row would read as "not past yet", i.e. "all of
                    // these are coming up shortly" — actively misleading.
                    isNext = isToday && kind == next?.first,
                    isPast = isToday && instant.isBefore(now),
                )
            },
            notes = notesFor(meta),
            loading = false,
            undefined = timed.isEmpty(),
        )
    }

    /** Big number in the hero: the next zman today, the day's headline otherwise. */
    private fun headlineFor(
        isToday: Boolean,
        next: Pair<ZmanKind, Instant>?,
        timed: List<Pair<ZmanKind, Instant>>,
        meta: CalendarDayMeta,
        zone: ZoneId,
    ): Pair<String, String>? {
        fun timeOf(kind: ZmanKind) =
            timed.firstOrNull { it.first == kind }?.second?.asZmanTimeOrNull(zone)

        if (isToday) {
            next?.let { (kind, instant) ->
                return "הזמן הבא — ${kind.hebrewName}" to instant.asZmanTime(zone)
            }
        }
        // "The next zman" is meaningless three weeks out, so a non-today day
        // shows the datum that actually defines it.
        timeOf(ZmanKind.CANDLE_LIGHTING)?.let { return "הדלקת נרות" to it }
        timeOf(ZmanKind.TZEIT_SHABBAT)?.let { return "צאת שבת" to it }
        meta.fast?.let { fast ->
            timeOf(ZmanKind.TZEIT_LECHUMRA)?.let { return "סיום ${fast.name}" to it }
        }
        timeOf(ZmanKind.HANETZ)?.let { return "הנץ החמה" to it }
        return null
    }

    /** The lines under the hero: parsha, holiday, omer, chanukah. */
    private fun notesFor(meta: CalendarDayMeta): List<String> = buildList {
        meta.parshaName?.let { add("פרשת $it") }
        meta.specialShabbatName?.let { add("שבת $it") }
        meta.yomTovName?.let { add(it) }
        meta.fast?.let { add(it.name) }
        meta.dayOfChanukah?.let { add("נר ${hebrewOrdinal(it)} של חנוכה") }
        // Neutral wording on purpose: the omer is counted at night, so the
        // count "belonging" to a Hebrew day is announced the evening before.
        // The app is Gregorian-day anchored, so it states the number without
        // claiming which night it is said.
        meta.omerDay?.let { add("העומר: $it") }
    }

    private fun hebrewDateOf(meta: CalendarDayMeta): String {
        val grid = monthGridFor(meta)
        return "${meta.hebrewDayLabel} ${grid.hebrewMonthLabel} ${grid.hebrewYearLabel}"
    }

    private fun monthGridFor(meta: CalendarDayMeta): MonthGrid {
        val idx = HebrewMonthSequence.indexOf(meta.date)
        return monthGrid(HebrewMonthSequence.clamp(idx))
    }

    companion object {
        private const val DEBOUNCE_MS = 120L
        private const val KEY_SELECTED = "calendar_selected_epoch_day"
        private const val KEY_MONTH = "calendar_month_index"
        private val GREGORIAN: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M.yyyy")

        private val WEEKDAYS = listOf(
            "יום ראשון", "יום שני", "יום שלישי", "יום רביעי",
            "יום חמישי", "יום שישי", "שבת",
        )

        fun hebrewWeekday(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value % 7]

        private val ORDINALS = listOf(
            "ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שביעי", "שמיני",
        )

        fun hebrewOrdinal(n: Int): String = ORDINALS.getOrElse(n - 1) { n.toString() }
    }
}
