package com.zmanimclock.app.feature.calendar.model

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import java.time.LocalDate

/**
 * Every Hebrew month in range, in chronological order, computed once.
 *
 * WHY A PRECOMPUTED LIST AND NOT "month + 1"
 * Stepping a Hebrew month is the single place a calendar goes wrong quietly:
 * Adar I and Adar II exist only in leap years, Elul rolls the year over, and
 * KosherJava's own [JewishDate.forward] preserves the day-of-month (so
 * stepping from 30 Cheshvan can land on a day the next month does not have)
 * and has no backward equivalent at all. Computing the order once, here, turns
 * navigation into an array index — and lets a unit test assert that the whole
 * sequence is strictly increasing in time, which no per-swipe arithmetic can
 * be checked for.
 *
 * Within a year the order is Tishrei → Elul, which is calendar order rather
 * than KosherJava's Nissan-first numbering:
 *
 *     7, 8, 9, 10, 11, 12, [13 only if leap], 1, 2, 3, 4, 5, 6
 *
 * RANGE: Hebrew 5700–5900 (≈1939–2140). Outside it ChaiTables has no terrain
 * data and no DST rule is knowable, so navigation stops at the ends rather
 * than throwing from deep inside KosherJava.
 */
object HebrewMonthSequence {

    const val MIN_YEAR = 5700
    const val MAX_YEAR = 5900

    /** Tishrei-first month order for a year, with Adar II only when it exists. */
    private fun monthsOf(year: Int): IntArray {
        val leap = JewishDate(year, HebrewMonthRef.TISHREI, 1).isJewishLeapYear
        return if (leap) {
            intArrayOf(7, 8, 9, 10, 11, 12, 13, 1, 2, 3, 4, 5, 6)
        } else {
            intArrayOf(7, 8, 9, 10, 11, 12, 1, 2, 3, 4, 5, 6)
        }
    }

    private val months: List<HebrewMonthRef> by lazy {
        val out = ArrayList<HebrewMonthRef>((MAX_YEAR - MIN_YEAR + 1) * 13)
        for (y in MIN_YEAR..MAX_YEAR) {
            for (m in monthsOf(y)) out.add(HebrewMonthRef(y, m))
        }
        out
    }

    /** Number of months in range — the pager's page count. */
    val size: Int get() = months.size

    /** The month at [index]. Callers must keep [index] within `0 until size`. */
    fun refAt(index: Int): HebrewMonthRef = months[index]

    /** The index of [ref], or -1 when it falls outside the range. */
    fun indexOf(ref: HebrewMonthRef): Int {
        if (ref.year !in MIN_YEAR..MAX_YEAR) return -1
        // Months per year vary (12 or 13), so the offset is accumulated rather
        // than multiplied. Cheap, and it cannot drift out of step with monthsOf.
        var i = 0
        for (y in MIN_YEAR until ref.year) i += monthsOf(y).size
        val within = monthsOf(ref.year).indexOf(ref.month)
        return if (within < 0) -1 else i + within
    }

    /** The index of the month containing [date], or -1 when out of range. */
    fun indexOf(date: LocalDate): Int = indexOf(HebrewMonthRef.containing(date))

    /** Clamps [index] into the valid range, for arrow buttons at the ends. */
    fun clamp(index: Int): Int = index.coerceIn(0, size - 1)
}
