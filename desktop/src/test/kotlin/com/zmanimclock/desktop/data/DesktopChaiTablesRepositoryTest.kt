package com.zmanimclock.desktop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DesktopChaiTablesRepositoryTest {

    private val repo = DesktopChaiTablesRepository()
    private val zone = "Asia/Jerusalem"

    @Test
    fun `a bundled city returns the real visible sunrise, not null`() {
        val instant = repo.getVisibleSunrise("ירושלים", LocalDate.of(2026, 9, 14), zone)
        assertNotNull(instant)
        val local = instant!!.atZone(ZoneId.of(zone))
        assertEquals(6, local.hour)
        assertEquals(21, local.minute)
        assertEquals(38, local.second)
    }

    @Test
    fun `a city with no bundled data returns null (falls back to mishor)`() {
        // צפת is a real CityCatalog id, but not one of the 38 bundled aliases.
        assertNull(repo.getVisibleSunrise("צפת", LocalDate.of(2026, 9, 14), zone))
    }

    @Test
    fun `a legacy English alias resolves through its metro, same as the Hebrew city`() {
        // "רחובות" (a bundled Hebrew id) and "tel_aviv" (a legacy alias
        // mapped to the same "rechovot" metro) must read the identical row.
        val date = LocalDate.of(2026, 9, 14)
        val metro = repo.getVisibleSunrise("רחובות", date, zone)
        val alias = repo.getVisibleSunrise("tel_aviv", date, zone)
        assertEquals(metro, alias)
    }

    @Test
    fun `the 2026-10-25 DST boundary does not print an hour-late netz`() {
        // The exact regression the netz data rebuild fixed. If this ever
        // regresses on desktop specifically, it means DesktopChaiTablesRepository
        // stopped sharing ChaiTablesBundledAsset's re-basing with Android.
        val date = LocalDate.of(2026, 10, 25)
        val instant = repo.getVisibleSunrise("קרני_שומרון", date, zone)!!
        val local = instant.atZone(ZoneId.of(zone))
        assertEquals("hour", 5, local.hour)
        assertEquals("minute", 58, local.minute)
    }
}
