package com.zmanimclock.app.feature.zmanim.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.feature.zmanim.data.ZmanimRepository
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.feature.zmanim.model.instantOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
) : ViewModel() {

    data class ZmanRow(
        val kind: ZmanKind,
        val name: String,
        val time: String,
        val isNext: Boolean,
    )

    data class UiState(
        val loading: Boolean = true,
        val locationName: String = "",
        val hebrewDate: String = "",
        val basedOnVisibleSunrise: Boolean = false,
        val rows: List<ZmanRow> = emptyList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val hebrewFormatter = HebrewDateFormatter().apply {
        isHebrewFormat = true
        isUseGershGershayim = true
    }

    init {
        refresh()
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
            val nextKind = timed.firstOrNull { (_, instant) -> instant.isAfter(now) }?.first

            _uiState.value = UiState(
                loading = false,
                locationName = prefs.cityNameHebrew,
                hebrewDate = hebrewDate(today, zone),
                basedOnVisibleSunrise = day.basedOnVisibleSunrise,
                rows = timed.map { (kind, instant) ->
                    ZmanRow(
                        kind = kind,
                        name = kind.hebrewName,
                        time = timeFormat.format(instant.atZone(zone)),
                        isNext = kind == nextKind,
                    )
                },
            )
        }
    }

    private fun hebrewDate(date: LocalDate, zone: ZoneId): String {
        val cal = GregorianCalendar.from(date.atStartOfDay(zone))
        return hebrewFormatter.format(JewishDate(cal))
    }
}
