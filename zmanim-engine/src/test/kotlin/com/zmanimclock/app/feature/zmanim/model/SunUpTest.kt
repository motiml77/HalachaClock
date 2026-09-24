package com.zmanimclock.app.feature.zmanim.model

import com.zmanimclock.app.feature.zmanim.engine.EngineLocation
import com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The hero card's sun / moon picture switches exactly at the day's own הנץ
 * and שקיעה — never on a fixed clock hour, which would show a sun an hour
 * after dark in winter and a moon an hour before it in summer.
 */
class SunUpTest {

    private val jerusalem = EngineLocation("ירושלים", 31.778, 35.235, 0.0, "Asia/Jerusalem")
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val date = LocalDate.of(2026, 12, 21)
    private val day = MaranZmanimEngine().calculate(jerusalem, date)

    private fun at(hour: Int, minute: Int) = date.atTime(hour, minute).atZone(zone).toInstant()

    @Test
    fun `midday is day and the small hours are night`() {
        assertTrue(day.isSunUpAt(at(12, 0)))
        assertFalse(day.isSunUpAt(at(2, 0)))
        assertFalse(day.isSunUpAt(at(23, 0)))
    }

    @Test
    fun `the switch is the netz itself, to the second`() {
        val netz = day.hanetzMishor!!
        assertFalse(day.isSunUpAt(netz.minusSeconds(1)))
        assertTrue(day.isSunUpAt(netz))
    }

    @Test
    fun `the switch back is the shkia itself, to the second`() {
        val shkia = day.shkia!!
        assertTrue(day.isSunUpAt(shkia.minusSeconds(1)))
        assertFalse(day.isSunUpAt(shkia))
    }

    @Test
    fun `a winter evening at 17_00 is already night`() {
        // Jerusalem's midwinter shkia is ~16:40 — an "18:00 is evening" rule
        // would still be drawing a sun here.
        assertFalse(day.isSunUpAt(at(17, 0)))
    }
}
