package com.zmanimclock.app.feature.chaitables.data

import android.content.ContextWrapper
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the one thing that broke silently before: the bundled asset's rows
 * must carry their own [ChaiTablesEntity.sourceEpochDay] (needed for correct
 * DST re-basing — see ChaiTablesRepository.instantFromEntry), and a corrected
 * asset must overwrite an older install's rows rather than being skipped by
 * a "jerusalem already has data" canary that cannot tell stale from fresh.
 *
 * The bundled asset itself is data (chai_tables_preloaded.json in
 * :zmanim-engine), independently validated against live ChaiTables — this
 * test is about the LOADER's contract with that data, not the data itself.
 */
class ChaiTablesPreloaderTest {

    // Never invoked by the preloader; a real Context is not needed.
    private class FakeContext : ContextWrapper(null)

    private fun preloader(dao: FakeChaiTablesDao) = ChaiTablesPreloader(FakeContext(), dao)

    // Checkpoints independently verified against live ChaiTables (visible sunrise).
    private val pins = listOf(
        Triple("jerusalem", 9 to 14, Triple(6, 21, 38)),
        Triple("jerusalem", 12 to 21, Triple(6, 33, 11)),
        Triple("jerusalem", 3 to 20, Triple(5, 42, 42)),
        Triple("jerusalem", 6 to 21, Triple(5, 33, 47)),
        Triple("karnei_shomron", 9 to 14, Triple(6, 31, 20)),
        Triple("karnei_shomron", 12 to 21, Triple(6, 42, 40)),
        Triple("karnei_shomron", 3 to 20, Triple(5, 52, 55)),
        Triple("karnei_shomron", 6 to 21, Triple(5, 45, 3)),
    )

    @Test
    fun `fresh install loads a full solar year per metro with sourceEpochDay set`() = runBlocking {
        val dao = FakeChaiTablesDao()

        assertTrue(preloader(dao).ensureDataLoaded())

        for (metro in listOf(
            "jerusalem", "haifa", "eilat", "ashdod", "modiin", "ariel", "karnei_shomron", "rechovot",
        )) {
            assertEquals("$metro should have a full 366-key solar year", 366, dao.getCountForLocation(metro))
        }

        for ((metro, monthDay, expected) in pins) {
            val (month, day) = monthDay
            val (hour, minute, second) = expected
            val row = dao.getSunrise(metro, SolarDayKey.of(month, day))
            assertEquals("$metro $month/$day hour", hour, row?.sunriseHour)
            assertEquals("$metro $month/$day minute", minute, row?.sunriseMinute)
            assertEquals("$metro $month/$day second", second, row?.sunriseSecond)
            // The old preloader never set this, which is exactly what let the
            // repository mis-date rows across a DST boundary.
            assertTrue("$metro $month/$day must carry its own source date", (row?.sourceEpochDay ?: 0L) > 0L)
        }
    }

    @Test
    fun `an alias city is loaded under its own key from its metro's rows`() = runBlocking {
        val dao = FakeChaiTablesDao()

        assertTrue(preloader(dao).ensureDataLoaded())

        assertEquals(366, dao.getCountForLocation("rehovot"))
        val metroRow = dao.getSunrise("rechovot", SolarDayKey.of(9, 14))
        val aliasRow = dao.getSunrise("rehovot", SolarDayKey.of(9, 14))
        assertEquals(metroRow?.sunriseHour, aliasRow?.sunriseHour)
        assertEquals(metroRow?.sunriseMinute, aliasRow?.sunriseMinute)
        assertEquals(metroRow?.sunriseSecond, aliasRow?.sunriseSecond)
        assertEquals(metroRow?.sourceEpochDay, aliasRow?.sourceEpochDay)
    }

    @Test
    fun `a second call is a no-op once the asset version is already loaded`() = runBlocking {
        val dao = FakeChaiTablesDao()
        preloader(dao).ensureDataLoaded()

        // Corrupt one row after the real load, the way a stale value would
        // look if the loader wrongly decided to skip reloading it.
        val key = SolarDayKey.of(9, 14)
        val before = dao.getSunrise("jerusalem", key)!!
        dao.insertAll(listOf(before.copy(sunriseHour = 1, sunriseMinute = 1, sunriseSecond = 1)))

        assertTrue(preloader(dao).ensureDataLoaded())

        val after = dao.getSunrise("jerusalem", key)!!
        assertEquals("already at the current version — must not touch existing rows", 1, after.sunriseHour)
    }

    @Test
    fun `a stale pre-version install is overwritten by the current asset`() = runBlocking {
        val dao = FakeChaiTablesDao()
        val key = SolarDayKey.of(9, 14)

        // Simulate an old install: real-looking rows for every metro (so the
        // old count-based canary would have considered this "already loaded"),
        // but no version marker and no sourceEpochDay — exactly what the
        // pre-fix ChaiTablesPreloader wrote, and exactly what produced the
        // 2026-10-25 one-hour bug.
        for (metro in listOf(
            "jerusalem", "haifa", "eilat", "ashdod", "modiin", "ariel", "karnei_shomron", "rechovot",
        )) {
            dao.insertAll(
                (1..366).map { day ->
                    ChaiTablesEntity(
                        locationKey = metro,
                        dayOfYear = day,
                        sunriseHour = 9,
                        sunriseMinute = 9,
                        sunriseSecond = 9,
                        fetchedAt = 0L,
                        sourceEpochDay = 0L,
                    )
                }
            )
        }
        dao.insertAll(
            listOf(
                ChaiTablesEntity(
                    locationKey = "jerusalem",
                    dayOfYear = key,
                    sunriseHour = 9,
                    sunriseMinute = 9,
                    sunriseSecond = 9,
                    fetchedAt = 0L,
                    sourceEpochDay = 0L,
                )
            )
        )
        assertFalse(
            "sanity: the stale row must not already equal the pin",
            dao.getSunrise("jerusalem", key)!!.sunriseHour == 6,
        )

        assertTrue(preloader(dao).ensureDataLoaded())

        val row = dao.getSunrise("jerusalem", key)!!
        assertEquals(6, row.sunriseHour)
        assertEquals(21, row.sunriseMinute)
        assertEquals(38, row.sunriseSecond)
        assertTrue("the corrected asset must stamp a real source date", row.sourceEpochDay > 0L)
    }
}
