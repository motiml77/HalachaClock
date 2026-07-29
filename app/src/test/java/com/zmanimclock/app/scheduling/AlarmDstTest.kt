package com.zmanimclock.app.scheduling

import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * What a user actually cares about across a DST switch: a 06:30 alarm must
 * still ring at 06:30 wall-clock, and must not fire twice, get skipped, or
 * land an hour off.
 *
 * Israel switches on the Friday before the last Sunday of March (forward)
 * and the last Sunday of October (back), both at 02:00 local.
 */
class AlarmDstTest {

    private val zone = ZoneId.of("Asia/Jerusalem")

    private fun dstStart(year: Int): LocalDate {
        var d = LocalDate.of(year, 3, 31)
        while (d.dayOfWeek != java.time.DayOfWeek.SUNDAY) d = d.minusDays(1)
        return d.minusDays(2)
    }

    private fun dstEnd(year: Int): LocalDate {
        var d = LocalDate.of(year, 10, 31)
        while (d.dayOfWeek != java.time.DayOfWeek.SUNDAY) d = d.minusDays(1)
        return d
    }

    private fun dailyAlarm(hour: Int, minute: Int) = AlarmEntity(
        id = 1,
        hour = hour,
        minute = minute,
        daysOfWeek = AlarmEntity.ALL_DAYS,
    )

    @Test
    fun `daily alarm keeps its wall-clock time across both DST switches`() {
        val alarm = dailyAlarm(6, 30)
        for (year in 2026..2030) {
            for (switch in listOf(dstStart(year), dstEnd(year))) {
                // "now" = just after midnight on the switch day
                val now = switch.atTime(0, 5).atZone(zone).toInstant()
                val fire = AlarmTimeCalculator.nextFixedOccurrence(alarm, zone, now)
                assertNotNull("no occurrence on $switch", fire)
                val local = fire!!.atZone(zone)
                assertEquals("wrong date on $switch", switch, local.toLocalDate())
                assertEquals(
                    "alarm moved off 06:30 on $switch",
                    LocalTime.of(6, 30), local.toLocalTime(),
                )
            }
        }
    }

    @Test
    fun `alarm inside the skipped hour still fires on the spring-forward day`() {
        // 02:00–02:59 does not exist on the spring-forward day. java.time
        // shifts such a local time forward by the gap rather than dropping it,
        // so the alarm must still produce an occurrence that day — never null,
        // never silently a day late.
        val alarm = dailyAlarm(2, 30)
        for (year in 2026..2030) {
            val switch = dstStart(year)
            val now = switch.atTime(0, 5).atZone(zone).toInstant()
            val fire = AlarmTimeCalculator.nextFixedOccurrence(alarm, zone, now)
            assertNotNull("02:30 alarm vanished on $switch", fire)
            val local = fire!!.atZone(zone)
            assertEquals("02:30 alarm slipped off $switch", switch, local.toLocalDate())
            // It lands at 03:30 (the gap is 02:00→03:00) — still that morning
            assertTrue(
                "02:30 alarm landed at ${local.toLocalTime()} on $switch",
                local.toLocalTime() in LocalTime.of(2, 0)..LocalTime.of(3, 59),
            )
        }
    }

    @Test
    fun `alarm inside the repeated hour fires once on the fall-back day`() {
        // 01:00–01:59 happens twice on the fall-back day. The alarm must
        // resolve to a single instant, and the following day's occurrence must
        // be ~24-25h later — not the same morning again.
        val alarm = dailyAlarm(1, 30)
        for (year in 2026..2030) {
            val switch = dstEnd(year)
            val now = switch.atTime(0, 5).atZone(zone).toInstant()
            val first = AlarmTimeCalculator.nextFixedOccurrence(alarm, zone, now)
            assertNotNull("01:30 alarm vanished on $switch", first)
            assertEquals(switch, first!!.atZone(zone).toLocalDate())

            val next = AlarmTimeCalculator.nextFixedOccurrence(
                alarm, zone, first.plusSeconds(60),
            )
            assertNotNull(next)
            assertEquals(
                "did not advance to the next day",
                switch.plusDays(1), next!!.atZone(zone).toLocalDate(),
            )
            val gap = Duration.between(first, next)
            assertTrue(
                "gap across fall-back was ${gap.toMinutes()} min",
                gap.toHours() in 23..26,
            )
        }
    }

    @Test
    fun `consecutive daily occurrences never repeat or skip a day around a switch`() {
        val alarm = dailyAlarm(6, 30)
        for (year in 2026..2027) {
            for (switch in listOf(dstStart(year), dstEnd(year))) {
                var cursor = switch.minusDays(2).atTime(0, 5).atZone(zone).toInstant()
                val seen = mutableListOf<LocalDate>()
                repeat(5) {
                    val fire = AlarmTimeCalculator.nextFixedOccurrence(alarm, zone, cursor)!!
                    seen += fire.atZone(zone).toLocalDate()
                    cursor = fire.plusSeconds(60)
                }
                assertEquals(
                    "dates around $switch were $seen",
                    (0..4).map { switch.minusDays(2).plusDays(it.toLong()) },
                    seen,
                )
            }
        }
    }

    @Test
    fun `daily alarm still lands correctly on 29 February`() {
        val alarm = dailyAlarm(6, 30)
        val feb29 = LocalDate.of(2028, 2, 29)
        val now = feb29.atTime(0, 5).atZone(zone).toInstant()
        val fire = AlarmTimeCalculator.nextFixedOccurrence(alarm, zone, now)!!
        assertEquals(feb29, fire.atZone(zone).toLocalDate())
        assertEquals(LocalTime.of(6, 30), fire.atZone(zone).toLocalTime())

        // …and rolls into 1 March, not 1 February or 29 Feb again
        val next = AlarmTimeCalculator.nextFixedOccurrence(alarm, zone, fire.plusSeconds(60))!!
        assertEquals(LocalDate.of(2028, 3, 1), next.atZone(zone).toLocalDate())
    }
}
