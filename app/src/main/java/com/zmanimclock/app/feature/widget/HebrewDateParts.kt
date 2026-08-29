package com.zmanimclock.app.feature.widget

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.calendar.presentation.CalendarViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.util.GregorianCalendar

/**
 * The Hebrew date broken into the four lines the date-only widget draws
 * separately (weekday, day, month, year) — as opposed to [WidgetRenderer]'s
 * `hebrewDate()`, which formats them as one combined string for the full
 * zmanim widget.
 */
data class HebrewDateParts(
    val weekday: String,
    val day: String,
    val month: String,
    val year: String,
)

/**
 * Pure and Android-free (KosherJava's date classes carry no Android
 * dependency), so this is unit-testable on the plain JVM without a device —
 * see HebrewDatePartsTest for the values this is pinned to.
 */
fun hebrewDateParts(date: LocalDate, zone: ZoneId, formatter: HebrewDateFormatter): HebrewDateParts {
    val jd = JewishDate(GregorianCalendar.from(date.atStartOfDay(zone)))
    return HebrewDateParts(
        weekday = CalendarViewModel.hebrewWeekday(date),
        day = formatter.formatHebrewNumber(jd.jewishDayOfMonth),
        month = formatter.formatMonth(jd),
        year = formatter.formatHebrewNumber(jd.jewishYear),
    )
}
