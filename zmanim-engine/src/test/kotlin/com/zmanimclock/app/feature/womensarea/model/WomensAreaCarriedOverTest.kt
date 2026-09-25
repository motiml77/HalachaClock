package com.zmanimclock.app.feature.womensarea.model

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.calendar.model.toLocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Separation days carried over from EARLIER vesets that have not been
 * uprooted: יום החודש מראייה קודמת, and הפלגה שלא נעקרה (only when every veset
 * involved was in one onah). Hebrew dates, worked by hand in each comment.
 */
class WomensAreaCarriedOverTest {

    private fun h(year: Int, month: Int, day: Int): LocalDate = JewishDate(year, month, day).toLocalDate()
    private var id = 1L
    private fun v(date: LocalDate, onah: Onah?) = VesetRecord(id++, date, onah)

    /** Vesets whose haflagot (inclusive) are [gaps], starting at [first], all in [onah] unless [onot] says otherwise. */
    private fun chain(first: LocalDate, gaps: List<Int>, onah: Onah = Onah.NIGHT, onot: List<Onah?>? = null): List<VesetRecord> {
        val dates = gaps.runningFold(first) { d, g -> d.plusDays((g - 1).toLong()) }
        return dates.mapIndexed { i, d -> v(d, if (onot != null) onot[i] else onah) }
    }

    private fun carried(vs: List<VesetRecord>): List<PrishaDay> {
        val cur = vs.last()
        return WomensAreaCalculator.carriedOver(cur.date, cur.onah, vs.dropLast(1))
    }

    // ------------------------------------------------ יום החודש מראייה קודמת

    @Test
    fun `an earlier veset's yom hachodesh still ahead is carried, in ITS onah`() {
        // 15 Nissan (day) → 10 Iyar (night). 15 Iyar, from Nissan, has not come yet.
        val prev = v(h(5786, 1, 15), Onah.DAY)
        val out = WomensAreaCalculator.carriedOver(h(5786, 2, 10), Onah.NIGHT, listOf(prev))
        val day = out.single { it.kind == VesetKind.YOM_HACHODESH_PREVIOUS }
        assertEquals(h(5786, 2, 15), day.date)
        assertEquals(Onah.DAY, day.onah)
        assertEquals(6, day.dayNumber) // 10 Iyar = 1 … 15 Iyar = 6
        assertEquals(prev.date, day.fromVeset)
        assertEquals("יום החודש מהראייה של ט״ו ניסן · ט״ו אייר", WomensAreaLabels.prishaTitle(day))
    }

    @Test
    fun `one that already passed, or that the new veset fell on, is not carried`() {
        val prev = v(h(5786, 1, 15), Onah.DAY)
        // 20 Iyar: 15 Iyar passed without a sighting — uprooted.
        assertTrue(WomensAreaCalculator.carriedOver(h(5786, 2, 20), Onah.DAY, listOf(prev)).none { it.kind == VesetKind.YOM_HACHODESH_PREVIOUS })
        // 15 Iyar itself: the new veset IS that day.
        assertTrue(WomensAreaCalculator.carriedOver(h(5786, 2, 15), Onah.DAY, listOf(prev)).none { it.kind == VesetKind.YOM_HACHODESH_PREVIOUS })
    }

    @Test
    fun `yom hachodesh of an earlier veset crosses Adar I to Adar II`() {
        // 20 Adar I 5787 → 12 Adar II 5787: 20 Adar II still ahead.
        val out = WomensAreaCalculator.carriedOver(h(5787, 13, 12), Onah.DAY, listOf(v(h(5787, 12, 20), Onah.DAY)))
        assertEquals(h(5787, 13, 20), out.single { it.kind == VesetKind.YOM_HACHODESH_PREVIOUS }.date)
    }

    @Test
    fun `it shows on the calendar, with the whole history`() {
        val prev = v(h(5786, 1, 15), Onah.DAY)
        val cur = h(5786, 2, 10)
        val m = WomensAreaMarkers.build(cur, Onah.NIGHT, prev.date, null, earlierVesets = listOf(prev))
        assertEquals(listOf(VesetKind.YOM_HACHODESH_PREVIOUS), m.getValue(h(5786, 2, 15)).prisha.map { it.kind })
    }

    // ------------------------------------------------ הפלגה שלא נעקרה

    @Test
    fun `a longer earlier haflaga, all in one onah, is carried from the new veset`() {
        // Haflagot 32 then 29, all בלילה → from the latest, day 32 as well as its own 29.
        val vs = chain(h(5786, 7, 1), listOf(32, 29))
        val day = carried(vs).single { it.kind == VesetKind.HAFLAGA_NOT_UPROOTED }
        assertEquals(32, day.interval)
        assertEquals(32, day.dayNumber)
        assertEquals(vs.last().date.plusDays(31), day.date)
        assertEquals(Onah.NIGHT, day.onah)
        assertEquals("הפלגה שלא נעקרה (32 יום) · יום 32", WomensAreaLabels.prishaTitle(day))
    }

    @Test
    fun `a longer haflaga later uproots a shorter one before it`() {
        // 28, 33, 29: 33 is carried; 28 was uprooted (day 28 passed within the 33).
        assertEquals(listOf(33), carried(chain(h(5786, 7, 1), listOf(28, 33, 29))).mapNotNull { it.interval })
        // 32, 30, 29: both 30 and 32 are still standing.
        assertEquals(setOf(30, 32), carried(chain(h(5786, 7, 1), listOf(32, 30, 29))).mapNotNull { it.interval }.toSet())
        // Equal or shorter earlier haflagot are nothing new.
        assertTrue(carried(chain(h(5786, 7, 1), listOf(29, 29))).none { it.kind == VesetKind.HAFLAGA_NOT_UPROOTED })
        assertTrue(carried(chain(h(5786, 7, 1), listOf(27, 29))).none { it.kind == VesetKind.HAFLAGA_NOT_UPROOTED })
    }

    @Test
    fun `not carried unless every veset from its start to the latest is in one onah`() {
        // 32 then 29, but the very first veset was ביום.
        assertTrue(carried(chain(h(5786, 7, 1), listOf(32, 29), onot = listOf(Onah.DAY, Onah.NIGHT, Onah.NIGHT))).none { it.kind == VesetKind.HAFLAGA_NOT_UPROOTED })
        // The middle one ביום.
        assertTrue(carried(chain(h(5786, 7, 1), listOf(32, 29), onot = listOf(Onah.NIGHT, Onah.DAY, Onah.NIGHT))).none { it.kind == VesetKind.HAFLAGA_NOT_UPROOTED })
        // The latest ביום.
        assertTrue(carried(chain(h(5786, 7, 1), listOf(32, 29), onot = listOf(Onah.NIGHT, Onah.NIGHT, Onah.DAY))).none { it.kind == VesetKind.HAFLAGA_NOT_UPROOTED })
        // No onah recorded on one of them.
        assertTrue(carried(chain(h(5786, 7, 1), listOf(32, 29), onot = listOf(null, Onah.NIGHT, Onah.NIGHT))).none { it.kind == VesetKind.HAFLAGA_NOT_UPROOTED })
        // All ביום: carried.
        assertEquals(listOf(32), carried(chain(h(5786, 7, 1), listOf(32, 29), onah = Onah.DAY)).mapNotNull { it.interval })
    }

    @Test
    fun `the full prediction puts carried days alongside its own, and the count runs to them`() {
        val vs = chain(h(5786, 7, 1), listOf(35, 29))
        val cur = vs.last()
        val p = WomensAreaCalculator.predictWithHistory(cur.date, cur.onah, vs.dropLast(1))
        assertEquals(29, p.haflagaInterval)
        assertTrue(p.prishaDays.any { it.kind == VesetKind.HAFLAGA_NOT_UPROOTED && it.dayNumber == 35 })
        assertEquals(35, p.lastCountedDay)
    }
}
