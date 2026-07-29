package com.zmanimclock.app.feature.zmanim.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.alarms.data.AlarmDao
import com.zmanimclock.app.feature.alarms.data.AlarmType
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.feature.zmanim.data.ZmanimRepository
import com.zmanimclock.app.feature.zmanim.model.FastDays
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.feature.zmanim.model.relevantTimedZmanim
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.GregorianCalendar
import javax.inject.Inject

/**
 * Loads the day's zmanim (visible-netz-based) for the configured location.
 * UI-agnostic state so the Claude Design screens can bind 1:1 later.
 */
@HiltViewModel
class ZmanimViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
    private val zmanimRepository: ZmanimRepository,
    alarmDao: AlarmDao,
) : ViewModel() {

    /** Zman kinds that currently have at least one ACTIVE alarm (for the bell markers). */
    val alertedKinds: StateFlow<Set<String>> =
        alarmDao.getActiveAlarms()
            .map { alarms ->
                alarms.filter { it.type == AlarmType.ZMAN }.map { it.zmanId }.toSet()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    data class ZmanRow(
        val kind: ZmanKind,
        val name: String,
        val time: String,
        val isNext: Boolean,
        val isPast: Boolean,
    )

    /** Bottom banner on fast days / erev a 25-hour fast (start & end times). */
    data class FastBanner(val title: String, val line: String)

    data class UiState(
        val loading: Boolean = true,
        val locationName: String = "",
        val hebrewDate: String = "",
        val gregorianDate: String = "",
        val basedOnVisibleSunrise: Boolean = false,
        /** Hero: the next zman of the day (null when the day is done). */
        val nextName: String? = null,
        val nextTime: String? = null,
        /** Short countdown, e.g. "1:06". */
        val countdown: String? = null,
        val rows: List<ZmanRow> = emptyList(),
        val fastBanner: FastBanner? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val hebrewFormatter = HebrewDateFormatter().apply {
        isHebrewFormat = true
        isUseGershGershayim = true
    }

    init {
        // Recompute whenever preferences change (city switch, candle offset…)
        viewModelScope.launch {
            prefsRepository.preferences.collect { refresh() }
        }
        // …and every minute, so the countdown, the next-zman highlight and the
        // date never freeze for the lifetime of the screen.
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val prefs = prefsRepository.preferences.first()
            val location = prefsRepository.prefsToGeoLocation(prefs)
            val cityId = if (prefs.useGps) null else prefs.cityId
            val zone = ZoneId.of(prefs.timeZoneId)
            val today = LocalDate.now(zone)

            val day = zmanimRepository.getDayZmanim(
                location = location,
                cityId = cityId,
                date = today,
                candleLightingOffsetMinutes = prefs.candleLightingMinutes.toLong(),
            )

            val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
            val now = Instant.now()

            val timed = day.relevantTimedZmanim(today)
            val next = timed.firstOrNull { (_, instant) -> instant.isAfter(now) }

            _uiState.value = UiState(
                loading = false,
                locationName = prefs.cityNameHebrew,
                hebrewDate = hebrewDate(today, zone),
                gregorianDate = DateTimeFormatter.ofPattern("d.M.yyyy").format(today),
                basedOnVisibleSunrise = day.basedOnVisibleSunrise,
                nextName = next?.first?.hebrewName,
                nextTime = next?.second?.let { timeFormat.format(it.atZone(zone)) },
                countdown = next?.second?.let { formatCountdown(now, it) },
                rows = timed.map { (kind, instant) ->
                    ZmanRow(
                        kind = kind,
                        name = kind.hebrewName,
                        time = timeFormat.format(instant.atZone(zone)),
                        isNext = kind == next?.first,
                        isPast = instant.isBefore(now),
                    )
                },
                fastBanner = fastBanner(today, zone, day, timeFormat),
            )
        }
    }

    /**
     * The bottom fast banner. Times are the day's own zmanim, so they stay
     * correct for every year and every city:
     *  - a minor fast today: "מעלות השחר X עד צאת הכוכבים Y (לחומרא Z)"
     *  - a 25-hour fast today: exit time only (entry was yesterday's sunset)
     *  - erev a 25-hour fast: "הצום מתחיל הערב בשקיעה X"
     */
    private fun fastBanner(
        today: LocalDate,
        zone: ZoneId,
        day: com.zmanimclock.app.feature.zmanim.engine.DayZmanim,
        fmt: DateTimeFormatter,
    ): FastBanner? {
        fun f(i: Instant?): String? = i?.let { fmt.format(it.atZone(zone)) }

        val fastToday = FastDays.fastOn(today, zone)
        if (fastToday != null) {
            // Fast exit is ALWAYS the default tzeit (6.2° — three medium
            // stars), per the user's ruling; Yom Kippur ends like Shabbat.
            val end = if (fastToday.endsLikeShabbat) day.tzeitShabbat else day.tzeitLechumra
            val endText = f(end) ?: return null
            val line = buildString {
                if (fastToday.startsEveningBefore) {
                    append("הצום יוצא ב־$endText")
                } else {
                    val start = f(day.alotHashachar)
                    if (start != null) append("מעלות השחר $start ")
                    append("עד צאת הכוכבים $endText")
                }
            }
            return FastBanner(title = "היום: ${fastToday.name}", line = line)
        }

        val fastTomorrow = FastDays.fastOn(today.plusDays(1), zone)
        if (fastTomorrow?.startsEveningBefore == true) {
            val shkia = f(day.shkia) ?: return null
            return FastBanner(
                title = "הערב: ${fastTomorrow.name}",
                line = "הצום מתחיל הערב בשקיעה $shkia",
            )
        }
        return null
    }

    private fun hebrewDate(date: LocalDate, zone: ZoneId): String {
        val cal = GregorianCalendar.from(date.atStartOfDay(zone))
        return hebrewFormatter.format(JewishDate(cal))
    }

    /** Short "1:06" / "0:42" countdown (README §7.1). */
    private fun formatCountdown(now: Instant, target: Instant): String {
        val minutes = java.time.Duration.between(now, target).toMinutes()
        return "%d:%02d".format(minutes / 60, minutes % 60)
    }
}
