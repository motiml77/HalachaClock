package com.zmanimclock.app.feature.womensarea.model

import com.zmanimclock.app.feature.calendar.model.HebrewMonthSequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Counting only — no halachic ruling is asserted here, only that each day
 * lands where the count on the luach puts it.
 *
 * Every LocalDate is a Hebrew day (see VesetPrediction). Anchors reuse facts
 * already verified elsewhere in this project against Hebcal: 15 Nissan 5786 =
 * Thu 2026-04-02 (OmerCountTest), and Rosh Hashana 5787 = 12-13/9/2026
 * (AlarmTimeCalculatorTest). Nissan has 30 days, Iyar 29.
 */
class WomensAreaCalculatorTest {

    private val fifteenNissan = LocalDate.of(2026, 4, 2)

    @Test
    fun `onahBeinonit is day 30, counting the veset day itself as day 1`() {
        // 15 Nissan = day 1 ... 30 Nissan = day 16, 1 Iyar = day 17 ... 14 Iyar = day 30.
        assertEquals(LocalDate.of(2026, 5, 1), WomensAreaCalculator.onahBeinonit(fifteenNissan))
        val prediction = WomensAreaCalculator.predict(fifteenNissan, Onah.DAY, previousStart = null)
        assertEquals(30, prediction.countDayNumber(prediction.onahBeinonit))
    }

    @Test
    fun `haflaga counts from the previous veset day as 1 up to this veset's day, then runs that count forward`() {
        val start = LocalDate.of(2026, 5, 20)
        val previous = LocalDate.of(2026, 4, 22)
        // 22.4 = 1 ... 20.5 = 29, so this is a haflaga of 29.
        assertEquals(29, WomensAreaCalculator.haflagaInterval(start, previous))
        // Run forward: 20.5 = 1 ... 17.6 = 29.
        val haflaga = WomensAreaCalculator.haflaga(start, previous)
        assertEquals(LocalDate.of(2026, 6, 17), haflaga)
        val prediction = WomensAreaCalculator.predict(start, Onah.NIGHT, previous)
        assertEquals(29, prediction.countDayNumber(haflaga!!))

        assertNull(WomensAreaCalculator.haflaga(start, previousStart = null))
        assertNull(WomensAreaCalculator.haflagaInterval(start, previousStart = start))
    }

    @Test
    fun `yomHachodesh is the same Hebrew day-of-month, next Hebrew month`() {
        // 15 Nissan -> 15 Iyar. Nissan has 30 days, so this is day 31 of the count.
        val yom = WomensAreaCalculator.yomHachodesh(fifteenNissan)
        assertEquals(LocalDate.of(2026, 5, 2), yom)
        assertEquals(31, WomensAreaCalculator.predict(fifteenNissan, Onah.DAY, null).countDayNumber(yom!!))
    }

    @Test
    fun `a veset on the 30th has no yomHachodesh when the next month has only 29 days`() {
        // 30 Nissan 5786 = 2026-04-17; Iyar has no 30th. Not moved to 29 Iyar.
        val thirtyNissan = LocalDate.of(2026, 4, 17)
        assertNull(WomensAreaCalculator.yomHachodesh(thirtyNissan))
        assertTrue(WomensAreaCalculator.isYomHachodeshMissing(thirtyNissan))
        val prediction = WomensAreaCalculator.predict(thirtyNissan, Onah.DAY, null)
        assertTrue(prediction.yomHachodeshMissing)
        assertEquals(listOf(VesetKind.ONAH_BEINONIT), prediction.prishaDays.map { it.kind })

        assertFalse(WomensAreaCalculator.isYomHachodeshMissing(fifteenNissan))
    }

    @Test
    fun `yomHachodesh rolls the Hebrew year over from Elul to Tishrei`() {
        // 15 Elul 5786 = 2026-08-28 -> 15 Tishrei 5787 = 2026-09-26.
        assertEquals(LocalDate.of(2026, 9, 26), WomensAreaCalculator.yomHachodesh(LocalDate.of(2026, 8, 28)))
    }

    @Test
    fun `yomHachodesh returns null past HebrewMonthSequence's own supported range, without calling it missing`() {
        val lastMonth = HebrewMonthSequence.refAt(HebrewMonthSequence.size - 1)
        assertNull(WomensAreaCalculator.yomHachodesh(lastMonth.lastDay))
        assertFalse(WomensAreaCalculator.isYomHachodeshMissing(lastMonth.lastDay))
    }

    @Test
    fun `every separation day inherits the veset's onah`() {
        val prediction = WomensAreaCalculator.predict(fifteenNissan, Onah.NIGHT, LocalDate.of(2026, 3, 5))
        assertEquals(3, prediction.prishaDays.size)
        assertTrue(prediction.prishaDays.all { it.onah == Onah.NIGHT })
        // In date order: haflaga (29 days from 5.3 -> 30.4), onah beinonit (1.5), yom hachodesh (2.5).
        assertEquals(
            listOf(VesetKind.HAFLAGA, VesetKind.ONAH_BEINONIT, VesetKind.YOM_HACHODESH),
            prediction.prishaDays.map { it.kind },
        )
        assertEquals(LocalDate.of(2026, 4, 30), prediction.haflaga)
    }

    @Test
    fun `the count runs to day 30, or on to a later separation day`() {
        val plain = WomensAreaCalculator.predict(LocalDate.of(2026, 4, 17), Onah.DAY, null)
        assertEquals(30, plain.lastCountedDay)
        assertNull(plain.countDayNumber(LocalDate.of(2026, 4, 16)))
        assertEquals(1, plain.countDayNumber(LocalDate.of(2026, 4, 17)))

        // A 35-day haflaga carries the count past 30.
        val start = LocalDate.of(2026, 5, 20)
        val long = WomensAreaCalculator.predict(start, Onah.DAY, start.minusDays(34))
        assertEquals(35, long.haflagaInterval)
        assertEquals(35, long.lastCountedDay)
    }

    @Test
    fun `a hefsek on Tuesday starts the clean days on Wednesday, and the tevila is after tzeit of the next Tuesday`() {
        // Tuesday 2026-04-07 (20 Nissan), hefsek before shkia.
        val hefsek = LocalDate.of(2026, 4, 7)
        val dates = WomensAreaCalculator.cleanDayDates(hefsek)

        assertEquals(7, dates.size)
        assertEquals(LocalDate.of(2026, 4, 8), dates.first()) // Wednesday = day 1
        assertEquals(LocalDate.of(2026, 4, 14), dates.last()) // the next Tuesday = day 7
        assertEquals(java.time.DayOfWeek.WEDNESDAY, dates.first().dayOfWeek)
        assertEquals(java.time.DayOfWeek.TUESDAY, dates.last().dayOfWeek)

        assertNull(WomensAreaCalculator.cleanDayNumber(hefsek, hefsek))
        assertEquals(1, WomensAreaCalculator.cleanDayNumber(hefsek, LocalDate.of(2026, 4, 8)))
        assertEquals(7, WomensAreaCalculator.cleanDayNumber(hefsek, LocalDate.of(2026, 4, 14)))
        assertNull(WomensAreaCalculator.cleanDayNumber(hefsek, LocalDate.of(2026, 4, 15)))

        // Tevila after tzeit of the 7th day (Tuesday 14.4) = ליל רביעי, the Hebrew day of 15.4.
        assertEquals(LocalDate.of(2026, 4, 14), WomensAreaCalculator.tevilaDay(hefsek))
        assertEquals(LocalDate.of(2026, 4, 15), WomensAreaCalculator.tevilaNight(hefsek))
    }

    @Test
    fun `a hefsek before the 5th day of the count is early, from the 5th on it is not`() {
        // Veset Sunday 5.4.2026 (day 1): Thursday 9.4 is day 5, the earliest.
        val veset = LocalDate.of(2026, 4, 5)
        assertEquals(java.time.DayOfWeek.SUNDAY, veset.dayOfWeek)
        assertEquals(1, WomensAreaCalculator.hefsekDayNumber(veset, veset))
        assertTrue(WomensAreaCalculator.isEarlyHefsek(veset, veset))
        assertEquals(4, WomensAreaCalculator.hefsekDayNumber(veset, LocalDate.of(2026, 4, 8)))
        assertTrue(WomensAreaCalculator.isEarlyHefsek(veset, LocalDate.of(2026, 4, 8))) // Wednesday
        assertEquals(5, WomensAreaCalculator.hefsekDayNumber(veset, LocalDate.of(2026, 4, 9)))
        assertFalse(WomensAreaCalculator.isEarlyHefsek(veset, LocalDate.of(2026, 4, 9))) // Thursday
        assertFalse(WomensAreaCalculator.isEarlyHefsek(veset, LocalDate.of(2026, 4, 20)))
    }

    @Test
    fun `no veset on or before the hefsek means nothing to warn about`() {
        val hefsek = LocalDate.of(2026, 4, 9)
        assertNull(WomensAreaCalculator.hefsekDayNumber(null, hefsek))
        assertFalse(WomensAreaCalculator.isEarlyHefsek(null, hefsek))
        assertNull(WomensAreaCalculator.hefsekDayNumber(hefsek.plusDays(1), hefsek))
        assertFalse(WomensAreaCalculator.isEarlyHefsek(hefsek.plusDays(1), hefsek))
    }

    @Test
    fun `a tevila night on Yom Kippur or Tisha B'Av is flagged, including a deferred 9 Av`() {
        // Tevila night = hefsek + 8. 10 Tishrei 5787 = 21.9.2026 → hefsek 13.9.
        assertEquals(TevilaBlock.YOM_KIPPUR, WomensAreaCalculator.tevilaNightBlock(LocalDate.of(2026, 9, 13)))
        assertNull(WomensAreaCalculator.tevilaNightBlock(LocalDate.of(2026, 9, 12))) // ליל ט׳ תשרי
        assertNull(WomensAreaCalculator.tevilaNightBlock(LocalDate.of(2026, 9, 14))) // ליל י״א תשרי
        // 9 Av 5787 = Thursday 12.8.2027 → hefsek 4.8.
        assertEquals(TevilaBlock.TISHA_BEAV, WomensAreaCalculator.tevilaNightBlock(LocalDate.of(2027, 8, 4)))
        assertNull(WomensAreaCalculator.tevilaNightBlock(LocalDate.of(2027, 8, 5))) // 10 Av, not deferred
        // 5782: 9 Av was Shabbat 6.8.2022, the fast on Sunday 10 Av — both nights flagged, 11 Av not.
        assertEquals(TevilaBlock.TISHA_BEAV, WomensAreaCalculator.tevilaNightBlock(LocalDate.of(2022, 7, 29)))
        assertEquals(TevilaBlock.TISHA_BEAV, WomensAreaCalculator.tevilaNightBlock(LocalDate.of(2022, 7, 30)))
        assertNull(WomensAreaCalculator.tevilaNightBlock(LocalDate.of(2022, 7, 31)))
        assertEquals(
            "ליל הטבילה חל בליל יום הכיפורים — אין טובלים בלילה זה. יש לשאול רב.",
            WomensAreaLabels.tevilaBlockText(TevilaBlock.YOM_KIPPUR),
        )
    }
}
