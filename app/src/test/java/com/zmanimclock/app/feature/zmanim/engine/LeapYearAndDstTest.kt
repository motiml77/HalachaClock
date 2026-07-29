package com.zmanimclock.app.feature.zmanim.engine

import com.zmanimclock.app.feature.chaitables.data.SolarDayKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Leap years and DST transitions — the two calendar hazards that silently
 * shift halachic times by a day or an hour.
 *
 * Israel's DST runs from the Friday before the last Sunday of March to the
 * last Sunday of October, so the transition dates move every year.
 */
class LeapYearAndDstTest {

    private val engine = MaranZmanimEngine()
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val jerusalem = EngineLocation("ירושלים", 31.778, 35.235, 0.0, "Asia/Jerusalem")

    // ---------- leap-year cache keying ----------

    @Test
    fun `solar day key is stable across leap and non-leap years`() {
        // The visible-sunrise table is a SOLAR almanac: 1 April has the same
        // sunrise every year. Keying it by raw dayOfYear breaks that, because
        // 1 April is day 91 in a leap year and day 92 otherwise — so after
        // February every cached row is read one day off in half the years.
        val leap = LocalDate.of(2028, 4, 1)     // 2028 is a leap year
        val nonLeap = LocalDate.of(2027, 4, 1)
        assertNotEquals(
            "precondition: raw dayOfYear really does differ",
            leap.dayOfYear, nonLeap.dayOfYear,
        )
        assertEquals(
            "same calendar day must map to the same key in any year",
            SolarDayKey.of(nonLeap), SolarDayKey.of(leap),
        )
    }

    @Test
    fun `solar day key handles 29 February without colliding`() {
        val feb28 = SolarDayKey.of(LocalDate.of(2028, 2, 28))
        val feb29 = SolarDayKey.of(LocalDate.of(2028, 2, 29))
        val mar1Leap = SolarDayKey.of(LocalDate.of(2028, 3, 1))
        val mar1Plain = SolarDayKey.of(LocalDate.of(2027, 3, 1))

        // 29 Feb gets its own slot (it must not read 1 March's sunrise)…
        assertNotEquals(feb28, feb29)
        assertNotEquals(feb29, mar1Leap)
        // …and 1 March is the same key whether or not the year was leap.
        assertEquals(mar1Plain, mar1Leap)
    }

    @Test
    fun `every day of a leap year maps to a distinct key`() {
        val keys = mutableSetOf<Int>()
        var d = LocalDate.of(2028, 1, 1)
        while (d.year == 2028) {
            assertTrue("duplicate key for $d", keys.add(SolarDayKey.of(d)))
            d = d.plusDays(1)
        }
        assertEquals(366, keys.size)
    }

    // ---------- DST transitions ----------

    /** Israel: DST starts the Friday before the last Sunday of March. */
    private fun dstStart(year: Int): LocalDate {
        var d = LocalDate.of(year, 3, 31)
        while (d.dayOfWeek != java.time.DayOfWeek.SUNDAY) d = d.minusDays(1)
        return d.minusDays(2) // the Friday before
    }

    /** Israel: DST ends the last Sunday of October. */
    private fun dstEnd(year: Int): LocalDate {
        var d = LocalDate.of(year, 10, 31)
        while (d.dayOfWeek != java.time.DayOfWeek.SUNDAY) d = d.minusDays(1)
        return d
    }

    @Test
    fun `spring-forward day still produces a coherent, correctly ordered day`() {
        for (year in 2026..2030) {
            val day = engine.calculate(jerusalem, dstStart(year))
            assertOrdered(day, "spring forward $year")
            // The clock loses an hour, but the SOLAR day does not: sunrise to
            // sunset must stay a normal ~12h, never ~11h or ~13h.
            val len = Duration.between(day.hanetzMishor, day.shkia)
            assertTrue(
                "day length ${len.toMinutes()}min on spring-forward $year",
                len.toMinutes() in 660..780,
            )
        }
    }

    @Test
    fun `fall-back day still produces a coherent, correctly ordered day`() {
        for (year in 2026..2030) {
            val day = engine.calculate(jerusalem, dstEnd(year))
            assertOrdered(day, "fall back $year")
            val len = Duration.between(day.hanetzMishor, day.shkia)
            assertTrue(
                "day length ${len.toMinutes()}min on fall-back $year",
                len.toMinutes() in 600..720,
            )
        }
    }

    @Test
    fun `times either side of a DST switch move by roughly an hour, not two`() {
        // Wall-clock sunrise jumps ~1h at the switch. Anything near 0 or near
        // 2 hours would mean the offset was applied twice or not at all.
        val start = dstStart(2027)
        val before = engine.calculate(jerusalem, start.minusDays(1))
        val after = engine.calculate(jerusalem, start)

        val beforeLocal = before.hanetzMishor!!.atZone(zone).toLocalTime()
        val afterLocal = after.hanetzMishor!!.atZone(zone).toLocalTime()
        val jumpMinutes = Duration.between(beforeLocal, afterLocal).toMinutes()
        assertTrue(
            "wall-clock sunrise jumped $jumpMinutes min across spring forward",
            jumpMinutes in 50..70,
        )

        // The underlying instants are only a day apart, unaffected by the shift
        val solarDelta = Duration.between(before.hanetzMishor, after.hanetzMishor)
        assertTrue(
            "solar delta was ${solarDelta.toMinutes()} min",
            solarDelta.toMinutes() in 1380..1440, // 23h–24h
        )
    }

    @Test
    fun `chatzot stays near local noon on both DST boundaries every year`() {
        for (year in 2026..2030) {
            for (date in listOf(dstStart(year), dstEnd(year))) {
                val chatzot = engine.calculate(jerusalem, date).chatzot!!
                    .atZone(zone).toLocalTime()
                val minutesFromNoon = kotlin.math.abs(
                    chatzot.toSecondOfDay() / 60 - 12 * 60
                )
                assertTrue(
                    "chatzot $chatzot on $date is ${minutesFromNoon}min from noon",
                    minutesFromNoon <= 75,
                )
            }
        }
    }

    @Test
    fun `29 February produces a full valid set of zmanim`() {
        val day = engine.calculate(jerusalem, LocalDate.of(2028, 2, 29))
        assertOrdered(day, "29 Feb 2028")
        assertTrue(day.shaahZmanisGra!! > 0)
    }

    private fun assertOrdered(day: DayZmanim, label: String) {
        val order = listOfNotNull(
            day.alotHashachar, day.misheyakir60, day.hanetzMishor,
            day.sofZmanShmaGra, day.sofZmanTfilaGra, day.chatzot,
            day.minchaGedola, day.minchaKetana, day.shkia,
            day.tzeitHakochavim, day.tzeitLechumra, day.tzeitShabbat,
        )
        assertEquals("$label: zmanim out of chronological order", order.sorted(), order)
    }
}
