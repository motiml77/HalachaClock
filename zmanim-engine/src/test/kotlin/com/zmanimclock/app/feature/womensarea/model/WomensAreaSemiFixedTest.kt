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

    // ------------------------------------------------------- the rule over time

    private val since = LocalDate.of(2020, 1, 1)
    private val sf = SemiFixedVeset(26, since)
    private fun vesets(gaps: List<Int>, first: LocalDate = LocalDate.of(2026, 1, 1)) =
        gaps.runningFold(first) { d, g -> d.plusDays((g - 1).toLong()) }

    @Test
    fun `on day N or later it stands and hides`() {
        val s = WomensAreaSemiFixed.status(vesets(listOf(28, 26)), sf)
        assertEquals(SemiFixedMode.STANDS, s.mode)
        assertFalse(s.latestContradicts)
        assertEquals(26, s.latestInterval)
        assertEquals(26, WomensAreaSemiFixed.minDayFor(sf, s))
        assertNull(s.transition)
    }

    @Test
    fun `one sighting before day N shows every day that cycle`() {
        val s = WomensAreaSemiFixed.status(vesets(listOf(28, 29, 24)), sf)
        assertEquals(SemiFixedMode.STANDS, s.mode)
        assertTrue(s.latestContradicts)
        assertEquals(24, s.latestInterval)
        assertEquals(1, s.run)
        assertNull(WomensAreaSemiFixed.minDayFor(sf, s))
    }

    @Test
    fun `back on time the next cycle - it hides again, onah beinonit too past 30`() {
        val sf33 = SemiFixedVeset(33, since)
        val s = WomensAreaSemiFixed.status(vesets(listOf(34, 28, 35)), sf33)
        assertFalse(s.latestContradicts)
        assertEquals(0, s.run)
        assertEquals(33, WomensAreaSemiFixed.minDayFor(sf33, s))
        val p = WomensAreaCalculator.predict(start, Onah.DAY, prev).copy(semiFixedMinDay = 33)
        assertTrue(p.hiddenDays.any { it.kind == VesetKind.ONAH_BEINONIT })
    }

    @Test
    fun `three in a row uproot it - every day shown`() {
        val dates = vesets(listOf(28, 24, 25, 23))
        val s = WomensAreaSemiFixed.status(dates, sf)
        assertEquals(SemiFixedMode.UPROOTED, s.mode)
        assertEquals(SemiFixedTransition.Uprooted(dates.last()), s.transition)
        assertNull(WomensAreaSemiFixed.minDayFor(sf, s))
        // Saved as uprooted, the same vesets find nothing new.
        val saved = WomensAreaSemiFixed.after(sf, s.transition!!)
        val again = WomensAreaSemiFixed.status(dates, saved)
        assertEquals(SemiFixedMode.UPROOTED, again.mode)
        assertNull(again.transition)
        assertEquals(0, again.run)
    }

    @Test
    fun `uprooted, on time once or twice is not enough - a break starts the count again`() {
        val first = vesets(listOf(24, 25, 23)) // uprooted at the 4th veset
        val saved = WomensAreaSemiFixed.after(sf, SemiFixedTransition.Uprooted(first.last()))
        val more = vesets(listOf(27, 28, 22, 30), first.last()).drop(1)
        val s = WomensAreaSemiFixed.status(first + more, saved)
        assertEquals(SemiFixedMode.UPROOTED, s.mode)
        assertEquals(1, s.run) // 27, 28, then 22 broke it, then 30
        assertNull(WomensAreaSemiFixed.minDayFor(saved, s))
    }

    @Test
    fun `uprooted, three in a row on time establish it again`() {
        val first = vesets(listOf(24, 25, 23))
        val saved = WomensAreaSemiFixed.after(sf, SemiFixedTransition.Uprooted(first.last()))
        val more = vesets(listOf(27, 26, 30), first.last()).drop(1)
        val all = first + more
        val s = WomensAreaSemiFixed.status(all, saved)
        assertEquals(SemiFixedMode.STANDS, s.mode)
        assertEquals(SemiFixedTransition.Reestablished(all.last()), s.transition)
        assertEquals(26, WomensAreaSemiFixed.minDayFor(saved, s))
        // Saved as standing again: the old contradictions no longer count.
        val restored = WomensAreaSemiFixed.after(saved, s.transition!!)
        assertNull(restored.uprootedAt)
        val again = WomensAreaSemiFixed.status(all, restored)
        assertEquals(SemiFixedMode.STANDS, again.mode)
        assertNull(again.transition)
        assertFalse(again.latestContradicts)
    }

    @Test
    fun `vesets from before the setting never count`() {
        val dates = vesets(listOf(24, 25, 23))
        val s = WomensAreaSemiFixed.status(dates, SemiFixedVeset(26, dates[2].plusDays(1)))
        assertEquals(1, s.run)
        assertEquals(SemiFixedMode.STANDS, s.mode)
        val none = WomensAreaSemiFixed.status(dates, SemiFixedVeset(26, dates.last().plusDays(1)))
        assertFalse(none.latestContradicts)
    }

    @Test
    fun `the first veset, with nothing to count from, does not contradict`() {
        val s = WomensAreaSemiFixed.status(listOf(LocalDate.of(2026, 1, 1)), sf)
        assertFalse(s.latestContradicts)
        assertNull(s.latestInterval)
    }

    @Test
    fun `day or night makes no difference - only the Hebrew day count`() {
        // Seen Tuesday evening after shkia: the Hebrew day is Wednesday's, and
        // that is the date recorded. 33 Hebrew days from the previous veset,
        // whichever onah either was in.
        val previous = LocalDate.of(2026, 3, 3)          // Tuesday (by day)
        val tuesdayNight = LocalDate.of(2026, 4, 4)       // ליל … recorded on its Hebrew day
        assertEquals(33, WomensAreaCalculator.haflagaInterval(tuesdayNight, previous))
        assertEquals(false, WomensAreaSemiFixed.isBeforeMinDay(tuesdayNight, previous, 33))
        assertEquals(true, WomensAreaSemiFixed.isBeforeMinDay(tuesdayNight.minusDays(1), previous, 33))
        // And what it hides is by day number alone, the same in both onot.
        val day = WomensAreaCalculator.predict(start, Onah.DAY, prev).copy(semiFixedMinDay = 28)
        val night = WomensAreaCalculator.predict(start, Onah.NIGHT, prev).copy(semiFixedMinDay = 28)
        assertEquals(day.hiddenDays.map { it.kind to it.date }, night.hiddenDays.map { it.kind to it.date })
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
            WomensAreaLabels.semiFixedUprooted(26) +
            WomensAreaLabels.semiFixedReestablished(26) +
            WomensAreaLabels.semiFixedUprootedStatus(26, 1)
        assertTrue(all.none { it.contains("וסת קבוע") || it.contains("ווסת קבוע") })
        assertEquals(
            "הראייה האחרונה הגיעה ביום 24 — לפני יום 26 שהוגדר כוסת חצי קבוע. בחודש זה מוצגים כל ימי הפרישה. יש לשאול רב. (ראייה סותרת 1 מתוך 3 ברצף)",
            WomensAreaLabels.semiFixedContradiction(24, 26, 1),
        )
    }
}
