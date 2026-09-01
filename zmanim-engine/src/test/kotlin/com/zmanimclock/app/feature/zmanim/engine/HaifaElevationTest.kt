package com.zmanimclock.app.feature.zmanim.engine

import com.zmanimclock.app.feature.zmanim.format.asZmanTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * חיפה is the one city whose stored elevation is NOT the SRTM reading at its
 * stored coordinate, and this test exists so nobody "fixes" that back.
 *
 * WHY IT IS SPECIAL. Every other locality takes the elevation sampled at the
 * coordinate we hold for it, which is a fair description of a town that sits
 * on one piece of ground. Haifa does not: the municipality runs from the port
 * at sea level up Har HaCarmel, and the coordinate we hold lands in the lower
 * city. Sampling an 11x11 grid across the municipal area put the high ground
 * at 318 m, roughly 4 km from that coordinate — so the centroid understates
 * this city by a quarter of a kilometre, which no other city on our list does.
 *
 * WHY 318 AND NOT THE PEAK. Both authorities say to take the high point of the
 * city — Rav Ovadia's instruction as reported by the לוח אור החיים team ("the
 * highest point in the city"), and yeshiva.org.il's own published principles
 * ("המיקום בעיר הוא הגבוה בעיר"). 318 m is the highest DEM sample inside the
 * built-up municipal area, not the highest point on the Carmel ridge, which
 * continues outside the city.
 *
 * WHY THE EXACT NUMBER BARELY MATTERS. Measured across the range, every value
 * from 250 m to 350 m produces the same displayed שקיעה in summer, winter and
 * around the solstice — the horizon dip flattens out. So this is a correction
 * of a category error (wrong part of the city), not a fitted constant.
 *
 * A ONE-OFF, DELIBERATELY. A general "take the maximum within N km" rule was
 * measured against 59 towns and rejected: it improved mean error against
 * yeshiva.org.il by six seconds while pushing two to four more towns onto the
 * LATE side of the reference, which for הדלקת נרות is the lenient direction.
 */
class HaifaElevationTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val engine = MaranZmanimEngine()

    /** Coordinates and elevation exactly as cities.json carries them. */
    private val haifa = EngineLocation("חיפה", 32.8130, 34.9993, 318.0, "Asia/Jerusalem")

    @Test
    fun `Haifa uses the Carmel, not the port`() {
        val lowerCity = engine.calculate(haifa.copy(elevationMeters = 67.0), DATE)
        val carmel = engine.calculate(haifa, DATE)
        assertEquals("19:06", lowerCity.shkia!!.asZmanTime(zone))
        assertEquals("19:07", carmel.shkia!!.asZmanTime(zone))
    }

    /**
     * The correction must not be a knife edge. If a future DEM refresh moves
     * the sample by tens of metres the displayed time must not move with it.
     */
    @Test
    fun `anything from 250m to 350m gives the same displayed shkia`() {
        val times = listOf(250.0, 280.0, 300.0, 318.0, 350.0)
            .map { engine.calculate(haifa.copy(elevationMeters = it), DATE).shkia!!.asZmanTime(zone) }
        assertEquals(
            "the displayed time must be flat across the plausible range, got $times",
            1,
            times.toSet().size,
        )
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 9, 1)
    }
}
