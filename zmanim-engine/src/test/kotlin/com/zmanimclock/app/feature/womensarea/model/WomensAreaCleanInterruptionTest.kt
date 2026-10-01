package com.zmanimclock.app.feature.womensarea.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * סתירת נקיים: blood during the clean days voids the count — the old hefsek,
 * clean days and tevila are no longer drawn, and a new hefsek starts afresh.
 * The vesets' days are untouched.
 */
class WomensAreaCleanInterruptionTest {

    private val veset = LocalDate.of(2026, 11, 2)
    private val hefsek = LocalDate.of(2026, 11, 7)     // clean days 8..14, tevila after tzeit of the 14th

    @Test
    fun `only a day after the hefsek, up to the 7th clean day, interrupts`() {
        assertFalse(WomensAreaCalculator.interruptsCleanDays(hefsek, hefsek))
        assertTrue(WomensAreaCalculator.interruptsCleanDays(hefsek, hefsek.plusDays(1)))
        assertTrue(WomensAreaCalculator.interruptsCleanDays(hefsek, hefsek.plusDays(7)))
        assertFalse(WomensAreaCalculator.interruptsCleanDays(hefsek, hefsek.plusDays(8)))
        assertEquals(
            hefsek.plusDays(3),
            WomensAreaCalculator.cleanInterruptionOf(hefsek, listOf(hefsek.plusDays(9), hefsek.plusDays(3), hefsek.minusDays(2))),
        )
        assertNull(WomensAreaCalculator.cleanInterruptionOf(hefsek, listOf(hefsek.minusDays(1))))
    }

    @Test
    fun `interrupted - no hefsek, clean days or tevila are drawn, only that day - the vesets' days stay`() {
        val plain = WomensAreaMarkers.build(veset, Onah.DAY, null, hefsek)
        val cut = WomensAreaMarkers.build(veset, Onah.DAY, null, hefsek, cleanInterruptedOn = hefsek.plusDays(3))
        assertTrue(plain.values.any { it.cleanDayNumber != null })
        assertTrue(cut.values.none { it.cleanDayNumber != null || it.isHefsekDay || it.isTevilaDay })
        assertTrue(cut.getValue(hefsek.plusDays(3)).cleanInterrupted)
        // The vesets' side — the count and every separation day — is exactly as before.
        plain.forEach { (date, m) ->
            assertEquals(m.countDayNumber, cut[date]?.countDayNumber)
            assertEquals(m.prisha, cut[date]?.prisha.orEmpty())
        }
    }

    @Test
    fun `a new hefsek on or after the interruption counts afresh`() {
        val cutDay = hefsek.plusDays(3)
        val newHefsek = cutDay  // the same day, before shkia
        assertNull(WomensAreaCalculator.cleanInterruptionOf(newHefsek, listOf(cutDay)))
        val m = WomensAreaMarkers.build(veset, Onah.DAY, null, newHefsek)
        assertEquals(1, m.getValue(newHefsek.plusDays(1)).cleanDayNumber)
        assertTrue(m.getValue(WomensAreaCalculator.tevilaDay(newHefsek)).isTevilaDay)
    }

    @Test
    fun `the history keeps each cycle's interruptions`() {
        val v = listOf(VesetRecord(1, veset, Onah.DAY), VesetRecord(2, veset.plusDays(29), Onah.DAY))
        val cycles = WomensAreaHistory.cycles(v, listOf(HefsekRecord(3, hefsek)), listOf(hefsek.plusDays(3), veset.plusDays(35)))
        assertEquals(listOf(veset.plusDays(35)), cycles[0].cleanInterruptions)
        assertEquals(listOf(hefsek.plusDays(3)), cycles[1].cleanInterruptions)
    }

    @Test
    fun `the message says the vesets are unchanged`() {
        assertTrue(WomensAreaLabels.cleanInterruptedText(hefsek.plusDays(3)).contains("חישובי הווסתות אינם משתנים"))
    }
}
