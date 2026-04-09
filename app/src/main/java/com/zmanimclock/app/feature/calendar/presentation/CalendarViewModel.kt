package com.zmanimclock.app.feature.calendar.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.chaitables.data.ChaiTablesRepository
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.feature.zmanim.data.ZmanimCalculator
import com.zmanimclock.app.feature.zmanim.data.model.DayZmanim
import com.zmanimclock.app.feature.zmanim.data.model.ZmanCategory
import com.zmanimclock.app.feature.zmanim.data.model.ZmanId
import com.zmanimclock.app.feature.zmanim.data.model.ZmanTime
import com.zmanimclock.app.location.model.AppGeoLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/**
 * Represents a single day cell in the calendar grid.
 */
data class CalendarDay(
    val gregorianDay: Int,           // day of month (1-31)
    val gregorianMonth: Int,         // 0-based month
    val gregorianYear: Int,
    val hebrewDay: Int,              // Hebrew day of month
    val hebrewMonth: String,         // Hebrew month name
    val hebrewYear: Int,
    val hebrewDayFormatted: String,  // e.g. "י״א"
    val holiday: String? = null,     // Holiday name or null
    val isToday: Boolean = false,
    val isSelected: Boolean = false,
    val isCurrentMonth: Boolean = true,
)

/**
 * Calendar display mode - Hebrew or Gregorian primary
 */
enum class CalendarMode {
    HEBREW,    // Hebrew dates primary, Gregorian secondary
    GREGORIAN, // Gregorian dates primary, Hebrew secondary
}

data class CalendarUiState(
    // Current view month
    val displayYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val displayMonth: Int = Calendar.getInstance().get(Calendar.MONTH), // 0-based
    val displayMonthTitle: String = "",

    // Calendar grid
    val days: List<CalendarDay> = emptyList(),
    val dayOfWeekHeaders: List<String> = listOf("א'", "ב'", "ג'", "ד'", "ה'", "ו'", "ש'"),

    // Display mode
    val calendarMode: CalendarMode = CalendarMode.HEBREW,

    // Selected day details
    val selectedDay: CalendarDay? = null,
    val selectedDayZmanim: List<ZmanTime> = emptyList(),
    val selectedDayZmanimByCategory: Map<ZmanCategory, List<ZmanTime>> = emptyMap(),
    val selectedDayHebrewDate: String = "",
    val selectedDayGregorianDate: String = "",
    val selectedDayHoliday: String? = null,
    val selectedDayShaahGra: String = "",
    val selectedDayShaahMga: String = "",
    val showDayDetail: Boolean = false,

    // Location
    val locationName: String = "",
    val locationTimeZone: TimeZone = TimeZone.getDefault(),
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val zmanimCalculator: ZmanimCalculator,
    private val prefsRepository: UserPreferencesRepository,
    private val chaiTablesRepository: ChaiTablesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val hebrewFormatter = HebrewDateFormatter().apply {
        isUseGershGershayim = true
        isHebrewFormat = true
    }

    private val enabledZmanim: Set<ZmanId> = ZmanId.entries.filter { it.defaultEnabled }.toSet()

    private var currentLocation: AppGeoLocation? = null

    init {
        viewModelScope.launch {
            val prefs = prefsRepository.preferences.first()
            currentLocation = prefsRepository.prefsToGeoLocation(prefs)
            _uiState.update {
                it.copy(
                    locationName = prefs.cityNameHebrew,
                    locationTimeZone = TimeZone.getTimeZone(prefs.timeZoneId),
                )
            }
            buildCalendarGrid()
        }
    }

    fun toggleCalendarMode() {
        _uiState.update {
            it.copy(
                calendarMode = if (it.calendarMode == CalendarMode.HEBREW)
                    CalendarMode.GREGORIAN else CalendarMode.HEBREW
            )
        }
        buildCalendarGrid()
    }

    fun navigateMonth(delta: Int) {
        _uiState.update {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, it.displayYear)
            cal.set(Calendar.MONTH, it.displayMonth)
            cal.add(Calendar.MONTH, delta)
            it.copy(
                displayYear = cal.get(Calendar.YEAR),
                displayMonth = cal.get(Calendar.MONTH),
                showDayDetail = false,
            )
        }
        buildCalendarGrid()
    }

    fun selectDay(day: CalendarDay) {
        _uiState.update { it.copy(selectedDay = day) }
        loadDayZmanim(day)
    }

    fun dismissDayDetail() {
        _uiState.update { it.copy(showDayDetail = false) }
    }

    private fun buildCalendarGrid() {
        val state = _uiState.value
        val year = state.displayYear
        val month = state.displayMonth

        val today = Calendar.getInstance()
        val todayYear = today.get(Calendar.YEAR)
        val todayMonth = today.get(Calendar.MONTH)
        val todayDay = today.get(Calendar.DAY_OF_MONTH)

        val cal = Calendar.getInstance()
        cal.set(year, month, 1)

        // Find the day of week for the 1st (Sunday=1 in Calendar, we want Sunday=0)
        var firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Build title
        val monthTitle = if (state.calendarMode == CalendarMode.HEBREW) {
            val jewishCal = JewishCalendar(cal)
            val hebrewMonthName = hebrewFormatter.formatMonth(jewishCal)
            val hebrewYear = hebrewFormatter.formatHebrewNumber(jewishCal.getJewishYear())
            "$hebrewMonthName $hebrewYear"
        } else {
            val monthNames = arrayOf(
                "ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני",
                "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר"
            )
            "${monthNames[month]} $year"
        }

        // Build day cells
        val days = mutableListOf<CalendarDay>()

        // Previous month filler days
        if (firstDayOfWeek > 0) {
            val prevCal = Calendar.getInstance()
            prevCal.set(year, month, 1)
            prevCal.add(Calendar.DAY_OF_MONTH, -firstDayOfWeek)
            for (i in 0 until firstDayOfWeek) {
                days.add(createCalendarDay(prevCal, todayYear, todayMonth, todayDay, isCurrentMonth = false))
                prevCal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        // Current month days
        cal.set(year, month, 1)
        for (day in 1..daysInMonth) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            days.add(createCalendarDay(cal, todayYear, todayMonth, todayDay, isCurrentMonth = true))
        }

        // Next month filler days to complete the grid (always 6 rows = 42 cells)
        val remaining = 42 - days.size
        val nextCal = Calendar.getInstance()
        nextCal.set(year, month, daysInMonth)
        nextCal.add(Calendar.DAY_OF_MONTH, 1)
        for (i in 0 until remaining) {
            days.add(createCalendarDay(nextCal, todayYear, todayMonth, todayDay, isCurrentMonth = false))
            nextCal.add(Calendar.DAY_OF_MONTH, 1)
        }

        _uiState.update {
            it.copy(
                displayMonthTitle = monthTitle,
                days = days,
            )
        }
    }

    private fun createCalendarDay(
        cal: Calendar,
        todayYear: Int,
        todayMonth: Int,
        todayDay: Int,
        isCurrentMonth: Boolean,
    ): CalendarDay {
        val jewishCal = JewishCalendar(cal)
        val hebrewDayStr = hebrewFormatter.formatHebrewNumber(jewishCal.getJewishDayOfMonth())
        val hebrewMonthStr = hebrewFormatter.formatMonth(jewishCal)
        val holiday = try {
            val yomTovIndex = jewishCal.yomTovIndex
            if (yomTovIndex > 0) hebrewFormatter.formatYomTov(jewishCal) else null
        } catch (e: Exception) {
            null
        }

        val gYear = cal.get(Calendar.YEAR)
        val gMonth = cal.get(Calendar.MONTH)
        val gDay = cal.get(Calendar.DAY_OF_MONTH)

        return CalendarDay(
            gregorianDay = gDay,
            gregorianMonth = gMonth,
            gregorianYear = gYear,
            hebrewDay = jewishCal.getJewishDayOfMonth(),
            hebrewMonth = hebrewMonthStr,
            hebrewYear = jewishCal.getJewishYear(),
            hebrewDayFormatted = hebrewDayStr,
            holiday = holiday,
            isToday = gYear == todayYear && gMonth == todayMonth && gDay == todayDay,
            isCurrentMonth = isCurrentMonth,
        )
    }

    private fun loadDayZmanim(day: CalendarDay) {
        viewModelScope.launch {
            val location = currentLocation ?: return@launch
            val prefs = prefsRepository.preferences.first()

            val cal = Calendar.getInstance(location.timeZone)
            cal.set(day.gregorianYear, day.gregorianMonth, day.gregorianDay, 12, 0, 0)

            try {
                // Fetch visible sunrise from ChaiTables
                val cityId = if (prefs.useGps) null else prefs.cityId
                val jewishCal = JewishCalendar(cal)
                val visibleSunrise = try {
                    chaiTablesRepository.getVisibleSunrise(
                        location = location,
                        cityId = cityId,
                        hebrewYear = jewishCal.getJewishYear(),
                        hebrewMonth = jewishCal.getJewishMonth(),
                        hebrewDay = jewishCal.getJewishDayOfMonth(),
                    )
                } catch (e: Exception) {
                    null
                }

                val dayZmanim = zmanimCalculator.calculateZmanim(
                    location = location,
                    date = cal,
                    useElevation = prefs.useElevation,
                    candleLightingOffset = prefs.candleLightingMinutes.toDouble(),
                    visibleSunrise = visibleSunrise,
                )

                val filteredZmanim = dayZmanim.zmanim.filter { it.id in enabledZmanim }
                val grouped = filteredZmanim.groupBy { it.id.category }
                val holiday = zmanimCalculator.getHebrewHoliday(cal)

                val hebrewDate = hebrewFormatter.format(jewishCal as JewishDate)

                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                    timeZone = location.timeZone
                }

                _uiState.update {
                    it.copy(
                        selectedDayZmanim = filteredZmanim,
                        selectedDayZmanimByCategory = grouped,
                        selectedDayHebrewDate = hebrewDate,
                        selectedDayGregorianDate = dateFormat.format(cal.time),
                        selectedDayHoliday = holiday,
                        selectedDayShaahGra = formatDuration(dayZmanim.shaahZmanisGra),
                        selectedDayShaahMga = formatDuration(dayZmanim.shaahZmanisMga),
                        showDayDetail = true,
                    )
                }
            } catch (e: Exception) {
                // Silently fail, user can try another day
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        if (millis == Long.MIN_VALUE || millis == 0L) return "--"
        val totalMinutes = millis / 60000
        val seconds = (millis % 60000) / 1000
        return "${totalMinutes}:${String.format("%02d", seconds)}"
    }
}
