package com.zmanimclock.app.scheduling

import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * "It rings on days I did not choose."
 *
 * The day filtering itself was never broken — every non-empty mask is honoured
 * exactly. The failure was the EMPTY mask: 0 doubles as the one-time marker,
 * and isEnabledOn answers true for every weekday when it sees 0. A user who
 * unticked every day meaning "never" got an alarm that rang on whatever day
 * came next.
 *
 * These tests pin both halves: every real selection is respected, and 0 keeps
 * its one-time meaning while the day circles can no longer produce it by
 * accident (that guard lives in DaysSelector; here the CONTRACT is pinned so
 * nobody re-widens isEnabledOn instead).
 */
class DayMaskTest {

    private val zone = ZoneId.of("Asia/Jerusalem")

    /** Sunday 9 Aug 2026 through Saturday 15 Aug 2026. */
    private val week = (9..15).map { LocalDate.of(2026, 8, it) }

    private fun alarm(mask: Int) = AlarmEntity(id = 1, hour = 7, minute = 0, daysOfWeek = mask)

    private fun firingDays(mask: Int): List<DayOfWeek> =
        week.filter { AlarmTimeCalculator.isDayAllowed(alarm(mask), it, zone) }.map { it.dayOfWeek }

    // -------------------------------------------------- real selections

    @Test
    fun `a single day fires on that day and no other`() {
        assertEquals(listOf(DayOfWeek.SUNDAY), firingDays(0b0000001))
        assertEquals(listOf(DayOfWeek.WEDNESDAY), firingDays(0b0001000))
        assertEquals(listOf(DayOfWeek.SATURDAY), firingDays(AlarmEntity.SATURDAY_ONLY))
        assertEquals(listOf(DayOfWeek.FRIDAY), firingDays(AlarmEntity.FRIDAY_ONLY))
    }

    @Test
    fun `a scattered selection fires on exactly those days`() {
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            firingDays(0b0001010),
        )
    }

    @Test
    fun `the presets cover exactly what they claim`() {
        assertEquals(7, firingDays(AlarmEntity.ALL_DAYS).size)
        assertFalse(firingDays(AlarmEntity.SUNDAY_TO_FRIDAY).contains(DayOfWeek.SATURDAY))
        assertEquals(6, firingDays(AlarmEntity.SUNDAY_TO_FRIDAY).size)
        assertEquals(5, firingDays(AlarmEntity.SUNDAY_TO_THURSDAY).size)
    }

    @Test
    fun `every bit maps to the weekday its letter shows`() {
        // The UI draws א ב ג ד ה ו ש left-to-right at indices 0..6, so bit i
        // must be that weekday. A mismatch here would silently shift every
        // alarm by a day, which is the bug this whole file exists for.
        val expected = listOf(
            DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
        )
        expected.forEachIndexed { index, day ->
            assertEquals("bit $index", listOf(day), firingDays(1 shl index))
        }
    }

    @Test
    fun `unselecting a day actually stops it firing`() {
        val withWednesday = 0b0001010            // Mon + Wed
        val withoutWednesday = withWednesday xor 0b0001000
        assertTrue(firingDays(withWednesday).contains(DayOfWeek.WEDNESDAY))
        assertFalse(firingDays(withoutWednesday).contains(DayOfWeek.WEDNESDAY))
        assertEquals(listOf(DayOfWeek.MONDAY), firingDays(withoutWednesday))
    }

    // ------------------------------------------------------ the empty mask

    @Test
    fun `an empty mask means ONE-TIME, and that is why it must never be reached by accident`() {
        // Documenting the trap rather than pretending it is not there: 0 is
        // allowed on every weekday BY DESIGN, because a one-time alarm fires
        // at the next occurrence whenever that is. The protection is that the
        // day circles refuse to write 0 — see DaysSelector.
        assertTrue(alarm(0).isOneTime)
        assertEquals(7, firingDays(0).size)
    }

    @Test
    fun `a one-time alarm fires once, at the next occurrence of its time`() {
        val a = alarm(0)
        val wednesday0900 = LocalDate.of(2026, 8, 12).atTime(9, 0).atZone(zone).toInstant()
        val fire = AlarmTimeCalculator.nextFixedOccurrence(a, zone, wednesday0900)
        assertEquals(
            LocalDate.of(2026, 8, 13).atTime(7, 0).atZone(zone).toInstant(),
            fire,
        )
    }

    // --------------------------------------- the guard the selector applies

    @Test
    fun `clearing the last remaining day is refused instead of silently becoming daily`() {
        // Mirrors DaysSelector's rule: a toggle that would empty the mask is
        // dropped, so "I unticked everything" can never turn into "rings every
        // day until it fires once".
        fun toggle(mask: Int, index: Int): Int {
            val next = mask xor (1 shl index)
            return if (next == 0) mask else next
        }
        assertEquals(0b0000001, toggle(0b0000001, 0))          // last day: unchanged
        assertEquals(0b0000010, toggle(0b0000011, 0))          // one of two: removed
        assertEquals(0b0000011, toggle(0b0000010, 0))          // adding is untouched
    }
}
