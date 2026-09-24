package com.zmanimclock.app.feature.womensarea.model

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.calendar.model.HebrewMonthRef
import com.zmanimclock.app.feature.calendar.model.HebrewMonthSequence
import com.zmanimclock.app.feature.calendar.model.toGregorianCalendar
import com.zmanimclock.app.feature.calendar.model.toLocalDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Which of the three predicted dates a calendar marker represents. */
enum class VesetKind { ONAH_BEINONIT, HAFLAGA, YOM_HACHODESH }

/**
 * The three predicted dates from one period-start entry. Pure calculation —
 * carries no halachic weight or interpretation, just the dates themselves.
 */
data class VesetPrediction(
    val sourceStart: LocalDate,
    val onahBeinonit: LocalDate,
    /** Null when [sourceStart] has no earlier period-start entry to measure a gap against. */
    val haflaga: LocalDate?,
    /** Null only past [HebrewMonthSequence]'s supported range — a dead case in practice. */
    val yomHachodesh: LocalDate?,
)

/**
 * Sefirat/veset date arithmetic for the Women's Area — deliberately nothing
 * more than that. Every function here is a plain date calculation with no
 * halachic ruling attached: this object answers "what date is 30 days after
 * X" and "what date is 7 days after Y", never "is she permitted" or "does
 * this veset apply". That judgment stays with the user and her own rabbi.
 */
object WomensAreaCalculator {

    private const val ONAH_BEINONIT_DAYS = 30L
    const val CLEAN_DAYS_COUNT = 7

    /**
     * עונה בינונית: 30 days after [start]. A day is the same length in both
     * calendars — the Hebrew/Gregorian mapping is a strictly increasing
     * bijection — so plain [LocalDate] arithmetic is exact; no Hebrew-calendar
     * round-trip is needed here.
     */
    fun onahBeinonit(start: LocalDate): LocalDate = start.plusDays(ONAH_BEINONIT_DAYS)

    /** הפלגה: [start] plus the gap between [start] and [previousStart]. */
    fun haflaga(start: LocalDate, previousStart: LocalDate?): LocalDate? {
        val previous = previousStart ?: return null
        return start.plusDays(ChronoUnit.DAYS.between(previous, start))
    }

    /**
     * יום החודש: the same Hebrew day-of-month as [start], in the Hebrew month
     * immediately after [start]'s — via [HebrewMonthSequence], so leap-year
     * Adar I/II and the Elul→Tishrei year rollover are handled exactly as the
     * main Calendar tab already relies on. Clamps to the next month's last day
     * when it is shorter than [start]'s day-of-month (the same "same day next
     * month, else month-end" rule an ordinary Gregorian calendar uses).
     */
    fun yomHachodesh(start: LocalDate): LocalDate? {
        val jd = JewishDate(start.toGregorianCalendar())
        val index = HebrewMonthSequence.indexOf(HebrewMonthRef(jd.jewishYear, jd.jewishMonth))
        if (index < 0 || index + 1 >= HebrewMonthSequence.size) return null
        val next = HebrewMonthSequence.refAt(index + 1)
        val day = jd.jewishDayOfMonth.coerceAtMost(next.daysInMonth)
        return JewishDate(next.year, next.month, day).toLocalDate()
    }

    fun predict(start: LocalDate, previousStart: LocalDate?): VesetPrediction = VesetPrediction(
        sourceStart = start,
        onahBeinonit = onahBeinonit(start),
        haflaga = haflaga(start, previousStart),
        yomHachodesh = yomHachodesh(start),
    )

    /** The 7 dates of the count, day 1 = [firstCleanDay] itself. */
    fun cleanDayDates(firstCleanDay: LocalDate): List<LocalDate> =
        (0 until CLEAN_DAYS_COUNT).map { firstCleanDay.plusDays(it.toLong()) }

    /** Which day (1..7) of the count [date] is, or null when it falls outside the window. */
    fun cleanDayNumber(firstCleanDay: LocalDate, date: LocalDate): Int? =
        (ChronoUnit.DAYS.between(firstCleanDay, date) + 1).toInt().takeIf { it in 1..CLEAN_DAYS_COUNT }
}
