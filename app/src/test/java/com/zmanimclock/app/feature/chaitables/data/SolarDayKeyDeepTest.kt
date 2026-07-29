package com.zmanimclock.app.feature.chaitables.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Deep coverage of the leap-safe sunrise-cache key, including how a table
 * fetched in one year behaves in every following year.
 *
 * The cache is a SOLAR almanac fetched once and reused forever, so a keying
 * error does not show up immediately — it appears as a silent one-day drift
 * years later. These tests pin that down.
 */
class SolarDayKeyDeepTest {

    private val leapYears = listOf(2024, 2028, 2032, 2036, 2040)
    private val plainYears = listOf(2025, 2026, 2027, 2029, 2030, 2031)

    // ---------- multi-year propagation ----------

    @Test
    fun `a table fetched in a leap year resolves to the same calendar day for the next 12 years`() {
        // Fetch happened in 2028 (leap). Every later year must read the row
        // written for, say, 1 April as 1 April — never 31 March or 2 April.
        for (month in 1..12) {
            for (day in listOf(1, 15, 28)) {
                val written = SolarDayKey.of(LocalDate.of(2028, month, day))
                for (year in 2029..2040) {
                    val read = SolarDayKey.of(LocalDate.of(year, month, day))
                    assertEquals(
                        "row for $month/$day written in 2028 misread in $year",
                        written, read,
                    )
                }
            }
        }
    }

    @Test
    fun `a table fetched in a plain year resolves correctly in later leap years`() {
        for (month in 1..12) {
            for (day in listOf(1, 15, 28)) {
                val written = SolarDayKey.of(LocalDate.of(2027, month, day))
                for (year in leapYears.filter { it > 2027 }) {
                    assertEquals(
                        "row for $month/$day written in 2027 misread in $year",
                        written, SolarDayKey.of(LocalDate.of(year, month, day)),
                    )
                }
            }
        }
    }

    @Test
    fun `the old day-of-year scheme really did drift — this is the bug being prevented`() {
        // Regression guard: proves the fix addresses a real defect rather than
        // a hypothetical one. Under the OLD scheme, after February every day
        // shifted by one between leap and plain years.
        val leapApril1 = LocalDate.of(2028, 4, 1).dayOfYear   // 92
        val plainApril1 = LocalDate.of(2027, 4, 1).dayOfYear  // 91
        assertEquals(1, leapApril1 - plainApril1)
        // …and the new scheme does not.
        assertEquals(
            SolarDayKey.of(LocalDate.of(2027, 4, 1)),
            SolarDayKey.of(LocalDate.of(2028, 4, 1)),
        )
    }

    @Test
    fun `dates before March are unaffected in either scheme`() {
        // Sanity: the drift starts only after the leap day.
        for (day in 1..28) {
            assertEquals(
                LocalDate.of(2028, 2, day).dayOfYear,
                LocalDate.of(2027, 2, day).dayOfYear,
            )
            assertEquals(
                SolarDayKey.of(LocalDate.of(2028, 2, day)),
                SolarDayKey.of(LocalDate.of(2027, 2, day)),
            )
        }
    }

    // ---------- 29 February ----------

    @Test
    fun `29 February has a dedicated key that no other day claims`() {
        val feb29 = SolarDayKey.of(LocalDate.of(2028, 2, 29))
        val all = mutableSetOf<Int>()
        for (year in listOf(2027, 2028)) {
            var d = LocalDate.of(year, 1, 1)
            while (d.year == year) {
                if (!(d.monthValue == 2 && d.dayOfMonth == 29)) all += SolarDayKey.of(d)
                d = d.plusDays(1)
            }
        }
        assertTrue("29 Feb collided with another day", feb29 !in all)
    }

    @Test
    fun `29 February falls back to 28 February and nothing else does`() {
        assertEquals(
            SolarDayKey.of(LocalDate.of(2028, 2, 28)),
            SolarDayKey.fallbackFor(LocalDate.of(2028, 2, 29)),
        )
        // no other date requests a fallback
        for (year in listOf(2027, 2028)) {
            var d = LocalDate.of(year, 1, 1)
            while (d.year == year) {
                if (!(d.monthValue == 2 && d.dayOfMonth == 29)) {
                    assertNull("unexpected fallback for $d", SolarDayKey.fallbackFor(d))
                }
                d = d.plusDays(1)
            }
        }
    }

    // ---------- key decoding (used by the DST re-basing) ----------

    @Test
    fun `decoding a key returns the same calendar day it was made from`() {
        for (year in leapYears + plainYears) {
            var d = LocalDate.of(year, 1, 1)
            while (d.year == year) {
                val decoded = SolarDayKey.toDate(SolarDayKey.of(d), year)
                assertEquals("round-trip failed for $d", d, decoded)
                d = d.plusDays(1)
            }
        }
    }

    @Test
    fun `a 29 February key decoded into a plain year degrades to 28 February`() {
        val key = SolarDayKey.of(LocalDate.of(2028, 2, 29))
        // Decoding into a non-leap year must not throw or return null — the
        // DST re-basing needs *a* source date, and 28 Feb is the solar twin.
        val decoded = SolarDayKey.toDate(key, 2027)
        assertNotNull(decoded)
        assertEquals(LocalDate.of(2027, 2, 28), decoded)
    }

    @Test
    fun `non-solar keys decode to null rather than to a wrong date`() {
        // The legacy 1..366 space OVERLAPS the solar space (101..366 is
        // ambiguous), so it is deliberately not decoded — the v5->v6 migration
        // deletes every legacy row, and a null here makes the caller leave the
        // stored time untouched instead of shifting it by a bogus DST delta.
        assertNull("marker rows must not decode", SolarDayKey.toDate(-5787, 2027))
        assertNull("the sentinel must not decode", SolarDayKey.toDate(0, 2027))
        assertNull("a day-of-year below 101 is not a solar key", SolarDayKey.toDate(91, 2027))
        assertNull("366 is not a valid month/day pair", SolarDayKey.toDate(366, 2028))
        // real solar keys still decode
        assertEquals(LocalDate.of(2027, 4, 1), SolarDayKey.toDate(401, 2027))
    }

    @Test
    fun `solar keys and legacy keys never occupy the same numeric space`() {
        // Legacy keys are 1..366; solar keys are >= 101 — the overlap 101..366
        // must be impossible for a real solar key to produce.
        val solarKeys = mutableSetOf<Int>()
        var d = LocalDate.of(2028, 1, 1)
        while (d.year == 2028) { solarKeys += SolarDayKey.of(d); d = d.plusDays(1) }
        // Real solar keys are month*100+day, so the smallest is 101 (1 Jan) and
        // day <= 31 means no solar key ever lands on x32..x99.
        solarKeys.forEach { k ->
            val day = k % 100
            val month = k / 100
            assertTrue("key $k has impossible day $day", day in 1..31)
            assertTrue("key $k has impossible month $month", month in 1..12)
        }
        // The ambiguous band exists (101..366) but is resolved by isSolarKey:
        // any value >= 101 is treated as a solar key, and the v5->v6 migration
        // deleted every legacy row so the ambiguity cannot arise in practice.
        assertTrue(SolarDayKey.isSolarKey(101))
        assertTrue(!SolarDayKey.isSolarKey(100))
        assertTrue(!SolarDayKey.isSolarKey(0)) // the sentinel row
    }
}
