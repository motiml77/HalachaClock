package com.zmanimclock.app.feature.chaitables.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The real chai_tables_preloaded.json, parsed and re-based by the exact code
 * every platform runs (ChaiTablesPreloader on Android, DesktopChaiTablesRepository
 * on desktop). Pins are values independently fetched live from ChaiTables and
 * cross-checked when the asset was built — see the netz data rebuild.
 */
class ChaiTablesBundledAssetTest {

    private val asset = ChaiTablesBundledAsset.parse(
        javaClass.getResourceAsStream("/chai_tables_preloaded.json")!!.bufferedReader().readText()
    )
    private val zone = "Asia/Jerusalem"

    @Test
    fun `parses all 8 metros with a full solar year each`() {
        val expectedMetros = setOf(
            "jerusalem", "haifa", "eilat", "ashdod", "modiin", "ariel", "karnei_shomron", "rechovot",
        )
        assertEquals(expectedMetros, asset.metros.keys)
        for ((name, rows) in asset.metros) {
            assertEquals("$name row count", 366, rows.size)
        }
        assertEquals(38, asset.cityToMetro.size)
    }

    @Test
    fun `pins match values independently fetched live from ChaiTables`() {
        val pins = listOf(
            Triple("jerusalem", LocalDate.of(2026, 9, 14), Triple(6, 21, 38)),
            Triple("jerusalem", LocalDate.of(2026, 12, 21), Triple(6, 33, 11)),
            Triple("jerusalem", LocalDate.of(2027, 3, 20), Triple(5, 42, 42)),
            Triple("jerusalem", LocalDate.of(2027, 6, 21), Triple(5, 33, 47)),
            Triple("karnei_shomron", LocalDate.of(2026, 9, 14), Triple(6, 31, 20)),
            Triple("karnei_shomron", LocalDate.of(2026, 12, 21), Triple(6, 42, 40)),
            Triple("karnei_shomron", LocalDate.of(2027, 3, 20), Triple(5, 52, 55)),
            Triple("karnei_shomron", LocalDate.of(2027, 6, 21), Triple(5, 45, 3)),
        )
        for ((metro, date, expected) in pins) {
            val (hour, minute, second) = expected
            val row = ChaiTablesBundledAsset.rowFor(asset.metros.getValue(metro), date)
            assertNotNull("$metro $date", row)
            assertEquals("$metro $date hour", hour, row!!.hour)
            assertEquals("$metro $date minute", minute, row.minute)
            assertEquals("$metro $date second", second, row.second)
        }
    }

    @Test
    fun `the 2026-10-25 DST boundary re-bases to the sea-level hour, not an hour late`() {
        // The exact regression this asset fixed: reusing the stored wall
        // clock verbatim on a DST-boundary day used to print 06:58 instead
        // of 05:58 for Karnei Shomron (an hour off from mishor 05:50:37).
        val date = LocalDate.of(2026, 10, 25)
        val row = ChaiTablesBundledAsset.rowFor(asset.metros.getValue("karnei_shomron"), date)!!
        val instant = ChaiTablesBundledAsset.rebasedInstant(row, date, zone)
        val local = instant.atZone(ZoneId.of(zone))
        assertEquals(5, local.hour)
        assertEquals(58, local.minute)
    }

    @Test
    fun `29 February has its own real row, not a fallback`() {
        // The asset was built with a genuine 2028-02-29 (5788) fetch for
        // exactly this key, so every metro should resolve it directly.
        val rows = asset.metros.getValue("jerusalem")
        val leapDay = ChaiTablesBundledAsset.rowFor(rows, LocalDate.of(2028, 2, 29))
        assertNotNull(leapDay)
        assertEquals(2, leapDay!!.month)
        assertEquals(29, leapDay.day)
    }

    @Test
    fun `a table with no 29 February row falls back to its solar twin, 28 February`() {
        // Exercises SolarDayKey.fallbackFor directly, with a synthetic table
        // that has no key 229 — the situation the real asset never actually
        // hits, since it always ships a genuine leap-day row.
        val twentyEighth = BundledSunriseRow(2, 28, 6, 30, 0, sourceEpochDay = 0L)
        val rows = listOf(twentyEighth, BundledSunriseRow(3, 1, 6, 25, 0, sourceEpochDay = 0L))
        val resolved = ChaiTablesBundledAsset.rowFor(rows, LocalDate.of(2032, 2, 29))
        assertEquals(twentyEighth, resolved)
    }

    @Test
    fun `every Hebrew and legacy English city id resolves to one of the 8 metros`() {
        for ((cityId, metro) in asset.cityToMetro) {
            assertTrue("'$cityId' -> unknown metro '$metro'", metro in asset.metros.keys)
        }
    }
}
