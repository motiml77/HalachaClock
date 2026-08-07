package com.zmanimclock.app.feature.calendar.model

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Grid geometry and month labelling.
 *
 * The month-name assertions are pinned as literal strings on purpose:
 * HebrewDateFormatter inverts its own array indices for Adar (its month array
 * ends [… אדר, אדר ב, אדר א] — out of order deliberately), so a library
 * upgrade that "tidied" that would silently swap Adar I and Adar II.
 */
class MonthGridBuilderTest {

    private val years = 5780..5800

    private fun isLeap(y: Int) = JewishDate(y, 7, 1).isJewishLeapYear

    private fun monthsOf(y: Int): List<Int> =
        if (isLeap(y)) listOf(7, 8, 9, 10, 11, 12, 13, 1, 2, 3, 4, 5, 6)
        else listOf(7, 8, 9, 10, 11, 12, 1, 2, 3, 4, 5, 6)

    private fun allRefs() = years.flatMap { y -> monthsOf(y).map { HebrewMonthRef(y, it) } }

    // ------------------------------------------------------------ geometry

    @Test
    fun `every grid is exactly six rows of seven`() {
        for (ref in allRefs()) {
            val g = MonthGridBuilder.build(ref)
            assertEquals("${ref.year}/${ref.month}", 6, g.weeks.size)
            g.weeks.forEach { assertEquals("${ref.year}/${ref.month}", 7, it.size) }
        }
    }

    @Test
    fun `the first column offset matches the weekday of the 1st and is always 0 to 6`() {
        for (ref in allRefs()) {
            val g = MonthGridBuilder.build(ref)
            val expected = JewishDate(ref.year, ref.month, 1).dayOfWeek - 1
            assertEquals("${ref.year}/${ref.month}", expected, g.firstColumnOffset)
            assertTrue("${ref.year}/${ref.month} offset ${g.firstColumnOffset}", g.firstColumnOffset in 0..6)
        }
    }

    @Test
    fun `each cell sits in the column matching its weekday, Sunday first`() {
        for (ref in allRefs()) {
            val g = MonthGridBuilder.build(ref)
            g.weeks.forEach { row ->
                row.forEachIndexed { col, meta ->
                    // DayOfWeek.SUNDAY.value is 7 in java.time; the grid uses 0.
                    val sundayIndex = meta.dayOfWeek.value % 7
                    assertEquals("${meta.date} in column $col", sundayIndex, col)
                }
            }
        }
    }

    @Test
    fun `the month's own days are contiguous, ascending, and exactly daysInMonth`() {
        for (ref in allRefs()) {
            val g = MonthGridBuilder.build(ref)
            val own = g.daysInThisMonth
            assertEquals("${ref.year}/${ref.month}", ref.daysInMonth, own.size)
            own.forEachIndexed { i, meta ->
                assertEquals("${ref.year}/${ref.month} cell $i", i + 1, meta.hebrewDayOfMonth)
                if (i > 0) {
                    assertEquals(own[i - 1].date.plusDays(1), meta.date)
                }
            }
        }
    }

    @Test
    fun `filler cells belong to the neighbouring months and are flagged as such`() {
        val g = MonthGridBuilder.build(HebrewMonthRef(5786, 5)) // אב תשפ"ו
        val filler = g.allDays.filter { !it.isInDisplayedMonth }
        assertTrue("a month starting mid-week must have leading filler", filler.isNotEmpty())
        filler.forEach {
            assertTrue(
                "${it.date} is flagged as filler but is in month ${it.hebrewMonth}",
                it.hebrewMonth != 5 || it.hebrewYear != 5786,
            )
        }
    }

    @Test
    fun `both five-row and six-row months occur, so the fixed six-row layout is exercised`() {
        val needed = allRefs().map { MonthGridBuilder.build(it) }
            .map { (it.firstColumnOffset + it.daysInMonth + 6) / 7 }
        assertTrue("no 5-row month found", needed.any { it == 5 })
        assertTrue("no 6-row month found", needed.any { it == 6 })
        assertTrue("a month needed $needed rows", needed.all { it in 5..6 })
    }

    // -------------------------------------------------------- month lengths

    @Test
    fun `fixed-length months never vary`() {
        val fixed = mapOf(7 to 30, 10 to 29, 11 to 30, 1 to 30, 2 to 29, 3 to 30, 4 to 29, 5 to 30, 6 to 29)
        for (y in years) {
            for ((month, days) in fixed) {
                assertEquals("year $y month $month", days, HebrewMonthRef(y, month).daysInMonth)
            }
        }
    }

    @Test
    fun `Cheshvan and Kislev are the variable ones`() {
        for (y in years) {
            assertTrue(HebrewMonthRef(y, 8).daysInMonth in 29..30)
            assertTrue(HebrewMonthRef(y, 9).daysInMonth in 29..30)
        }
    }

    @Test
    fun `Adar I is 30 days and Adar II is 29, and a common Adar is 29`() {
        for (y in years) {
            if (isLeap(y)) {
                assertEquals("year $y Adar I", 30, HebrewMonthRef(y, 12).daysInMonth)
                assertEquals("year $y Adar II", 29, HebrewMonthRef(y, 13).daysInMonth)
            } else {
                assertEquals("year $y Adar", 29, HebrewMonthRef(y, 12).daysInMonth)
            }
        }
    }

    /**
     * Sums the months back up to the year. This is the assertion that catches
     * an extra or missing month in the sequence — the same class of bug as an
     * Adar mix-up, but visible as a total rather than as an ordering.
     */
    @Test
    fun `the months of a year add up to the year's own length`() {
        for (y in years) {
            val sum = monthsOf(y).sumOf { HebrewMonthRef(y, it).daysInMonth }
            assertEquals("year $y", JewishDate.getDaysInJewishYear(y), sum)
            val valid = if (isLeap(y)) setOf(383, 384, 385) else setOf(353, 354, 355)
            assertTrue("year $y length $sum", sum in valid)
        }
    }

    // -------------------------------------------------------------- labels

    @Test
    fun `Adar is labelled by shita, not by hand — the formatter inverts its own indices`() {
        // 5786 common, 5787 leap.
        assertEquals("אדר", MonthGridBuilder.build(HebrewMonthRef(5786, 12)).hebrewMonthLabel)
        assertEquals("אדר א׳", MonthGridBuilder.build(HebrewMonthRef(5787, 12)).hebrewMonthLabel)
        assertEquals("אדר ב׳", MonthGridBuilder.build(HebrewMonthRef(5787, 13)).hebrewMonthLabel)
    }

    @Test
    fun `ordinary month and year labels`() {
        val g = MonthGridBuilder.build(HebrewMonthRef(5786, 5))
        assertEquals("אב", g.hebrewMonthLabel)
        assertEquals("תשפ״ו", g.hebrewYearLabel)
    }

    @Test
    fun `Hebrew day numerals come from the library, gershayim included`() {
        val g = MonthGridBuilder.build(HebrewMonthRef(5786, 5)) // אב, 30 days
        val own = g.daysInThisMonth
        assertEquals("ט״ו", own[14].hebrewDayLabel)
        assertEquals("ט״ז", own[15].hebrewDayLabel)
        assertEquals("כ׳", own[19].hebrewDayLabel)
        assertEquals("ל׳", own[29].hebrewDayLabel)
    }

    @Test
    fun `the Gregorian span names two years when the Hebrew month straddles them`() {
        // טבת תשפ"ז runs from December 2026 into January 2027.
        val tevet = MonthGridBuilder.build(HebrewMonthRef(5787, 10))
        assertEquals("דצמבר 2026 – ינואר 2027", tevet.gregorianSpanLabel)
    }

    @Test
    fun `the Gregorian span names two months and one year in the ordinary case`() {
        val av = MonthGridBuilder.build(HebrewMonthRef(5786, 5))
        assertEquals("יולי–אוגוסט 2026", av.gregorianSpanLabel)
    }
}
