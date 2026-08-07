package com.zmanimclock.app.feature.calendar.model

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The month sequence is the whole navigation model, so it is tested hard.
 *
 * The failure this file exists to prevent: Adar I / Adar II appearing in the
 * wrong year, in the wrong order, or in a common year at all. A calendar that
 * gets that wrong looks completely fine for eleven months and then jumps from
 * Shevat straight to Nissan, or shows "אדר" twice.
 */
class HebrewMonthSequenceTest {

    private val range = HebrewMonthSequence.MIN_YEAR..HebrewMonthSequence.MAX_YEAR

    private fun isLeap(y: Int) = JewishDate(y, 7, 1).isJewishLeapYear

    @Test
    fun `leap years match the Metonic rule and KosherJava, for every year in range`() {
        for (y in range) {
            val metonic = (7 * y + 1) % 19 < 7
            assertEquals("year $y", metonic, isLeap(y))
        }
    }

    @Test
    fun `a common year contributes 12 months and a leap year 13`() {
        for (y in range) {
            val count = (0 until HebrewMonthSequence.size)
                .asSequence()
                .map { HebrewMonthSequence.refAt(it) }
                .count { it.year == y }
            assertEquals("year $y", if (isLeap(y)) 13 else 12, count)
        }
    }

    @Test
    fun `months within a year run Tishrei to Elul, with Adar II only when it exists`() {
        for (y in listOf(5786, 5787, 5788, 5790, 5800)) {
            val actual = (0 until HebrewMonthSequence.size)
                .asSequence()
                .map { HebrewMonthSequence.refAt(it) }
                .filter { it.year == y }
                .map { it.month }
                .toList()
            val expected =
                if (isLeap(y)) listOf(7, 8, 9, 10, 11, 12, 13, 1, 2, 3, 4, 5, 6)
                else listOf(7, 8, 9, 10, 11, 12, 1, 2, 3, 4, 5, 6)
            assertEquals("year $y (leap=${isLeap(y)})", expected, actual)
        }
    }

    /**
     * The single most valuable assertion here. If Adar I and Adar II were
     * swapped, or Elul did not roll the year, or a month were duplicated or
     * skipped, time would stop increasing somewhere in these ~2400 pairs.
     */
    @Test
    fun `the whole sequence is strictly increasing in time`() {
        var previous = HebrewMonthSequence.refAt(0).firstDay
        for (i in 1 until HebrewMonthSequence.size) {
            val ref = HebrewMonthSequence.refAt(i)
            val current = ref.firstDay
            assertTrue(
                "index $i (${ref.year}/${ref.month}) starts $current, " +
                    "not after the previous month's $previous",
                current.isAfter(previous),
            )
            previous = current
        }
    }

    @Test
    fun `consecutive months are exactly one month apart, never skipping`() {
        for (i in 1 until HebrewMonthSequence.size) {
            val prev = HebrewMonthSequence.refAt(i - 1)
            val cur = HebrewMonthSequence.refAt(i)
            assertEquals(
                "gap between ${prev.year}/${prev.month} and ${cur.year}/${cur.month}",
                prev.daysInMonth.toLong(),
                java.time.temporal.ChronoUnit.DAYS.between(prev.firstDay, cur.firstDay),
            )
        }
    }

    @Test
    fun `index and ref round-trip`() {
        for (i in 0 until HebrewMonthSequence.size) {
            assertEquals(i, HebrewMonthSequence.indexOf(HebrewMonthSequence.refAt(i)))
        }
    }

    @Test
    fun `every day of a leap year and a common year maps to its own month`() {
        for (y in listOf(5786, 5787)) { // 5786 common, 5787 leap
            val months = (0 until HebrewMonthSequence.size)
                .map { HebrewMonthSequence.refAt(it) }
                .filter { it.year == y }
            for (ref in months) {
                for (d in 1..ref.daysInMonth) {
                    val date = JewishDate(ref.year, ref.month, d).toLocalDate()
                    assertEquals(
                        "$date should map to ${ref.year}/${ref.month}",
                        HebrewMonthSequence.indexOf(ref),
                        HebrewMonthSequence.indexOf(date),
                    )
                }
            }
        }
    }

    @Test
    fun `Shevat leads to Adar I then Adar II then Nissan in a leap year`() {
        val leap = 5787
        assertTrue("5787 must be a leap year for this test", isLeap(leap))
        val shevat = HebrewMonthSequence.indexOf(HebrewMonthRef(leap, 11))
        assertEquals(HebrewMonthRef(leap, 12), HebrewMonthSequence.refAt(shevat + 1)) // אדר א׳
        assertEquals(HebrewMonthRef(leap, 13), HebrewMonthSequence.refAt(shevat + 2)) // אדר ב׳
        assertEquals(HebrewMonthRef(leap, 1), HebrewMonthSequence.refAt(shevat + 3))  // ניסן
    }

    @Test
    fun `Shevat leads straight to Adar then Nissan in a common year`() {
        val common = 5786
        assertTrue("5786 must be a common year for this test", !isLeap(common))
        val shevat = HebrewMonthSequence.indexOf(HebrewMonthRef(common, 11))
        assertEquals(HebrewMonthRef(common, 12), HebrewMonthSequence.refAt(shevat + 1)) // אדר
        assertEquals(HebrewMonthRef(common, 1), HebrewMonthSequence.refAt(shevat + 2))  // ניסן
    }

    @Test
    fun `Elul rolls over into Tishrei of the next year`() {
        val elul = HebrewMonthSequence.indexOf(HebrewMonthRef(5786, 6))
        assertEquals(HebrewMonthRef(5787, 7), HebrewMonthSequence.refAt(elul + 1))
    }

    @Test
    fun `out-of-range lookups return -1 rather than throwing`() {
        assertEquals(-1, HebrewMonthSequence.indexOf(HebrewMonthRef(5699, 7)))
        assertEquals(-1, HebrewMonthSequence.indexOf(HebrewMonthRef(5901, 7)))
        // Month 13 of a common year does not exist.
        assertEquals(-1, HebrewMonthSequence.indexOf(HebrewMonthRef(5786, 13)))
    }

    @Test
    fun `the ends of the range are usable and clamp instead of throwing`() {
        assertEquals(0, HebrewMonthSequence.clamp(-5))
        assertEquals(HebrewMonthSequence.size - 1, HebrewMonthSequence.clamp(Int.MAX_VALUE))
        // Both ends must actually build.
        MonthGridBuilder.build(HebrewMonthSequence.refAt(0))
        MonthGridBuilder.build(HebrewMonthSequence.refAt(HebrewMonthSequence.size - 1))
    }

    @Test
    fun `today is inside the range`() {
        assertTrue(HebrewMonthSequence.indexOf(LocalDate.now()) >= 0)
    }
}
