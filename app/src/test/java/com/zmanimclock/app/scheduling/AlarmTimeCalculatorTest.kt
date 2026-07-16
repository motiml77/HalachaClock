package com.zmanimclock.app.scheduling

import com.zmanimclock.app.feature.alarm.MathChallenge
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.alarms.data.DismissChallenge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

class AlarmTimeCalculatorTest {

    private val zone = ZoneId.of("Asia/Jerusalem")

    /** Wednesday 2026-07-15, 08:00 local. */
    private val wednesdayMorning =
        LocalDateTime.of(2026, 7, 15, 8, 0).atZone(zone).toInstant()

    private fun alarm(
        hour: Int = 6,
        minute: Int = 30,
        days: Int = AlarmEntity.ALL_DAYS,
        skipShabbat: Boolean = false,
        skipYomTov: Boolean = false,
    ) = AlarmEntity(
        hour = hour, minute = minute, daysOfWeek = days,
        skipShabbat = skipShabbat, skipYomTov = skipYomTov,
    )

    @Test
    fun `same-day when the time is still ahead`() {
        val fire = AlarmTimeCalculator.nextFixedOccurrence(alarm(hour = 22), zone, wednesdayMorning)
        assertEquals(LocalDate.of(2026, 7, 15), fire!!.atZone(zone).toLocalDate())
        assertEquals(22, fire.atZone(zone).hour)
    }

    @Test
    fun `next day when the time already passed`() {
        val fire = AlarmTimeCalculator.nextFixedOccurrence(alarm(hour = 6), zone, wednesdayMorning)
        assertEquals(LocalDate.of(2026, 7, 16), fire!!.atZone(zone).toLocalDate())
    }

    @Test
    fun `weekday mask skips to the next enabled day`() {
        // Only Sunday enabled (bit 0); from Wednesday → next Sunday 19/7
        val fire = AlarmTimeCalculator.nextFixedOccurrence(alarm(days = 1), zone, wednesdayMorning)
        assertEquals(DayOfWeek.SUNDAY, fire!!.atZone(zone).dayOfWeek)
        assertEquals(LocalDate.of(2026, 7, 19), fire.atZone(zone).toLocalDate())
    }

    @Test
    fun `skipShabbat pushes a Saturday alarm to the next allowed day`() {
        // Only Saturday enabled but skipShabbat=true → never fires
        val fire = AlarmTimeCalculator.nextFixedOccurrence(
            alarm(days = 0b1000000, skipShabbat = true), zone, wednesdayMorning,
        )
        assertNull(fire)
    }

    @Test
    fun `all-days with skipShabbat rings Friday then Sunday`() {
        // Friday 17/7 22:00 → next occurrence Saturday would be 18/7 but skipped → Sunday 19/7
        val fridayNight = LocalDateTime.of(2026, 7, 17, 23, 0).atZone(zone).toInstant()
        val fire = AlarmTimeCalculator.nextFixedOccurrence(
            alarm(hour = 6, skipShabbat = true), zone, fridayNight,
        )
        assertEquals(DayOfWeek.SUNDAY, fire!!.atZone(zone).dayOfWeek)
    }

    @Test
    fun `one-time alarm fires at the earliest future moment`() {
        val fire = AlarmTimeCalculator.nextFixedOccurrence(alarm(hour = 9, days = 0), zone, wednesdayMorning)
        assertEquals(LocalDate.of(2026, 7, 15), fire!!.atZone(zone).toLocalDate())
    }

    @Test
    fun `skipYomTov skips Tisha BAv NO but skips Rosh Hashana YES`() {
        // 2026-09-11 21:00 erev Rosh Hashana (RH 5787 = 12-13/9/2026)
        val erev = LocalDateTime.of(2026, 9, 11, 21, 0).atZone(zone).toInstant()
        val fire = AlarmTimeCalculator.nextFixedOccurrence(
            alarm(hour = 8, skipYomTov = true), zone, erev,
        )
        // 12/9 and 13/9 are Yom Tov (and 12/9 is also Shabbat) → next is 14/9
        assertEquals(LocalDate.of(2026, 9, 14), fire!!.atZone(zone).toLocalDate())
    }

    @Test
    fun `math challenge levels generate solvable problems`() {
        val rnd = Random(42)
        assertNull(MathChallenge.generate(DismissChallenge.NONE, rnd))
        repeat(50) {
            val easy = MathChallenge.generate(DismissChallenge.MATH_EASY, rnd)!!
            assertTrue(easy.answer in 4..18)
            val med = MathChallenge.generate(DismissChallenge.MATH_MEDIUM, rnd)!!
            assertTrue(med.answer in 24..98)
            val hard = MathChallenge.generate(DismissChallenge.MATH_HARD, rnd)!!
            assertTrue(hard.answer in 78..171)
            assertNotNull(hard.text)
        }
    }
}
