package com.zmanimclock.app.feature.zmanim.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fast-day detection across THREE civil years, verified against Hebcal's
 * Israel calendar (fetched 2026-07-21). Covers the calendar quirks:
 *  - 2027 has NO Asara B'Tevet (10 Tevet 5787 fell in Dec 2026);
 *  - 2028 has TWO (January 5788 + December 5789);
 *  - deferral rules (fasts on Shabbat observed Sunday, Esther on Thursday)
 *    are exercised implicitly — the observed dates below already include them.
 * No dates are hardcoded in production code — everything derives from the
 * Hebrew calendar, so any future year works the same way.
 */
class FastDaysTest {

    private val zone = ZoneId.of("Asia/Jerusalem")

    private val expected = mapOf(
        // 2026
        LocalDate.of(2026, 3, 2) to "תענית אסתר",
        LocalDate.of(2026, 7, 2) to "צום י\"ז בתמוז",
        LocalDate.of(2026, 7, 23) to "תשעה באב",
        LocalDate.of(2026, 9, 14) to "צום גדליה",
        LocalDate.of(2026, 9, 21) to "יום הכיפורים",
        LocalDate.of(2026, 12, 20) to "צום עשרה בטבת",
        // 2027 — no Asara B'Tevet this civil year
        LocalDate.of(2027, 3, 22) to "תענית אסתר",
        LocalDate.of(2027, 7, 22) to "צום י\"ז בתמוז",
        LocalDate.of(2027, 8, 12) to "תשעה באב",
        LocalDate.of(2027, 10, 4) to "צום גדליה",
        LocalDate.of(2027, 10, 11) to "יום הכיפורים",
        // 2028 — two Asara B'Tevet
        LocalDate.of(2028, 1, 9) to "צום עשרה בטבת",
        LocalDate.of(2028, 3, 9) to "תענית אסתר",
        LocalDate.of(2028, 7, 11) to "צום י\"ז בתמוז",
        LocalDate.of(2028, 8, 1) to "תשעה באב",
        LocalDate.of(2028, 9, 24) to "צום גדליה",
        LocalDate.of(2028, 9, 30) to "יום הכיפורים",
        LocalDate.of(2028, 12, 28) to "צום עשרה בטבת",
    )

    @Test
    fun `every fast in 2026-2028 is detected on exactly the Hebcal dates`() {
        val found = mutableMapOf<LocalDate, String>()
        var d = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2028, 12, 31)
        while (!d.isAfter(end)) {
            FastDays.fastOn(d, zone)?.let { found[d] = it.name }
            d = d.plusDays(1)
        }
        assertEquals(expected, found)
    }

    @Test
    fun `25-hour fasts are flagged to start the evening before`() {
        val tishaBav = FastDays.fastOn(LocalDate.of(2026, 7, 23), zone)!!
        val yomKippur = FastDays.fastOn(LocalDate.of(2026, 9, 21), zone)!!
        val minorFast = FastDays.fastOn(LocalDate.of(2026, 7, 2), zone)!!
        assertEquals(true, tishaBav.startsEveningBefore)
        assertEquals(false, tishaBav.endsLikeShabbat)
        assertEquals(true, yomKippur.startsEveningBefore)
        assertEquals(true, yomKippur.endsLikeShabbat)
        assertEquals(false, minorFast.startsEveningBefore)
    }
}
