package com.zmanimclock.app.feature.womensarea.model

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.calendar.model.toGregorianCalendar
import java.time.DayOfWeek
import java.time.LocalDate

/** Every Hebrew string the Women's Area shows for a day, a kind or an onah — in one place. */
object WomensAreaLabels {

    val VesetKind.hebrewName: String
        get() = when (this) {
            VesetKind.ONAH_BEINONIT -> "עונה בינונית"
            VesetKind.HAFLAGA -> "הפלגה"
            VesetKind.YOM_HACHODESH -> "יום החודש"
        }

    /** "ביום" / "בלילה". */
    val Onah.hebrewName: String
        get() = when (this) {
            Onah.DAY -> "ביום"
            Onah.NIGHT -> "בלילה"
        }

    private fun formatter() = HebrewDateFormatter().apply {
        isHebrewFormat = true
        isUseGershGershayim = true
    }

    private fun jewishDateOf(date: LocalDate) = JewishDate(date.toGregorianCalendar())

    /** "ט״ו ניסן תשפ״ו". */
    fun hebrewDate(date: LocalDate): String {
        val fmt = formatter()
        val jd = jewishDateOf(date)
        return "${fmt.formatHebrewNumber(jd.jewishDayOfMonth)} ${fmt.formatMonth(jd)} ${fmt.formatHebrewNumber(jd.jewishYear)}"
    }

    /** "ט״ו ניסן" — no year, for tight spots. */
    fun hebrewDayAndMonth(date: LocalDate): String {
        val fmt = formatter()
        val jd = jewishDateOf(date)
        return "${fmt.formatHebrewNumber(jd.jewishDayOfMonth)} ${fmt.formatMonth(jd)}"
    }

    /** "שלישי" — the weekday of the Hebrew day itself (its daytime). */
    fun weekdayName(date: LocalDate): String = weekdayName(date.dayOfWeek)

    private fun weekdayName(day: DayOfWeek): String = when (day) {
        DayOfWeek.SUNDAY -> "ראשון"
        DayOfWeek.MONDAY -> "שני"
        DayOfWeek.TUESDAY -> "שלישי"
        DayOfWeek.WEDNESDAY -> "רביעי"
        DayOfWeek.THURSDAY -> "חמישי"
        DayOfWeek.FRIDAY -> "שישי"
        DayOfWeek.SATURDAY -> "שבת"
    }

    /** "2.4" — the civil date, secondary everywhere. */
    fun gregorianShort(date: LocalDate): String = "${date.dayOfMonth}.${date.monthValue}"

    /**
     * When an onah of the Hebrew day [date] actually falls, in civil terms.
     *
     * DAY: "יום חמישי ט״ו ניסן (2.4)".
     * NIGHT: "ליל חמישי ט״ו ניסן — הערב של יום רביעי 1.4, אחרי השקיעה" — the
     * night belongs to the Hebrew day that FOLLOWS it, so its civil evening is
     * the day before [date].
     */
    fun onahTiming(date: LocalDate, onah: Onah): String = when (onah) {
        Onah.DAY -> "יום ${weekdayName(date)} ${hebrewDayAndMonth(date)} (${gregorianShort(date)})"
        Onah.NIGHT -> {
            val evening = date.minusDays(1)
            "ליל ${weekdayName(date)} ${hebrewDayAndMonth(date)} — " +
                "הערב של ${eveningDay(evening)} ${gregorianShort(evening)}, אחרי השקיעה"
        }
    }

    /** "יום שלישי ט״ו ניסן (2.4), לפני השקיעה" — when the הפסק טהרה was made. */
    fun hefsekTiming(date: LocalDate): String =
        "יום ${weekdayName(date)} ${hebrewDayAndMonth(date)} (${gregorianShort(date)}), לפני השקיעה"

    /**
     * "ליל רביעי כ״ג ניסן — הערב של יום שלישי 9.4, אחרי צאת הכוכבים" — the
     * tevila night is the evening before the Hebrew day [date], after tzeit.
     */
    fun tevilaTiming(date: LocalDate): String {
        val evening = date.minusDays(1)
        return "ליל ${weekdayName(date)} ${hebrewDayAndMonth(date)} — " +
            "הערב של ${eveningDay(evening)} ${gregorianShort(evening)}, אחרי צאת הכוכבים"
    }

    /** "יום רביעי" / "שבת" — "יום שבת" reads wrongly. */
    private fun eveningDay(date: LocalDate): String =
        if (date.dayOfWeek == DayOfWeek.SATURDAY) "שבת" else "יום ${weekdayName(date)}"
}
