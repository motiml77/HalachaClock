package com.zmanimclock.app.feature.womensarea.model

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.calendar.model.toGregorianCalendar
import com.zmanimclock.app.feature.calendar.model.toLocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Calendar

/**
 * הפלגה — and the other two separation days around it — across every kind of
 * month boundary: a full (30) or deficient (29) month in between, Cheshvan and
 * Kislev at both their lengths, Adar I / Adar II in a leap year, and the
 * Elul→Tishrei year change.
 *
 * Every case is stated in HEBREW dates with the expected answer worked out by
 * hand in its comment, and is then checked a second way: by an ORACLE that
 * walks the Hebrew calendar one day at a time with KosherJava's own
 * JewishDate.forward — a different code path from the calculator's LocalDate
 * arithmetic — counting the veset day as 1.
 *
 * Month numbers are KosherJava's: 1 ניסן … 6 אלול, 7 תשרי, 8 חשוון, 9 כסלו,
 * 10 טבת, 11 שבט, 12 אדר (אדר א׳ in a leap year), 13 אדר ב׳.
 */
class WomensAreaHaflagaTest {

    // ------------------------------------------------------------- helpers

    private fun h(year: Int, month: Int, day: Int): LocalDate = JewishDate(year, month, day).toLocalDate()

    private fun heb(date: LocalDate): String {
        val jd = JewishDate(date.toGregorianCalendar())
        return "${jd.jewishDayOfMonth}/${jd.jewishMonth}/${jd.jewishYear}"
    }

    /** Oracle: the Hebrew day that is day [n] of a count starting at [start] (= day 1), by stepping. */
    private fun oracleDay(start: LocalDate, n: Int): LocalDate {
        val jd = JewishDate(start.toGregorianCalendar())
        repeat(n - 1) { jd.forward(Calendar.DATE, 1) }
        return jd.toLocalDate()
    }

    /** Oracle: the inclusive count from [from] (= 1) to [to], by stepping. */
    private fun oracleInterval(from: LocalDate, to: LocalDate): Int {
        val jd = JewishDate(from.toGregorianCalendar())
        var n = 1
        while (jd.toLocalDate() != to) { jd.forward(Calendar.DATE, 1); n++ }
        return n
    }

    /** Asserts one haflaga case both ways: the hand-worked answer, and the oracle. */
    private fun assertHaflaga(prev: LocalDate, cur: LocalDate, interval: Int, expected: LocalDate) {
        assertEquals("interval ${heb(prev)} → ${heb(cur)}", interval, WomensAreaCalculator.haflagaInterval(cur, prev))
        assertEquals("oracle interval", oracleInterval(prev, cur), interval)
        val actual = WomensAreaCalculator.haflaga(cur, prev)!!
        assertEquals("haflaga from ${heb(cur)}: expected ${heb(expected)}, got ${heb(actual)}", expected, actual)
        assertEquals("oracle haflaga", oracleDay(cur, interval), actual)
        // And it is the interval-th day of the new count, by definition.
        assertEquals(interval, WomensAreaCalculator.predict(cur, Onah.DAY, prev).countDayNumber(actual))
    }

    private fun length(year: Int, month: Int) = JewishDate(year, month, 1).daysInJewishMonth

    // ------------------------------------------- the calendar facts relied on

    @Test
    fun `the month lengths these cases are built on`() {
        // 5784: 383-day leap year, deficient — Cheshvan 29, Kislev 29.
        assertEquals(29, length(5784, 8)); assertEquals(29, length(5784, 9))
        // 5785: 355-day common year, full — Cheshvan 30, Kislev 30.
        assertEquals(30, length(5785, 8)); assertEquals(30, length(5785, 9))
        // 5786: 354-day common year, regular — Cheshvan 29, Kislev 30; Adar 29.
        assertEquals(29, length(5786, 8)); assertEquals(30, length(5786, 9)); assertEquals(29, length(5786, 12))
        assertFalse(JewishDate(5786, 7, 1).isJewishLeapYear)
        // 5787: 385-day leap year, full — Cheshvan 30, Kislev 30; Adar I 30, Adar II 29.
        assertTrue(JewishDate(5787, 7, 1).isJewishLeapYear)
        assertEquals(385, JewishDate(5787, 7, 1).daysInJewishYear)
        assertEquals(30, length(5787, 8)); assertEquals(30, length(5787, 9))
        assertEquals(30, length(5787, 12)); assertEquals(29, length(5787, 13))
        // Fixed lengths: Nissan 30, Iyar 29, Sivan 30, Tammuz 29, Elul 29, Tishrei 30, Tevet 29, Shevat 30.
        assertEquals(listOf(30, 29, 30, 29, 29, 30, 29, 30), listOf(1, 2, 3, 4, 6, 7, 10, 11).map { length(5786, it) })
    }

    // ------------------------------------------------ a 29 or 30 day month

    @Test
    fun `across a deficient month — 1 Iyar to 1 Sivan is 30, landing on 30 Sivan`() {
        // Iyar has 29: 1 Iyar = 1 … 29 Iyar = 29, 1 Sivan = 30. From 1 Sivan, day 30 = 30 Sivan.
        assertHaflaga(prev = h(5786, 2, 1), cur = h(5786, 3, 1), interval = 30, expected = h(5786, 3, 30))
    }

    @Test
    fun `across a full month — 1 Nissan to 1 Iyar is 31, and lands on 2 Sivan, not 1`() {
        // Nissan has 30, so the gap is 31. From 1 Iyar: 29 Iyar = 29, 1 Sivan = 30, 2 Sivan = 31.
        // The same Hebrew date one month on (1 Sivan) is NOT the haflaga — that is yom hachodesh.
        assertHaflaga(prev = h(5786, 1, 1), cur = h(5786, 2, 1), interval = 31, expected = h(5786, 3, 2))
        assertEquals(h(5786, 3, 1), WomensAreaCalculator.yomHachodesh(h(5786, 2, 1)))
    }

    @Test
    fun `Cheshvan full vs deficient changes the haflaga by a day`() {
        // 5785, Cheshvan 30: 1 Cheshvan → 1 Kislev = 31. Kislev 30 → day 31 = 1 Tevet.
        assertHaflaga(prev = h(5785, 8, 1), cur = h(5785, 9, 1), interval = 31, expected = h(5785, 10, 1))
        // 5786, Cheshvan 29: same Hebrew dates = 30. Kislev 30 → day 30 = 30 Kislev.
        assertHaflaga(prev = h(5786, 8, 1), cur = h(5786, 9, 1), interval = 30, expected = h(5786, 9, 30))
    }

    @Test
    fun `a deficient Kislev — 10 Kislev to 10 Tevet 5784 is 30, landing on 10 Shevat`() {
        // Kislev 5784 has 29: gap 30. From 10 Tevet (Tevet 29): 29 Tevet = 20, 10 Shevat = 30.
        assertHaflaga(prev = h(5784, 9, 10), cur = h(5784, 10, 10), interval = 30, expected = h(5784, 11, 10))
    }

    // ----------------------------------------------------------- leap years

    @Test
    fun `Adar I to Adar II — 10 Adar I to 10 Adar II is 31, landing on 11 Nissan`() {
        // Adar I 5787 has 30: gap 31. From 10 Adar II (29): 29 Adar II = 20, 1 Nissan = 21, 11 Nissan = 31.
        assertHaflaga(prev = h(5787, 12, 10), cur = h(5787, 13, 10), interval = 31, expected = h(5787, 1, 11))
    }

    @Test
    fun `the same Hebrew dates give a haflaga 30 days longer in a leap year`() {
        // 5786 (common): 15 Shevat → 15 Nissan = 30 (Shevat) + 29 (Adar) + 1 = 60.
        // From 15 Nissan: 30 Nissan = 16, 29 Iyar = 45, 15 Sivan = 60.
        assertHaflaga(prev = h(5786, 11, 15), cur = h(5786, 1, 15), interval = 60, expected = h(5786, 3, 15))
        // 5787 (leap): + Adar I's 30 = 90. From 15 Nissan: 29 Iyar = 45, 30 Sivan = 75, 15 Tammuz = 90.
        assertHaflaga(prev = h(5787, 11, 15), cur = h(5787, 1, 15), interval = 90, expected = h(5787, 4, 15))
    }

    @Test
    fun `end of Adar I to the start of Adar II is a 2-day gap`() {
        assertHaflaga(prev = h(5787, 12, 30), cur = h(5787, 13, 1), interval = 2, expected = h(5787, 13, 2))
    }

    @Test
    fun `a common-year Adar — 20 Adar to 18 Nissan 5786 is 28, landing on 15 Iyar`() {
        // Adar 29: 20…29 Adar = 10 days, 1…18 Nissan = 18 → 28. From 18 Nissan: 30 Nissan = 13, 15 Iyar = 28.
        assertHaflaga(prev = h(5786, 12, 20), cur = h(5786, 1, 18), interval = 28, expected = h(5786, 2, 15))
    }

    // ------------------------------------------------------- the year change

    @Test
    fun `across Rosh Hashana — 1 Elul 5786 to 1 Tishrei 5787 is 30, landing on 30 Tishrei`() {
        // Elul has 29: gap 30. From 1 Tishrei (30): day 30 = 30 Tishrei 5787.
        assertHaflaga(prev = h(5786, 6, 1), cur = h(5787, 7, 1), interval = 30, expected = h(5787, 7, 30))
    }

    @Test
    fun `a haflaga that itself crosses into the new year`() {
        // 20 Av → 18 Elul 5786: Av 30 → 11 + 18 = 29. From 18 Elul (29): 29 Elul = 12, 17 Tishrei 5787 = 29.
        assertHaflaga(prev = h(5786, 5, 20), cur = h(5786, 6, 18), interval = 29, expected = h(5787, 7, 17))
    }

    // ------------------------------------------------------------ short/long

    @Test
    fun `a short haflaga inside one month`() {
        // 1 → 22 Tammuz = 22. From 22 Tammuz (29): 29 Tammuz = 8, 14 Av = 22.
        assertHaflaga(prev = h(5786, 4, 1), cur = h(5786, 4, 22), interval = 22, expected = h(5786, 5, 14))
    }

    @Test
    fun `a long haflaga carries the count past day 30`() {
        val prev = h(5786, 11, 15); val cur = h(5786, 1, 15) // 60, from above
        val p = WomensAreaCalculator.predict(cur, Onah.NIGHT, prev)
        assertEquals(60, p.lastCountedDay)
        assertEquals(listOf(VesetKind.ONAH_BEINONIT, VesetKind.YOM_HACHODESH, VesetKind.HAFLAGA), p.prishaDays.map { it.kind })
    }

    // --------------------------------------------- how it sits with the rest

    @Test
    fun `a haflaga of exactly 30 falls on the onah beinonit, and both are marked`() {
        // Case above: 1 Iyar → 1 Sivan = 30, so both are 30 Sivan.
        val prev = h(5786, 2, 1); val cur = h(5786, 3, 1)
        val p = WomensAreaCalculator.predict(cur, Onah.DAY, prev)
        assertEquals(p.onahBeinonit, p.haflaga)
        val marker = WomensAreaMarkers.build(cur, Onah.DAY, prev, null).getValue(h(5786, 3, 30))
        assertEquals(setOf(VesetKind.ONAH_BEINONIT, VesetKind.HAFLAGA), marker.prisha.map { it.kind }.toSet())
    }

    @Test
    fun `the haflaga takes the onah of the CURRENT veset`() {
        val p = WomensAreaCalculator.predict(h(5786, 3, 1), Onah.NIGHT, h(5786, 2, 1))
        assertEquals(Onah.NIGHT, p.prishaDays.single { it.kind == VesetKind.HAFLAGA }.onah)
    }

    @Test
    fun `no haflaga without an earlier veset`() {
        val cur = h(5786, 3, 1)
        assertNull(WomensAreaCalculator.haflaga(cur, null))
        assertNull(WomensAreaCalculator.haflaga(cur, cur))
        assertNull(WomensAreaCalculator.haflaga(cur, cur.plusDays(3)))
        assertEquals(listOf(VesetKind.ONAH_BEINONIT, VesetKind.YOM_HACHODESH), WomensAreaCalculator.predict(cur, Onah.DAY, null).prishaDays.map { it.kind })
    }

    // -------------------------------------------- yom hachodesh at the edges

    @Test
    fun `yom hachodesh through Adar — common, Adar I, Adar II`() {
        assertEquals(h(5786, 1, 14), WomensAreaCalculator.yomHachodesh(h(5786, 12, 14))) // Adar → Nissan
        assertEquals(h(5787, 12, 14), WomensAreaCalculator.yomHachodesh(h(5787, 11, 14))) // Shevat → Adar I
        assertEquals(h(5787, 13, 14), WomensAreaCalculator.yomHachodesh(h(5787, 12, 14))) // Adar I → Adar II
        assertEquals(h(5787, 1, 14), WomensAreaCalculator.yomHachodesh(h(5787, 13, 14))) // Adar II → Nissan
    }

    @Test
    fun `a veset on the 30th has a yom hachodesh only when the next month has a 30th`() {
        assertTrue(WomensAreaCalculator.isYomHachodeshMissing(h(5787, 12, 30))) // Adar I 30 → Adar II has 29
        assertTrue(WomensAreaCalculator.isYomHachodeshMissing(h(5786, 11, 30))) // Shevat 30 → Adar has 29
        assertEquals(h(5785, 9, 30), WomensAreaCalculator.yomHachodesh(h(5785, 8, 30))) // Cheshvan 30 → Kislev 30 (5785)
        assertTrue(WomensAreaCalculator.isYomHachodeshMissing(h(5786, 7, 30))) // Tishrei 30 → Cheshvan 5786 has 29
        assertEquals(h(5787, 8, 30), WomensAreaCalculator.yomHachodesh(h(5787, 7, 30))) // … but Cheshvan 5787 has 30
        assertEquals(h(5786, 6, 30 - 1), WomensAreaCalculator.yomHachodesh(h(5786, 5, 29))) // 29 Av → 29 Elul (Elul never has a 30th)
    }

    // ---------------------------------------------------------- the sweep

    @Test
    fun `every start day in 5784–5788, gaps 20 to 40, agrees with the oracle`() {
        var start = h(5784, 7, 1)
        val end = h(5788, 7, 1)
        var checked = 0
        while (start < end) {
            for (gap in 20..40) {
                val cur = oracleDay(start, gap) // cur is day `gap` counting from start
                val haflaga = WomensAreaCalculator.haflaga(cur, start)!!
                assertEquals("gap from ${heb(start)}", gap, WomensAreaCalculator.haflagaInterval(cur, start))
                assertEquals("haflaga from ${heb(cur)}", oracleDay(cur, gap), haflaga)
                checked++
            }
            start = start.plusDays(1)
        }
        assertTrue(checked > 30_000)
    }
}
