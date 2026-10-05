package com.zmanimclock.app.feature.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * The bundled city list is zmanim INPUT, so a bad row is a wrong time on every
 * phone and computer that picks it. These pin the properties a row can get
 * wrong without anything crashing: a typo'd time-zone id, coordinates with
 * swapped signs, a duplicated id, a field the hand-rolled parser mangles.
 */
class CityCatalogTest {

    private val cities = CityCatalog.cities
    private val abroad = cities.filter { it.country != "IL" }

    @Test
    fun `ids are unique`() {
        val dupes = cities.groupBy { it.id }.filter { it.value.size > 1 }.keys
        assertTrue("duplicate ids: $dupes", dupes.isEmpty())
    }

    @Test
    fun `every row has usable coordinates, elevation and zone`() {
        for (c in cities) {
            assertTrue("${c.id}: latitude ${c.latitude}", c.latitude in -66.0..66.0)
            assertTrue("${c.id}: longitude ${c.longitude}", c.longitude in -180.0..180.0)
            assertTrue("${c.id}: elevation ${c.elevation}", c.elevation in 0.0..4000.0)
            assertNotNull("${c.id}: zone ${c.timeZoneId}", runCatching { ZoneId.of(c.timeZoneId) }.getOrNull())
            assertTrue("${c.id}: Hebrew name", c.nameHebrew.isNotBlank())
            assertTrue("${c.id}: English name", c.nameEnglish.isNotBlank())
        }
    }

    @Test
    fun `every city abroad carries an English name, a country and a region`() {
        for (c in abroad) {
            assertTrue("${c.id}: English name must be Latin", c.nameEnglish.all { it.code < 0x250 })
            assertEquals("${c.id}: country code", 2, c.country.length)
            assertTrue("${c.id}: region", c.region.isNotBlank())
        }
    }

    /**
     * A city's own zone must be where it actually is: the longitude and the
     * zone's standard offset can disagree by a few hours at most, never by
     * the half-day a wrong-hemisphere paste produces.
     */
    @Test
    fun `zone offset agrees with longitude`() {
        for (c in cities) {
            val standardHours = ZoneId.of(c.timeZoneId).rules.getStandardOffset(java.time.Instant.parse("2027-01-15T12:00:00Z"))
                .totalSeconds / 3600.0
            val solarHours = c.longitude / 15.0
            assertTrue(
                "${c.id}: zone ${c.timeZoneId} (UTC%+.1f) vs longitude ${c.longitude}".format(standardHours),
                kotlin.math.abs(standardHours - solarHours) < 4.0,
            )
        }
    }

    /** The region 'ארה"ב' holds a quote — the parser must not cut it at the escape. */
    @Test
    fun `a region containing a quote parses whole`() {
        assertEquals("ארה\"ב", CityCatalog.byId("new_york")?.region)
    }

    @Test
    fun `search finds a city by its English name`() {
        assertEquals("New York", CityCatalog.search("new york").first().nameEnglish)
        assertFalse(CityCatalog.search("buenos").isEmpty())
    }

    // ---- the cities added for the diaspora -------------------------------------

    /**
     * Anchors read from Hebcal's independent solar calculation for the same
     * point (it prints whole minutes, hence the 60 s tolerance). They pin what
     * a coordinate typo cannot hide from the structural checks above: a wrong
     * hemisphere, a wrong zone, or DST applied on the wrong side of its switch
     * date. Sea level on purpose — that is what the outside source publishes.
     */
    private fun seaLevel(id: String, date: java.time.LocalDate): com.zmanimclock.app.feature.zmanim.engine.DayZmanim {
        val c = CityCatalog.byId(id)!!
        val loc = com.zmanimclock.app.feature.zmanim.engine.EngineLocation(
            c.nameEnglish, c.latitude, c.longitude, 0.0, c.timeZoneId,
        )
        return com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine().calculate(loc, date)
    }

    private fun assertClock(id: String, instant: java.time.Instant?, expected: String) {
        val zone = ZoneId.of(CityCatalog.byId(id)!!.timeZoneId)
        val actual = instant!!.atZone(zone).toLocalTime()
        val want = java.time.LocalTime.parse(expected)
        val off = kotlin.math.abs(java.time.Duration.between(want, actual).seconds)
        assertTrue("$id: expected ~$expected but was $actual", off <= 60)
    }

    @Test
    fun `Boston sunset follows US daylight time`() {
        // 21 Mar 2027 is after the US switch (14 Mar) — EDT.
        assertClock("boston", seaLevel("boston", java.time.LocalDate.of(2027, 3, 21)).shkiaMishor, "18:57")
    }

    @Test
    fun `Marseille sunset is still winter time before the European switch`() {
        // The EU switches on 28 Mar, a week after this date — CET.
        assertClock("marseille", seaLevel("marseille", java.time.LocalDate.of(2027, 3, 21)).shkiaMishor, "18:52")
    }

    @Test
    fun `Rio sunrise is right in the southern winter`() {
        assertClock("rio_de_janeiro", seaLevel("rio_de_janeiro", java.time.LocalDate.of(2027, 6, 21)).hanetzMishor, "06:33")
    }

    @Test
    fun `Moscow sunset is right at high latitude in midwinter`() {
        assertClock("moscow", seaLevel("moscow", java.time.LocalDate.of(2026, 12, 21)).shkiaMishor, "15:58")
    }

    @Test
    fun `Budapest and Kyiv keep their own zones`() {
        assertClock("budapest", seaLevel("budapest", java.time.LocalDate.of(2027, 6, 21)).hanetzMishor, "04:46")
        assertClock("kyiv", seaLevel("kyiv", java.time.LocalDate.of(2027, 10, 12)).shkiaMishor, "18:13")
    }
}
