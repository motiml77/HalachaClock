package com.zmanimclock.app.feature.zmanim.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.alarms.data.AlarmDao
import com.zmanimclock.app.feature.alarms.data.AlarmType
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.feature.zmanim.data.ZmanimRepository
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.feature.zmanim.model.instantOf
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

            val timed = ZmanKind.entries
                .mapNotNull { kind -> day.instantOf(kind)?.let { kind to it } }
                .sortedBy { (_, instant) -> instant }
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
            )
        }
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
