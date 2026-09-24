package com.zmanimclock.app.feature.womensarea.model

import com.zmanimclock.app.feature.calendar.model.HebrewMonthSequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Pure date arithmetic — no halachic content is asserted here, only that the
 * three formulas and the 7-day count compute the dates they claim to.
 *
 * yomHachodesh anchors reuse facts already verified elsewhere in this project
 * against Hebcal, rather than hand-deriving fresh ones: 15 Nissan 5786 =
 * Wed 2026-04-02 (see OmerCountTest), and Rosh Hashana 5787 = 12-13/9/2026
 * (see AlarmTimeCalculatorTest).
 */
class WomensAreaCalculatorTest {

    @Test
    fun `onahBeinonit is exactly 30 days later, regardless of which Hebrew months it crosses`() {
        assertEquals(LocalDate.of(2026, 5, 2), WomensAreaCalculator.onahBeinonit(LocalDate.of(2026, 4, 2)))
        // Crosses a Cheshvan/Kislev-length year boundary — still plain +30,
        // since a civil day is the same length regardless of which Hebrew
        // month it falls in.
        assertEquals(LocalDate.of(2026, 11, 20), WomensAreaCalculator.onahBeinonit(LocalDate.of(2026, 10, 21)))
    }

    @Test
    fun `haflaga projects the previous gap forward, or is null with no previous entry`() {
        val start = LocalDate.of(2026, 5, 20)
        assertEquals(LocalDate.of(2026, 6, 17), WomensAreaCalculator.haflaga(start, LocalDate.of(2026, 4, 22))) // 28-day gap
        assertEquals(LocalDate.of(2026, 5, 25), WomensAreaCalculator.haflaga(start, LocalDate.of(2026, 5, 15))) // 5-day gap
        assertNull(WomensAreaCalculator.haflaga(start, previousStart = null))
    }

    @Test
    fun `yomHachodesh is the same Hebrew day-of-month, next Hebrew month`() {
        // 15 Nissan 5786 = 2026-04-02 -> 15 Iyar 5786 (Nissan has 30 days, no clamp needed)
        assertEquals(LocalDate.of(2026, 5, 2), WomensAreaCalculator.yomHachodesh(LocalDate.of(2026, 4, 2)))
    }

    @Test
    fun `yomHachodesh clamps to month-end when the next month is shorter`() {
        // 30 Nissan 5786 (month end) = 2026-04-17 -> Iyar has only 29 days,
        // so this clamps to 29 Iyar, not a nonexistent 30 Iyar.
        val thirtyNissan = LocalDate.of(2026, 4, 17)
        assertEquals(LocalDate.of(2026, 5, 16), WomensAreaCalculator.yomHachodesh(thirtyNissan))
    }

    @Test
    fun `yomHachodesh rolls the Hebrew year over from Elul to Tishrei`() {
        // 15 Elul 5786 = 2026-08-28 -> 15 Tishrei 5787 = 2026-09-26 (RH 5787
        // is 2026-09-12/13, an already-verified anchor in this project).
        val fifteenElul = LocalDate.of(2026, 8, 28)
        assertEquals(LocalDate.of(2026, 9, 26), WomensAreaCalculator.yomHachodesh(fifteenElul))
    }

    @Test
    fun `yomHachodesh returns null past HebrewMonthSequence's own supported range`() {
        // The very last month in the whole sequence, by construction — no
        // guessed date, just HebrewMonthSequence's own documented boundary.
        val lastMonth = HebrewMonthSequence.refAt(HebrewMonthSequence.size - 1)
        assertNull(WomensAreaCalculator.yomHachodesh(lastMonth.lastDay))
    }

    @Test
    fun `predict bundles all three, correctly nulling haflaga with no previous entry`() {
        val start = LocalDate.of(2026, 4, 2)
        val withPrevious = WomensAreaCalculator.predict(start, LocalDate.of(2026, 3, 5))
        assertEquals(start, withPrevious.sourceStart)
        assertEquals(LocalDate.of(2026, 5, 2), withPrevious.onahBeinonit)
        assertEquals(LocalDate.of(2026, 4, 30), withPrevious.haflaga) // 28-day gap projected forward
        assertEquals(LocalDate.of(2026, 5, 2), withPrevious.yomHachodesh)

        val withoutPrevious = WomensAreaCalculator.predict(start, previousStart = null)
        assertNull(withoutPrevious.haflaga)
    }

    @Test
    fun `the clean-day count is exactly 7 consecutive days, 1-indexed`() {
        val firstCleanDay = LocalDate.of(2026, 4, 10)
        val dates = WomensAreaCalculator.cleanDayDates(firstCleanDay)

        assertEquals(7, dates.size)
        assertEquals(firstCleanDay, dates.first())
        assertEquals(LocalDate.of(2026, 4, 16), dates.last())

        assertEquals(1, WomensAreaCalculator.cleanDayNumber(firstCleanDay, firstCleanDay))
        assertEquals(7, WomensAreaCalculator.cleanDayNumber(firstCleanDay, LocalDate.of(2026, 4, 16)))
        assertNull(WomensAreaCalculator.cleanDayNumber(firstCleanDay, firstCleanDay.minusDays(1)))
        assertNull(WomensAreaCalculator.cleanDayNumber(firstCleanDay, LocalDate.of(2026, 4, 17)))
    }
}
