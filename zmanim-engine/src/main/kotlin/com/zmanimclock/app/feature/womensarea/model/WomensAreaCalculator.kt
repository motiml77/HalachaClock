package com.zmanimclock.app.feature.womensarea.model

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.calendar.model.HebrewMonthRef
import com.zmanimclock.app.feature.calendar.model.HebrewMonthSequence
import com.zmanimclock.app.feature.calendar.model.toGregorianCalendar
import com.zmanimclock.app.feature.calendar.model.toLocalDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * עונת יום / עונת לילה — as the user reported it when entering the veset.
 * Every separation day inherits the onah of the veset it is computed from.
 */
enum class Onah { DAY, NIGHT }

/** Which of the three separation days a marker represents. */
enum class VesetKind { ONAH_BEINONIT, HAFLAGA, YOM_HACHODESH }

/** One separation day: which kind, on which Hebrew day, in which onah, and its number in the count. */
data class PrishaDay(
    val kind: VesetKind,
    val date: LocalDate,
    /** Null only for an entry saved before the onah was asked for. */
    val onah: Onah?,
    /** Its place in the count that starts at 1 on the veset day itself. */
    val dayNumber: Int,
)

/**
 * Everything one veset entry produces.
 *
 * THE DATE CONVENTION, which every field here follows
 * Each [LocalDate] stands for ONE HEBREW DAY — the Gregorian date on whose
 * daytime that Hebrew date falls, exactly the date a cell of the Hebrew
 * month grid carries (see MonthGridBuilder). The night onah of a Hebrew day
 * is the evening BEFORE that Gregorian date: a veset seen on Tuesday evening
 * after shkia is ליל ט״ו, stored as the ט״ו cell with [Onah.NIGHT]. Because a
 * cell is one Hebrew day, counting cells is counting Hebrew dates, and plain
 * LocalDate day arithmetic on these values is exactly that count.
 */
data class VesetPrediction(
    val sourceStart: LocalDate,
    val onah: Onah?,
    /** Day 30 of the count (the veset day itself is day 1). */
    val onahBeinonit: LocalDate,
    /** Null when there is no earlier veset to measure a haflaga from. */
    val haflaga: LocalDate?,
    /**
     * The haflaga length, counted inclusively the way it is counted on the
     * luach: the previous veset day is 1, and the count runs up to AND
     * including this veset's own day. Null together with [haflaga].
     */
    val haflagaInterval: Int?,
    /**
     * The same Hebrew day-of-month in the next Hebrew month. Null when that
     * month has no such day (a veset on ל׳ followed by a 29-day month) —
     * see [yomHachodeshMissing] — or past HebrewMonthSequence's range.
     */
    val yomHachodesh: LocalDate?,
    /** True when [yomHachodesh] is null because the next month has no ל׳. */
    val yomHachodeshMissing: Boolean,
) {
    /** The separation days, in date order. */
    val prishaDays: List<PrishaDay>
        get() = listOfNotNull(
            PrishaDay(VesetKind.ONAH_BEINONIT, onahBeinonit, onah, dayNumberOf(onahBeinonit)),
            haflaga?.let { PrishaDay(VesetKind.HAFLAGA, it, onah, dayNumberOf(it)) },
            yomHachodesh?.let { PrishaDay(VesetKind.YOM_HACHODESH, it, onah, dayNumberOf(it)) },
        ).sortedWith(compareBy({ it.date }, { it.kind.ordinal }))

    /** How far the on-calendar count runs: at least to day 30, and on to the latest separation day. */
    val lastCountedDay: Int
        get() = prishaDays.maxOf { it.dayNumber }.coerceAtLeast(WomensAreaCalculator.ONAH_BEINONIT_DAY)

    /** [date]'s number in the count (the veset day = 1), or null outside 1..[lastCountedDay]. */
    fun countDayNumber(date: LocalDate): Int? =
        dayNumberOf(date).takeIf { it in 1..lastCountedDay }

    private fun dayNumberOf(date: LocalDate): Int =
        (ChronoUnit.DAYS.between(sourceStart, date) + 1).toInt()
}

/**
 * Veset date arithmetic for the Women's Area. Counting only: this object
 * answers "which Hebrew day is day 30" and "which is the same date next
 * month", never "is she permitted". That judgment stays with the user and
 * her own rabbi.
 */
object WomensAreaCalculator {

    /** עונה בינונית is the 30th day, counting the veset day itself as the 1st. */
    const val ONAH_BEINONIT_DAY = 30
    const val CLEAN_DAYS_COUNT = 7

    /** עונה בינונית: day 30 of the count — [start] is day 1, so 29 days after it. */
    fun onahBeinonit(start: LocalDate): LocalDate = start.plusDays((ONAH_BEINONIT_DAY - 1).toLong())

    /**
     * The haflaga length: the previous veset's day is 1, counting up to and
     * including [start]'s day. Null with no previous veset, or when
     * [previousStart] is not actually earlier.
     */
    fun haflagaInterval(start: LocalDate, previousStart: LocalDate?): Int? {
        val previous = previousStart?.takeIf { it < start } ?: return null
        return (ChronoUnit.DAYS.between(previous, start) + 1).toInt()
    }

    /**
     * הפלגה: the same count run forward from [start] — [start] is day 1, and
     * day [haflagaInterval] is the separation day.
     */
    fun haflaga(start: LocalDate, previousStart: LocalDate?): LocalDate? {
        val interval = haflagaInterval(start, previousStart) ?: return null
        return start.plusDays((interval - 1).toLong())
    }

    /**
     * יום החודש: the same Hebrew day-of-month as [start], in the Hebrew month
     * immediately after [start]'s — via [HebrewMonthSequence], so leap-year
     * Adar I/II and the Elul→Tishrei year rollover step exactly as the
     * Calendar tab steps them.
     *
     * A veset on ל׳ followed by a 29-day month has no such day, and this
     * returns null rather than moving it to כ״ט or to the next ראש חודש:
     * which day (if any) applies then is a question for a rabbi, not a
     * default for arithmetic to pick. [isYomHachodeshMissing] tells that case
     * apart from the out-of-range one.
     */
    fun yomHachodesh(start: LocalDate): LocalDate? {
        val (jd, next) = nextMonthOf(start) ?: return null
        if (jd.jewishDayOfMonth > next.daysInMonth) return null
        return JewishDate(next.year, next.month, jd.jewishDayOfMonth).toLocalDate()
    }

    /** True when [start] is a ל׳ and the next Hebrew month has only 29 days. */
    fun isYomHachodeshMissing(start: LocalDate): Boolean {
        val (jd, next) = nextMonthOf(start) ?: return false
        return jd.jewishDayOfMonth > next.daysInMonth
    }

    private fun nextMonthOf(start: LocalDate): Pair<JewishDate, HebrewMonthRef>? {
        val jd = JewishDate(start.toGregorianCalendar())
        val index = HebrewMonthSequence.indexOf(HebrewMonthRef(jd.jewishYear, jd.jewishMonth))
        if (index < 0 || index + 1 >= HebrewMonthSequence.size) return null
        return jd to HebrewMonthSequence.refAt(index + 1)
    }

    fun predict(start: LocalDate, onah: Onah?, previousStart: LocalDate?): VesetPrediction = VesetPrediction(
        sourceStart = start,
        onah = onah,
        onahBeinonit = onahBeinonit(start),
        haflaga = haflaga(start, previousStart),
        haflagaInterval = haflagaInterval(start, previousStart),
        yomHachodesh = yomHachodesh(start),
        yomHachodeshMissing = isYomHachodeshMissing(start),
    )

    /**
     * שבעה נקיים after a הפסק טהרה made on [hefsek] before shkia: they begin
     * with the NEXT Hebrew day — the one that starts at that evening's tzeit —
     * and run 7 Hebrew days. A hefsek on Tuesday before shkia makes Wednesday
     * day 1 and the following Tuesday day 7.
     */
    fun cleanDayDates(hefsek: LocalDate): List<LocalDate> =
        (1..CLEAN_DAYS_COUNT).map { hefsek.plusDays(it.toLong()) }

    /** Which clean day (1..7) [date] is after a hefsek on [hefsek], or null outside them. */
    fun cleanDayNumber(hefsek: LocalDate, date: LocalDate): Int? =
        ChronoUnit.DAYS.between(hefsek, date).toInt().takeIf { it in 1..CLEAN_DAYS_COUNT }

    /**
     * The day the tevila is made AFTER — the 7th clean day. The tevila is only
     * once it has ended, after tzeit: a 7th day on Sunday means tevila on
     * Sunday night after tzeit, which on the luach is already ליל שני. The
     * calendar marks this day (with a star), since this is the day she lives
     * through and waits out; [tevilaNight] is the Hebrew day that night opens.
     */
    fun tevilaDay(hefsek: LocalDate): LocalDate = hefsek.plusDays(CLEAN_DAYS_COUNT.toLong())

    /** The Hebrew day whose NIGHT is ליל הטבילה — the day after [tevilaDay]. */
    fun tevilaNight(hefsek: LocalDate): LocalDate = tevilaDay(hefsek).plusDays(1)
}
