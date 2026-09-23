package com.zmanimclock.app.scheduling

import com.zmanimclock.app.feature.alarm.MathChallenge
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.alarms.data.DismissChallenge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        omerMode: Boolean = false,
    ) = AlarmEntity(
        hour = hour, minute = minute, daysOfWeek = days,
        skipShabbat = skipShabbat, skipYomTov = skipYomTov,
        omerMode = omerMode,
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
    fun `omerMode allows only the 49 nights of the omer, other alarms are unaffected`() {
        // 15 Nissan 5786 (Pesach I) = Thu 2026-04-02 (Hebcal), so the first
        // Sefirah night — the alert firing at tzeit that evening, opening 16
        // Nissan — is fireDate 2026-04-02; the evening before is still 15
        // Nissan / Pesach I, not yet the omer.
        val firstOmerNight = LocalDate.of(2026, 4, 2)
        val dayBeforeOmerBegins = LocalDate.of(2026, 4, 1)
        val deepSummer = LocalDate.of(2026, 7, 15) // wednesdayMorning's date

        assertTrue(AlarmTimeCalculator.isDayAllowed(alarm(omerMode = true), firstOmerNight, zone))
        assertFalse(AlarmTimeCalculator.isDayAllowed(alarm(omerMode = true), dayBeforeOmerBegins, zone))
        assertFalse(AlarmTimeCalculator.isDayAllowed(alarm(omerMode = true), deepSummer, zone))

        // A plain (non-omer) alarm never consults OmerCount at all.
        assertTrue(AlarmTimeCalculator.isDayAllowed(alarm(omerMode = false), dayBeforeOmerBegins, zone))
        assertTrue(AlarmTimeCalculator.isDayAllowed(alarm(omerMode = false), deepSummer, zone))
    }

    @Test
    fun `omerMode never rings on a Friday night, that is already Shabbat`() {
        // 2026-04-03 is a Friday inside the omer window (omer day 2). Firing
        // at tzeit that evening enters Shabbat, so it is skipped — even
        // though it IS a real omer night.
        val fridayInOmer = LocalDate.of(2026, 4, 3)
        assertEquals(DayOfWeek.FRIDAY, fridayInOmer.dayOfWeek)
        assertFalse(AlarmTimeCalculator.isDayAllowed(alarm(omerMode = true), fridayInOmer, zone))

        // Saturday tzeit is motzaei-Shabbat — an omer night that IS allowed.
        val saturdayInOmer = LocalDate.of(2026, 4, 4)
        assertEquals(DayOfWeek.SATURDAY, saturdayInOmer.dayOfWeek)
        assertTrue(AlarmTimeCalculator.isDayAllowed(alarm(omerMode = true), saturdayInOmer, zone))
    }

    @Test
    fun `omerMode skips Pesach VII eve but still rings the final count on erev Shavuot`() {
        // Omer day 6 fires 2026-04-07 (Tue) at tzeit, entering 21 Nissan =
        // Pesach VII, a Yom Tov in Israel → skipped though it is a real
        // omer night. This is the ONE Yom-Tov collision inside the count.
        val erevPesachVii = LocalDate.of(2026, 4, 7)
        assertFalse(AlarmTimeCalculator.isDayAllowed(alarm(omerMode = true), erevPesachVii, zone))

        // The final count, omer day 49, fires 2026-05-20 at tzeit, entering
        // 5 Sivan = EREV Shavuot — a weekday. Shavuot itself (6 Sivan) only
        // begins the FOLLOWING night, so this count is NOT on Yom Tov and DOES
        // ring: the culminating count keeps its alert.
        val erevShavuot = LocalDate.of(2026, 5, 20)
        assertTrue(AlarmTimeCalculator.isDayAllowed(alarm(omerMode = true), erevShavuot, zone))
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
