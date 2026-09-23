package com.zmanimclock.app.feature.zmanim.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The 49 nights of the omer, swept across a full civil year rather than
 * hand-picked, so any off-by-one at either end shows up immediately.
 *
 * The single external fact this is anchored to: 15 Nissan 5786 (Pesach I) =
 * Thu 2026-04-02, per Hebcal's Diaspora listing — so the alert firing at
 * tzeit that evening (civil date 2026-04-02) opens 16 Nissan, the first
 * omer night. Everything else — that the run is exactly 49 consecutive
 * civil days from there, counting 1..49 in order — is internal consistency,
 * not a second guessed date.
 */
class OmerCountTest {

    private val zone = ZoneId.of("Asia/Jerusalem")

    @Test
    fun `the omer is exactly 49 consecutive nights starting erev 16 Nissan`() {
        val found = mutableMapOf<LocalDate, Int>()
        var d = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 12, 31)
        while (!d.isAfter(end)) {
            OmerCount.dayOfOmerAtTzeit(d, zone)?.let { found[d] = it }
            d = d.plusDays(1)
        }

        val firstNight = LocalDate.of(2026, 4, 2)
        assertEquals(49, found.size)
        assertEquals(1, found[firstNight])
        (0..48).forEach { offset ->
            assertEquals(offset + 1, found[firstNight.plusDays(offset.toLong())])
        }
    }

    @Test
    fun `season prompt is eligible only on ordinary days of the count`() {
        fun season(month: Int, day: Int) =
            OmerCount.seasonYearOrNull(LocalDate.of(2026, month, day), zone)

        assertEquals(2026, season(4, 3))  // 16 Nissan — first Chol HaMoed omer day
        assertEquals(2026, season(5, 21)) // day 49 (5 Sivan, erev Shavuot) — a weekday
        assertNull(season(4, 2))          // 15 Nissan — Pesach I Yom Tov, before the count
        assertNull(season(4, 8))          // 21 Nissan — Pesach VII Yom Tov
        assertNull(season(4, 4))          // 17 Nissan — a Shabbat inside the omer
        assertNull(season(9, 23))         // deep off-season
    }

    @Test
    fun `countText covers exactly days 1 through 49`() {
        assertNull(OmerCount.countText(0))
        assertEquals("היום יום אחד לעומר", OmerCount.countText(1))
        assertEquals("היום תשעה וארבעים יום, שהם שבעה שבועות לעומר", OmerCount.countText(49))
        assertNull(OmerCount.countText(50))
    }
}
