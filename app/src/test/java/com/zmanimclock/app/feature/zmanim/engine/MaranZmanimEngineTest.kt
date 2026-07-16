package com.zmanimclock.app.feature.zmanim.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Structural verification of the Maran engine: every anchor is checked as a
 * formula relative to the day base. Exact luach values (moreshet-maran.com /
 * royzmanim.com "Chazon Yosef" mode) are verified in the step-6 test suite.
 */
class MaranZmanimEngineTest {

    private val engine = MaranZmanimEngine()

    private val jerusalem = EngineLocation(
        name = "ירושלים",
        latitude = 31.778,
        longitude = 35.235,
        timeZoneId = "Asia/Jerusalem",
    )

    private val testDate: LocalDate = LocalDate.of(2026, 7, 15)

    @Test
    fun `mishor fallback is used when visible sunrise is absent`() {
        val day = engine.calculate(jerusalem, testDate)
        assertNotNull(day.hanetzMishor)
        assertTrue(!day.basedOnVisibleSunrise)
    }

    @Test
    fun `visible sunrise becomes the base of the day`() {
        val mishorDay = engine.calculate(jerusalem, testDate)
        val visible = mishorDay.hanetzMishor!!.plusSeconds(180) // netz 3 min after mishor
        val day = engine.calculate(jerusalem, testDate, visibleSunrise = visible)

        assertTrue(day.basedOnVisibleSunrise)
        assertEquals(visible, day.hanetzVisible)
        // Sof zman shma must move together with the visible netz
        assertEquals(
            Duration.between(mishorDay.sofZmanShmaGra, day.sofZmanShmaGra).seconds > 0,
            true,
        )
    }

    @Test
    fun `shaah zmanit GRA is exactly one twelfth of netz-to-shkia`() {
        val day = engine.calculate(jerusalem, testDate)
        val span = Duration.between(day.hanetzMishor, day.shkia).toMillis()
        assertEquals(span / 12, day.shaahZmanisGra!!)
    }

    @Test
    fun `alot is 72 zmaniyot minutes before the base`() {
        val day = engine.calculate(jerusalem, testDate)
        val expected = day.hanetzMishor!!.minusMillis(day.shaahZmanisGra!! * 72 / 60)
        assertCloseMillis(expected, day.alotHashachar!!)
    }

    @Test
    fun `sof zman shma GRA is base plus 3 shaos`() {
        val day = engine.calculate(jerusalem, testDate)
        val expected = day.hanetzMishor!!.plusMillis(day.shaahZmanisGra!! * 3)
        assertCloseMillis(expected, day.sofZmanShmaGra!!)
    }

    @Test
    fun `chatzot is base plus 6 shaos`() {
        val day = engine.calculate(jerusalem, testDate)
        val expected = day.hanetzMishor!!.plusMillis(day.shaahZmanisGra!! * 6)
        assertCloseMillis(expected, day.chatzot!!)
    }

    @Test
    fun `mincha gedola is at least 30 fixed minutes after chatzot`() {
        val day = engine.calculate(jerusalem, testDate)
        val gap = Duration.between(day.chatzot, day.minchaGedola).toMinutes()
        assertTrue("mincha gedola only $gap min after chatzot", gap >= 30)
    }

    @Test
    fun `tzeit is 13 and a half zmaniyot minutes after shkia`() {
        val day = engine.calculate(jerusalem, testDate)
        val expected = day.shkia!!.plusMillis((day.shaahZmanisGra!! * 13.5 / 60).toLong())
        assertCloseMillis(expected, day.tzeitHakochavim!!)
    }

    @Test
    fun `tzeit shabbat is 40 fixed minutes after shkia`() {
        val day = engine.calculate(jerusalem, testDate)
        assertEquals(
            Duration.ofMinutes(40),
            Duration.between(day.shkia, day.tzeitShabbat),
        )
    }

    @Test
    fun `rabbeinu tam is 72 zmaniyot minutes after shkia`() {
        val day = engine.calculate(jerusalem, testDate)
        val expected = day.shkia!!.plusMillis(day.shaahZmanisGra!! * 72 / 60)
        assertCloseMillis(expected, day.tzeitRabbeinuTam!!)
    }

    @Test
    fun `plag YY is hour and quarter zmaniyot before tzeit`() {
        val day = engine.calculate(jerusalem, testDate)
        val expected = day.tzeitHakochavim!!.minusMillis((day.shaahZmanisGra!! * 1.25).toLong())
        assertCloseMillis(expected, day.plagHaminchaYalkutYosef!!)
    }

    @Test
    fun `daily order is halachically coherent`() {
        val day = engine.calculate(jerusalem, testDate)
        val order = listOfNotNull(
            day.alotHashachar,
            day.misheyakir66,
            day.misheyakir60,
            day.hanetzMishor,
            day.sofZmanShmaMga,
            day.sofZmanShmaGra,
            day.sofZmanTfilaGra,
            day.chatzot,
            day.minchaGedola,
            day.minchaKetana,
            day.plagHaminchaYalkutYosef,
            day.shkia,
            day.tzeitHakochavim,
            day.tzeitShabbat,
            day.tzeitRabbeinuTam,
        )
        val sorted = order.sortedBy(Instant::toEpochMilli)
        assertEquals("zmanim out of order:\n${render(day)}", sorted, order)
    }

    @Test
    fun `sanity - july jerusalem times land in expected local windows`() {
        val day = engine.calculate(jerusalem, testDate)
        val tz = ZoneId.of(jerusalem.timeZoneId)
        val sunriseHour = day.hanetzMishor!!.atZone(tz).hour
        val sunsetHour = day.shkia!!.atZone(tz).hour
        assertTrue("sunrise hour was $sunriseHour", sunriseHour in 5..6)
        assertTrue("sunset hour was $sunsetHour", sunsetHour in 19..20)
    }

    private fun assertCloseMillis(expected: Instant, actual: Instant, toleranceMillis: Long = 10) {
        val diff = Duration.between(expected, actual).abs().toMillis()
        assertTrue("expected $expected but was $actual (diff ${diff}ms)", diff <= toleranceMillis)
    }

    private fun render(day: DayZmanim): String = buildString {
        val tz = ZoneId.of(day.location.timeZoneId)
        fun line(name: String, i: Instant?) =
            appendLine("$name: ${i?.atZone(tz)?.toLocalTime() ?: "--"}")
        line("alot", day.alotHashachar)
        line("misheyakir66", day.misheyakir66)
        line("netz", day.hanetzMishor)
        line("shma gra", day.sofZmanShmaGra)
        line("chatzot", day.chatzot)
        line("mincha gedola", day.minchaGedola)
        line("plag YY", day.plagHaminchaYalkutYosef)
        line("shkia", day.shkia)
        line("tzeit", day.tzeitHakochavim)
    }
}
