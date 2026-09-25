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

/** A night on which there is no tevila. */
enum class TevilaBlock { YOM_KIPPUR, TISHA_BEAV }

/**
 * Which separation day a marker represents: the three from the latest veset,
 * and the two carried over from earlier ones that have not been uprooted.
 */
enum class VesetKind {
    ONAH_BEINONIT,
    HAFLAGA,
    YOM_HACHODESH,
    /** יום החודש of an EARLIER veset whose date is still ahead — not yet uprooted. */
    YOM_HACHODESH_PREVIOUS,
    /** An earlier, longer haflaga not yet uprooted, counted from the latest veset. */
    HAFLAGA_NOT_UPROOTED,
}

/** One separation day: which kind, on which Hebrew day, in which onah, and its number in the count. */
data class PrishaDay(
    val kind: VesetKind,
    val date: LocalDate,
    /** Null only for an entry saved before the onah was asked for. */
    val onah: Onah?,
    /** Its place in the count that starts at 1 on the veset day itself. */
    val dayNumber: Int,
    /** For a carried-over day: the earlier veset it comes from (יום החודש) or where its haflaga began. */
    val fromVeset: LocalDate? = null,
    /** For [VesetKind.HAFLAGA_NOT_UPROOTED]: the haflaga's length. */
    val interval: Int? = null,
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
    /** Days carried over from earlier vesets, not yet uprooted (see WomensAreaCalculator.carriedOver). */
    val carried: List<PrishaDay> = emptyList(),
) {
    /** The separation days, in date order — this veset's own, then any carried over. */
    val prishaDays: List<PrishaDay>
        get() = (
            listOfNotNull(
                PrishaDay(VesetKind.ONAH_BEINONIT, onahBeinonit, onah, dayNumberOf(onahBeinonit)),
                haflaga?.let { PrishaDay(VesetKind.HAFLAGA, it, onah, dayNumberOf(it)) },
                yomHachodesh?.let { PrishaDay(VesetKind.YOM_HACHODESH, it, onah, dayNumberOf(it)) },
            ) + carried
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

    /**
     * [predict], plus the days carried over from [earlier] vesets (any order,
     * all before [start]) — the full picture the calendar shows.
     */
    fun predictWithHistory(start: LocalDate, onah: Onah?, earlier: List<VesetRecord>): VesetPrediction {
        val before = earlier.filter { it.date < start }
        return predict(start, onah, before.maxOfOrNull { it.date })
            .copy(carried = carriedOver(start, onah, before))
    }

    /**
     * The separation days that EARLIER vesets still impose after a new one on
     * [start] — the ones not yet uprooted, because their day has not passed:
     *
     * יום החודש מראייה קודמת — any earlier veset's יום החודש that falls AFTER
     * [start]. (One already passed without a sighting was uprooted; one the
     * new veset fell on is that veset itself.) In the earlier veset's onah.
     *
     * הפלגה שלא נעקרה — an earlier haflaga LONGER than every haflaga after it:
     * a longer haflaga uproots a shorter one, never the reverse, so a long one
     * followed only by shorter ones was never tested and still counts, from
     * [start]. Only when EVERY veset from the start of that haflaga up to and
     * including [start] was in one onah — all ביום or all בלילה (the owner's
     * rule); otherwise it is not carried at all.
     */
    fun carriedOver(start: LocalDate, onah: Onah?, earlier: List<VesetRecord>): List<PrishaDay> {
        val before = earlier.filter { it.date < start }.sortedBy { it.date }
        fun dayNumber(date: LocalDate) = (ChronoUnit.DAYS.between(start, date) + 1).toInt()
        val out = mutableListOf<PrishaDay>()

        before.forEach { e ->
            val yom = yomHachodesh(e.date) ?: return@forEach
            if (yom > start) out += PrishaDay(VesetKind.YOM_HACHODESH_PREVIOUS, yom, e.onah, dayNumber(yom), fromVeset = e.date)
        }

        if (onah != null && before.isNotEmpty()) {
            val chain = before + VesetRecord(-1, start, onah)
            // haflagot[i] runs from chain[i] to chain[i + 1]; the last is the current one.
            val haflagot = (0 until chain.size - 1).map { haflagaInterval(chain[it + 1].date, chain[it].date)!! }
            var longestSince = haflagot.last()
            for (i in haflagot.size - 2 downTo 0) {
                val h = haflagot[i]
                val sameOnah = chain.subList(i, chain.size).all { it.onah == onah }
                if (h > longestSince && sameOnah) {
                    val date = start.plusDays((h - 1).toLong())
                    out += PrishaDay(VesetKind.HAFLAGA_NOT_UPROOTED, date, onah, h, fromVeset = chain[i].date, interval = h)
                }
                longestSince = maxOf(longestSince, h)
            }
        }
        return out
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
     * The earliest day of the count (the veset day = 1) a הפסק טהרה is
     * normally made on — the 5th, as the Rema counts it: a veset on Sunday,
     * a hefsek on Thursday at the earliest. The app only WARNS before it
     * (see [isEarlyHefsek]); whether an earlier one is valid is a question
     * for a rabbi, and she can always carry on.
     */
    const val MIN_HEFSEK_DAY = 5

    /**
     * Which day of the count a hefsek on [hefsek] falls on, counting from
     * [veset] as day 1 — or null with no veset on or before it.
     */
    fun hefsekDayNumber(veset: LocalDate?, hefsek: LocalDate): Int? {
        val start = veset?.takeIf { it <= hefsek } ?: return null
        return (ChronoUnit.DAYS.between(start, hefsek) + 1).toInt()
    }

    /** True when a hefsek on [hefsek] would be before the [MIN_HEFSEK_DAY]th day of [veset]'s count. */
    fun isEarlyHefsek(veset: LocalDate?, hefsek: LocalDate): Boolean =
        hefsekDayNumber(veset, hefsek)?.let { it < MIN_HEFSEK_DAY } ?: false

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

    /**
     * A night on which there is no tevila, if ליל הטבילה after [hefsek] is
     * one: the night of יום הכיפורים (10 Tishrei), or of תשעה באב — the 9th of
     * Av, and also the 10th when the fast is deferred to it because the 9th
     * was Shabbat (both nights are then a question). The app only says so;
     * what to do is for a rabbi.
     */
    fun tevilaNightBlock(hefsek: LocalDate): TevilaBlock? {
        val night = tevilaNight(hefsek)
        val jd = JewishDate(night.toGregorianCalendar())
        return when {
            jd.jewishMonth == TISHREI && jd.jewishDayOfMonth == 10 -> TevilaBlock.YOM_KIPPUR
            jd.jewishMonth == AV && jd.jewishDayOfMonth == 9 -> TevilaBlock.TISHA_BEAV
            jd.jewishMonth == AV && jd.jewishDayOfMonth == 10 &&
                night.minusDays(1).dayOfWeek == java.time.DayOfWeek.SATURDAY -> TevilaBlock.TISHA_BEAV
            else -> null
        }
    }

    private const val TISHREI = 7
    private const val AV = 5
}
