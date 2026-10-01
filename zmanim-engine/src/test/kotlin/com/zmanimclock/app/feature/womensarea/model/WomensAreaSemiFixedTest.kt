package com.zmanimclock.app.feature.womensarea.model

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.calendar.model.toLocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * וסת חצי קבוע: no separation day before day N of the count, the עונה
 * בינונית too when N is past 30; a sighting before N shows every day, and
 * three in a row cancel it.
 */
class WomensAreaSemiFixedTest {

    private fun h(year: Int, month: Int, day: Int): LocalDate = JewishDate(year, month, day).toLocalDate()

    // 1 Nissan 5786 → 27 Nissan: haflaga 27. From 27 Nissan (Nissan has 30):
    // 23 Iyar = day 27 (haflaga), 26 Iyar = day 30 (עונה בינונית), 27 Iyar = day 31 (יום החודש).
    private val prev = h(5786, 1, 1)
    private val start = h(5786, 1, 27)
    private fun prediction(minDay: Int?) =
        WomensAreaCalculator.predict(start, Onah.DAY, prev).copy(semiFixedMinDay = minDay)

    @Test
    fun `the worked example's day numbers`() {
        val p = prediction(null)
        assertEquals(27, p.haflagaInterval)
        assertEquals(h(5786, 2, 23), p.haflaga)
        assertEquals(h(5786, 2, 26), p.onahBeinonit)
        assertEquals(h(5786, 2, 27), p.yomHachodesh)
        assertEquals(3, p.prishaDays.size)
        assertTrue(p.hiddenDays.isEmpty())
    }

    @Test
    fun `only the days before N are hidden`() {
        val p = prediction(28)
        assertEquals(listOf(VesetKind.HAFLAGA), p.hiddenDays.map { it.kind })
        assertEquals(listOf(VesetKind.ONAH_BEINONIT, VesetKind.YOM_HACHODESH), p.prishaDays.map { it.kind })
        assertEquals(3, p.allPrishaDays.size)
    }

    @Test
    fun `day N itself is shown - N of 30 keeps the onah beinonit`() {
        val p = prediction(30)
        assertTrue(p.prishaDays.any { it.kind == VesetKind.ONAH_BEINONIT })
        assertEquals(listOf(VesetKind.HAFLAGA), p.hiddenDays.map { it.kind })
    }

    @Test
    fun `past day 30 the onah beinonit is hidden too`() {
        val p = prediction(31)
        assertEquals(listOf(VesetKind.HAFLAGA, VesetKind.ONAH_BEINONIT), p.hiddenDays.map { it.kind })
        assertEquals(listOf(VesetKind.YOM_HACHODESH), p.prishaDays.map { it.kind })
    }

    @Test
    fun `everything hidden leaves an empty list and the count still runs to 30`() {
        val p = prediction(40)
        assertTrue(p.prishaDays.isEmpty())
        assertEquals(30, p.lastCountedDay)
        val markers = WomensAreaMarkers.build(start, Onah.DAY, prev, null, semiFixedMinDay = 40)
        assertTrue(markers.values.all { it.prisha.isEmpty() })
        assertEquals(30, markers.values.mapNotNull { it.countDayNumber }.max())
    }

    @Test
    fun `carried-over days are hidden by the same rule`() {
        // ה׳ חשון → ו׳ כסלו (32) → ד׳ טבת (29), all at night: יוה״ח מראייה קודמת on ו׳ טבת
        // (day 3) and הפלגה שלא נעקרה 32 on ו׳ שבט (day 32).
        val vs = listOf(VesetRecord(1, h(5787, 8, 5), Onah.NIGHT), VesetRecord(2, h(5787, 9, 6), Onah.NIGHT))
        val cur = h(5787, 10, 4)
        val p = WomensAreaCalculator.predictWithHistory(cur, Onah.NIGHT, vs).copy(semiFixedMinDay = 26)
        assertTrue(p.hiddenDays.any { it.kind == VesetKind.YOM_HACHODESH_PREVIOUS })
        assertTrue(p.prishaDays.any { it.kind == VesetKind.HAFLAGA_NOT_UPROOTED })
    }

    @Test
    fun `the markers leave out the hidden days`() {
        val markers = WomensAreaMarkers.build(start, Onah.DAY, prev, null, semiFixedMinDay = 28)
        assertTrue(markers[h(5786, 2, 23)]?.prisha.isNullOrEmpty())
        assertEquals(VesetKind.ONAH_BEINONIT, markers.getValue(h(5786, 2, 26)).prisha.single().kind)
    }

    // ------------------------------------------------------- contradiction

    private val since = LocalDate.of(2020, 1, 1)
    private fun vesets(first: LocalDate, gaps: List<Int>) = gaps.runningFold(first) { d, g -> d.plusDays((g - 1).toLong()) }

    @Test
    fun `a veset on day N or later does not contradict`() {
        val s = WomensAreaSemiFixed.status(vesets(LocalDate.of(2026, 1, 1), listOf(28, 26)), SemiFixedVeset(26, since))
        assertFalse(s.latestContradicts)
        assertEquals(26, s.latestInterval)
        assertEquals(0, s.consecutiveContradictions)
    }

    @Test
    fun `one before day N contradicts - this cycle shows every day`() {
        val sf = SemiFixedVeset(26, since)
        val s = WomensAreaSemiFixed.status(vesets(LocalDate.of(2026, 1, 1), listOf(28, 29, 24)), sf)
        assertTrue(s.latestContradicts)
        assertEquals(24, s.latestInterval)
        assertEquals(1, s.consecutiveContradictions)
        assertFalse(s.shouldCancel)
        assertNull(WomensAreaSemiFixed.minDayFor(sf, s.latestContradicts))
        assertEquals(26, WomensAreaSemiFixed.minDayFor(sf, contradicted = false))
    }

    @Test
    fun `a sighting back on time ends the run`() {
        val s = WomensAreaSemiFixed.status(vesets(LocalDate.of(2026, 1, 1), listOf(24, 25, 27)), SemiFixedVeset(26, since))
        assertFalse(s.latestContradicts)
        assertEquals(0, s.consecutiveContradictions)
    }

    @Test
    fun `three in a row cancel it`() {
        val s = WomensAreaSemiFixed.status(vesets(LocalDate.of(2026, 1, 1), listOf(28, 24, 25, 23)), SemiFixedVeset(26, since))
        assertEquals(3, s.consecutiveContradictions)
        assertTrue(s.shouldCancel)
    }

    @Test
    fun `vesets from before the setting never count`() {
        val dates = vesets(LocalDate.of(2026, 1, 1), listOf(24, 25, 23))
        // Set after the third veset: only the last one can contradict.
        val s = WomensAreaSemiFixed.status(dates, SemiFixedVeset(26, dates[2].plusDays(1)))
        assertEquals(1, s.consecutiveContradictions)
        assertFalse(s.shouldCancel)
        // Set after all of them: nothing contradicts.
        val none = WomensAreaSemiFixed.status(dates, SemiFixedVeset(26, dates.last().plusDays(1)))
        assertFalse(none.latestContradicts)
    }

    @Test
    fun `the first veset, with nothing to count from, does not contradict`() {
        val s = WomensAreaSemiFixed.status(listOf(LocalDate.of(2026, 1, 1)), SemiFixedVeset(26, since))
        assertFalse(s.latestContradicts)
        assertNull(s.latestInterval)
    }

    // ------------------------------------------------------------ texts

    @Test
    fun `the method text names the onah beinonit only past day 30`() {
        assertTrue(WomensAreaLabels.semiFixedMethod(31).joinToString().contains("העונה הבינונית"))
        assertFalse(WomensAreaLabels.semiFixedMethod(26).joinToString().contains("העונה הבינונית"))
        assertTrue(WomensAreaLabels.semiFixedMethod(26)[0].contains("לפני יום 26"))
    }

    @Test
    fun `no text ever calls it a veset kavua`() {
        val all = WomensAreaLabels.semiFixedMethod(31) +
            WomensAreaLabels.semiFixedContradiction(24, 26, 1) +
            WomensAreaLabels.semiFixedCancelled(26)
        assertTrue(all.none { it.contains("וסת קבוע") || it.contains("ווסת קבוע") })
        assertEquals(
            "הראייה האחרונה הגיעה ביום 24 — לפני יום 26 שהוגדר כוסת חצי קבוע. בחודש זה מוצגים כל ימי הפרישה. יש לשאול רב. (ראייה סותרת 1 מתוך 3 ברצף)",
            WomensAreaLabels.semiFixedContradiction(24, 26, 1),
        )
    }
}
