package com.zmanimclock.app.feature.womensarea.model

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.calendar.model.toLocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WomensAreaHistoryTest {

    private fun h(year: Int, month: Int, day: Int): LocalDate = JewishDate(year, month, day).toLocalDate()
    private var nextId = 1L
    private fun veset(date: LocalDate, onah: Onah? = Onah.DAY) = VesetRecord(nextId++, date, onah)

    /** Vesets every [gap] days (inclusive count) starting at [first]. */
    private fun everyN(first: LocalDate, gap: Int, n: Int, onah: Onah = Onah.DAY) =
        (0 until n).map { veset(first.plusDays((it * (gap - 1)).toLong()), onah) }

    @Test
    fun `cycles are newest first, each with the haflaga that led to it and its own hefsek`() {
        val a = veset(h(5786, 1, 1)); val b = veset(h(5786, 2, 1)); val c = veset(h(5786, 3, 1))
        val hefA = HefsekRecord(100, h(5786, 1, 6)); val hefB = HefsekRecord(101, h(5786, 2, 7))
        val cycles = WomensAreaHistory.cycles(listOf(b, a, c), listOf(hefB, hefA))
        assertEquals(listOf(c, b, a), cycles.map { it.veset })
        assertEquals(listOf(30, 31, null), cycles.map { it.haflagaInterval }) // Iyar 29, Nissan 30
        assertEquals(listOf(null, hefB, hefA), cycles.map { it.hefsek })
        assertEquals(listOf(1, 1, 1), cycles.map { it.hebrewDayOfMonth })
        // What was computed on each: its own prediction, from its own previous veset.
        assertEquals(h(5786, 3, 30), cycles[0].prediction.haflaga)
    }

    @Test
    fun `only the last 6 vesets are kept, and hefseks older than the oldest kept go with them`() {
        val vs = everyN(h(5786, 7, 1), gap = 29, n = 8)
        val oldHefsek = HefsekRecord(200, vs[1].date.plusDays(5)) // belongs to the 2nd-oldest cycle
        val keptHefsek = HefsekRecord(201, vs[2].date.plusDays(5))
        val prune = WomensAreaHistory.idsToPrune(vs.shuffled(), listOf(oldHefsek, keptHefsek))
        assertEquals(setOf(vs[0].id, vs[1].id, 200L), prune)
        assertEquals(emptySet<Long>(), WomensAreaHistory.idsToPrune(vs.take(6), listOf(oldHefsek)))
    }

    @Test
    fun `three equal haflagot in a row, all in one onah, are reported`() {
        val same = WomensAreaHistory.cycles(everyN(h(5786, 7, 1), 29, 5, Onah.NIGHT), emptyList())
        assertEquals(listOf(HistoryPattern.SameHaflaga(29, 4, Onah.NIGHT)), WomensAreaHistory.patterns(same).filterIsInstance<HistoryPattern.SameHaflaga>())
        assertEquals("הפלגה של 29 יום חזרה 4 פעמים ברצף — כולן בלילה", WomensAreaLabels.patternText(HistoryPattern.SameHaflaga(29, 4, Onah.NIGHT)))

        // Two equal then a different one: not reported.
        val vs = listOf(veset(h(5786, 7, 1)), veset(h(5786, 7, 25)), veset(h(5786, 8, 23)), veset(h(5786, 9, 22)))
        val cycles = WomensAreaHistory.cycles(vs, emptyList())
        assertEquals(listOf(29, 29, 25, null), cycles.map { it.haflagaInterval })
        assertTrue(WomensAreaHistory.patterns(cycles).none { it is HistoryPattern.SameHaflaga })
    }

    @Test
    fun `equal haflagot across a change of onah are NOT a pattern`() {
        // Four vesets 29 days apart — three equal haflagot — but the oldest was by day.
        val vs = everyN(h(5786, 7, 1), 29, 4, Onah.NIGHT).mapIndexed { i, v -> if (i == 0) v.copy(onah = Onah.DAY) else v }
        val cycles = WomensAreaHistory.cycles(vs, emptyList())
        assertEquals(listOf(29, 29, 29, null), cycles.map { it.haflagaInterval })
        // Only two haflagot have both ends בלילה: not enough.
        assertTrue(WomensAreaHistory.patterns(cycles).isEmpty())

        // Same haflagot, the NEWEST by day: the run stops at once.
        val newestDay = everyN(h(5786, 7, 1), 29, 5, Onah.NIGHT).mapIndexed { i, v -> if (i == 4) v.copy(onah = Onah.DAY) else v }
        assertTrue(WomensAreaHistory.patterns(WomensAreaHistory.cycles(newestDay, emptyList())).isEmpty())

        // A veset with no onah recorded breaks it as well.
        val unknown = everyN(h(5786, 7, 1), 29, 5, Onah.NIGHT).mapIndexed { i, v -> if (i == 2) v.copy(onah = null) else v }
        assertTrue(WomensAreaHistory.patterns(WomensAreaHistory.cycles(unknown, emptyList())).isEmpty())
    }

    @Test
    fun `the same Hebrew date three months running is reported — across a leap year's two Adars`() {
        val vs = listOf(h(5787, 11, 14), h(5787, 12, 14), h(5787, 13, 14), h(5787, 1, 14)).map { veset(it, Onah.DAY) }
        val patterns = WomensAreaHistory.patterns(WomensAreaHistory.cycles(vs, emptyList()))
        assertEquals(HistoryPattern.SameDayOfMonth(14, 4, Onah.DAY), patterns.filterIsInstance<HistoryPattern.SameDayOfMonth>().single())
        assertEquals(
            "הראייה הופיעה 4 חודשים ברצף בי״ד בחודש — כולן ביום",
            WomensAreaLabels.patternText(HistoryPattern.SameDayOfMonth(14, 4, Onah.DAY)),
        )
        // A skipped month breaks it: 14 Shevat, then 14 Adar II.
        val skipped = listOf(h(5787, 10, 14), h(5787, 11, 14), h(5787, 13, 14)).map { veset(it) }
        assertTrue(WomensAreaHistory.patterns(WomensAreaHistory.cycles(skipped, emptyList())).none { it is HistoryPattern.SameDayOfMonth })
    }

    @Test
    fun `the same date with mixed onot is not reported`() {
        val vs = listOf(h(5786, 1, 5), h(5786, 2, 5), h(5786, 3, 5)).mapIndexed { i, d -> veset(d, if (i == 1) Onah.NIGHT else Onah.DAY) }
        assertTrue(WomensAreaHistory.patterns(WomensAreaHistory.cycles(vs, emptyList())).isEmpty())
    }

    @Test
    fun `a steady step between haflagot (dilug), in one onah, is reported`() {
        // Haflagot 28, 29, 30 in time order → newest first 30, 29, 28 → growing by one.
        var d = h(5786, 7, 1)
        val vs = mutableListOf(veset(d))
        for (gap in listOf(28, 29, 30)) { d = d.plusDays((gap - 1).toLong()); vs += veset(d) }
        val p = WomensAreaHistory.patterns(WomensAreaHistory.cycles(vs, emptyList()))
        assertEquals(listOf(HistoryPattern.SteadyHaflagaStep(1, 3, Onah.DAY)), p.filterIsInstance<HistoryPattern.SteadyHaflagaStep>())
        assertEquals(
            "ההפלגות גדלות ביום אחד בכל פעם (3 הפלגות ברצף) — כולן ביום",
            WomensAreaLabels.patternText(p.filterIsInstance<HistoryPattern.SteadyHaflagaStep>().single()),
        )
    }

    @Test
    fun `too little history finds nothing`() {
        assertTrue(WomensAreaHistory.patterns(emptyList()).isEmpty())
        val one = WomensAreaHistory.cycles(listOf(veset(h(5786, 1, 1))), emptyList())
        assertNull(one.single().haflagaInterval)
        assertTrue(WomensAreaHistory.patterns(one).isEmpty())
    }
}
