package com.zmanimclock.app.feature.calendar.model

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import java.time.LocalDate

/**
 * One Hebrew month, identified the way KosherJava identifies it.
 *
 * [month] uses KosherJava's numbering: NISSAN = 1 … ADAR = 12, ADAR_II = 13,
 * TISHREI = 7. There is deliberately no separate "Adar I" number — in a leap
 * year, month 12 IS Adar I and month 13 is Adar II; in a common year month 12
 * is plain Adar and month 13 does not exist (constructing it throws).
 */
data class HebrewMonthRef(val year: Int, val month: Int) {

    /** Whether [year] is a leap year — i.e. whether Adar II exists in it. */
    val isLeapYear: Boolean get() = JewishDate(year, TISHREI, 1).isJewishLeapYear

    /** True only in a leap year's month 12. */
    val isAdarI: Boolean get() = month == ADAR && isLeapYear

    /** True only in a leap year's month 13. */
    val isAdarII: Boolean get() = month == ADAR_II

    /** 29 or 30. Never assume — Cheshvan and Kislev vary year to year. */
    val daysInMonth: Int get() = JewishDate(year, month, 1).daysInJewishMonth

    /** The Gregorian date of this month's 1st. */
    val firstDay: LocalDate get() = JewishDate(year, month, 1).toLocalDate()

    /** The Gregorian date of this month's last day. */
    val lastDay: LocalDate get() = JewishDate(year, month, daysInMonth).toLocalDate()

    companion object {
        const val NISSAN = 1
        const val TISHREI = 7
        const val ADAR = 12
        const val ADAR_II = 13

        /** The month containing [date]. */
        fun containing(date: LocalDate): HebrewMonthRef {
            val jd = JewishDate(date.toGregorianCalendar())
            return HebrewMonthRef(jd.jewishYear, jd.jewishMonth)
        }
    }
}

/**
 * KosherJava reports the Gregorian month 0-based (java.util.Calendar
 * convention) while [LocalDate] is 1-based. Getting this wrong shifts every
 * date by a month, so it is converted in exactly one place.
 */
internal fun JewishDate.toLocalDate(): LocalDate =
    LocalDate.of(gregorianYear, gregorianMonth + 1, gregorianDayOfMonth)

/**
 * A [LocalDate] as the calendar day itself, with no time zone involved.
 *
 * The Hebrew calendar grid is location-independent: which Hebrew date a
 * Gregorian date maps to is the same everywhere, so noon is used purely to
 * stay clear of any DST midnight edge in the underlying GregorianCalendar.
 */
internal fun LocalDate.toGregorianCalendar(): java.util.GregorianCalendar =
    java.util.GregorianCalendar(year, monthValue - 1, dayOfMonth, 12, 0, 0)
