package com.zmanimclock.app.feature.womensarea.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WomensAreaMarkersTest {

    // 15 Nissan 5786; the previous veset 29 days earlier (inclusive) -> haflaga on 30.4.
    private val veset = LocalDate.of(2026, 4, 2)
    private val previous = LocalDate.of(2026, 3, 5)

    @Test
    fun `every day from the veset to the last separation day is numbered, and nothing else is`() {
        val markers = WomensAreaMarkers.build(veset, Onah.DAY, previous, latestFirstCleanDay = null)
        // Yom hachodesh (15 Iyar) is day 31, the latest of the three.
        (1..31).forEach { n -> assertEquals(n, markers[veset.plusDays((n - 1).toLong())]?.countDayNumber) }
        assertNull(markers[veset.minusDays(1)])
        assertNull(markers[veset.plusDays(31)])
    }

    @Test
    fun `the veset day carries its onah, and each separation day its kind`() {
        val markers = WomensAreaMarkers.build(veset, Onah.NIGHT, previous, latestFirstCleanDay = null)
        assertTrue(markers.getValue(veset).isVesetDay)
        assertEquals(Onah.NIGHT, markers.getValue(veset).vesetOnah)

        assertEquals(listOf(VesetKind.HAFLAGA), markers.getValue(LocalDate.of(2026, 4, 30)).prisha.map { it.kind })
        assertEquals(listOf(VesetKind.ONAH_BEINONIT), markers.getValue(LocalDate.of(2026, 5, 1)).prisha.map { it.kind })
        assertEquals(listOf(VesetKind.YOM_HACHODESH), markers.getValue(LocalDate.of(2026, 5, 2)).prisha.map { it.kind })
        assertTrue(markers.values.flatMap { it.prisha }.all { it.onah == Onah.NIGHT })
    }

    @Test
    fun `two kinds on one day both appear on that day`() {
        // 1 Iyar 5786 = 2026-04-18; Iyar has 29 days, so day 30 IS 1 Sivan = yom hachodesh.
        val oneIyar = LocalDate.of(2026, 4, 18)
        val markers = WomensAreaMarkers.build(oneIyar, Onah.DAY, previousVeset = null, latestFirstCleanDay = null)
        val day30 = oneIyar.plusDays(29)
        assertEquals(
            listOf(VesetKind.ONAH_BEINONIT, VesetKind.YOM_HACHODESH),
            markers.getValue(day30).prisha.map { it.kind },
        )
        assertEquals(30, markers.getValue(day30).countDayNumber)
    }

    @Test
    fun `clean days are numbered 1 to 7 alongside the veset count`() {
        val firstClean = veset.plusDays(5)
        val markers = WomensAreaMarkers.build(veset, Onah.DAY, null, firstClean)
        assertEquals(1, markers.getValue(firstClean).cleanDayNumber)
        assertEquals(6, markers.getValue(firstClean).countDayNumber)
        assertEquals(7, markers.getValue(firstClean.plusDays(6)).cleanDayNumber)
        assertNull(markers.getValue(firstClean.plusDays(7)).cleanDayNumber)
    }
}
