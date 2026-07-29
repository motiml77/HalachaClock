package com.zmanimclock.app.feature.chaitables.data

import android.util.Log
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesDao
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesEntity
import com.zmanimclock.app.feature.chaitables.data.remote.ChaiTablesFetcher
import com.zmanimclock.app.location.model.AppGeoLocation
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.GregorianCalendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for ChaiTables visible sunrise data.
 *
 * KEY OPTIMIZATION: visible sunrise depends on the sun's position relative to
 * terrain, which is determined by the Gregorian/solar date. April 9 has the
 * same visible sunrise every year (± seconds). Therefore:
 *
 * - Data is cached by Gregorian day-of-year (1-366) + location key
 * - One fetch per location is valid FOREVER (no yearly refresh needed)
 * - Lookup: date → day-of-year → cached local time → Instant on the real date
 *
 * A sentinel entry (dayOfYear=0, hour=-1) prevents repeated failed fetches.
 */
@Singleton
class ChaiTablesRepository @Inject constructor(
    private val dao: ChaiTablesDao,
    private val fetcher: ChaiTablesFetcher,
    private val metroMapper: MetroAreaMapper,
) {
    companion object {
        private const val TAG = "ChaiTablesRepo"

        /** ~365 entries expected for a full year. */
        private const val FULL_YEAR_THRESHOLD = 300

        private const val SENTINEL_DAY = 0
        private const val SENTINEL_HOUR = -1
    }

    /**
     * The visible sunrise for [date] at [location], or null when unavailable
     * (no terrain data / fetch failed) — the caller falls back to mishor.
     *
     * @param allowNetwork false = cache/metro steps only, no fetch and no
     *   sentinel write. Used from broadcast receivers (goAsync budget) and
     *   before user-unlock; the refresh worker completes the cache later.
     */
    suspend fun getVisibleSunrise(
        location: AppGeoLocation,
        cityId: String?,
        date: LocalDate,
        allowNetwork: Boolean = true,
    ): Instant? {
        val locationKey = metroMapper.computeLocationKey(cityId, location)
        // Keyed by (month, day), NOT by day-of-year: the table is a solar
        // almanac, and a leap day shifts every later day-of-year by one, so
        // raw day-of-year reads the wrong row for three years out of four.
        val dayKey = SolarDayKey.of(date)

        // 1. Cache hit for this calendar day (29 Feb falls back to 28 Feb)
        dao.getSunrise(locationKey, dayKey)?.let { cached ->
            return instantFromEntry(cached, date, location)
        }
        SolarDayKey.fallbackFor(date)?.let { alt ->
            dao.getSunrise(locationKey, alt)?.let { cached ->
                return instantFromEntry(cached, date, location)
            }
        }

        // 2. Full year already cached — this specific day simply has no data
        val count = dao.getCountForLocation(locationKey)
        if (count >= FULL_YEAR_THRESHOLD) return null

        // 3. GPS users inside Israel: try the nearest preloaded metro area
        if (cityId == null && metroMapper.isCoordinateInIsrael(location.latitude, location.longitude)) {
            metroMapper.findNearestMetro(location.latitude, location.longitude)?.let { metro ->
                dao.getSunrise(metro, dayKey)?.let { metroCached ->
                    Log.d(TAG, "Using nearest metro '$metro' for GPS location $locationKey")
                    return instantFromEntry(metroCached, date, location)
                }
            }
        }

        // 4. Sentinel — this location is known to have no ChaiTables data
        if (count > 0 && dao.getSunrise(locationKey, SENTINEL_DAY)?.sunriseHour == SENTINEL_HOUR) {
            return null
        }

        if (!allowNetwork) return null

        // 5. Fetch a full Hebrew year from the network and cache it forever
        Log.i(TAG, "Fetching ChaiTables for $locationKey")
        val hebrewYear = hebrewYearFor(date)
        val params = metroMapper.buildParams(location, cityId, hebrewYear)
        val result = fetcher.fetch(params, locationKey)

        result.onSuccess { data ->
            val now = System.currentTimeMillis()
            dao.insertAll(data.entries.map { e ->
                ChaiTablesEntity(
                    locationKey = locationKey,
                    dayOfYear = e.dayOfYear,
                    sunriseHour = e.hour,
                    sunriseMinute = e.minute,
                    sunriseSecond = e.second,
                    fetchedAt = now,
                )
            })
            Log.i(TAG, "Cached ${data.entries.size} entries for $locationKey (valid forever)")
            dao.getSunrise(locationKey, dayKey)?.let { entry ->
                return instantFromEntry(entry, date, location)
            }
            SolarDayKey.fallbackFor(date)?.let { alt ->
                dao.getSunrise(locationKey, alt)?.let { entry ->
                    return instantFromEntry(entry, date, location)
                }
            }
        }

        result.onFailure { error ->
            Log.w(TAG, "ChaiTables fetch failed for $locationKey: ${error.message}")
            dao.insertAll(
                listOf(
                    ChaiTablesEntity(
                        locationKey = locationKey,
                        dayOfYear = SENTINEL_DAY,
                        sunriseHour = SENTINEL_HOUR,
                        sunriseMinute = 0,
                        sunriseSecond = 0,
                        fetchedAt = System.currentTimeMillis(),
                    )
                )
            )
        }

        return null
    }

    /** Pre-fetch a location's full table. True when data exists or was fetched. */
    suspend fun prefetchForLocation(
        location: AppGeoLocation,
        cityId: String?,
        date: LocalDate = LocalDate.now(),
    ): Boolean {
        val locationKey = metroMapper.computeLocationKey(cityId, location)

        val count = dao.getCountForLocation(locationKey)
        if (count >= FULL_YEAR_THRESHOLD) {
            Log.d(TAG, "Already have $count entries for $locationKey")
            return true
        }

        val params = metroMapper.buildParams(location, cityId, hebrewYearFor(date))
        val result = fetcher.fetch(params, locationKey)

        result.onSuccess { data ->
            val now = System.currentTimeMillis()
            dao.insertAll(data.entries.map { e ->
                ChaiTablesEntity(
                    locationKey = locationKey,
                    dayOfYear = e.dayOfYear,
                    sunriseHour = e.hour,
                    sunriseMinute = e.minute,
                    sunriseSecond = e.second,
                    fetchedAt = now,
                )
            })
            Log.i(TAG, "Prefetched ${data.entries.size} entries for $locationKey")
            return true
        }

        return false
    }

    /** Clear cache for a location (e.g. when the user changes city). */
    suspend fun clearLocationCache(cityId: String?, location: AppGeoLocation) {
        dao.deleteForLocation(metroMapper.computeLocationKey(cityId, location))
    }

    /**
     * Combine a cached local time-of-day with the real date in the location's
     * zone.
     *
     * ChaiTables is fetched with DST ON, so the stored wall-clock time carries
     * the DST offset that was in force on the SOURCE date. Reusing that string
     * verbatim in a year whose DST boundary moved shifts הנץ הנראה by a full
     * hour on the days between the two boundaries. Correct for it by shifting
     * the wall clock by the difference between the target and source offsets —
     * the underlying solar moment is what the table actually encodes.
     *
     * Values are also range-validated: a malformed parse used to reach
     * LocalTime.of and throw DateTimeException up through the zmanim call
     * chain (crashing the alarm editor's preview).
     */
    private fun instantFromEntry(
        entry: ChaiTablesEntity,
        date: LocalDate,
        location: AppGeoLocation,
    ): Instant? {
        if (entry.sunriseHour < 0) return null // sentinel
        if (entry.sunriseHour !in 0..23 ||
            entry.sunriseMinute !in 0..59 ||
            entry.sunriseSecond !in 0..59
        ) {
            return null // corrupt row — fall back to the astronomical sunrise
        }
        val zone = ZoneId.of(location.timeZone.id)
        val stored = LocalTime.of(entry.sunriseHour, entry.sunriseMinute, entry.sunriseSecond)

        val corrected = runCatching {
            val rules = zone.rules
            val sourceYear = Instant.ofEpochMilli(entry.fetchedAt).atZone(zone).year
            val sourceDate = LocalDate.ofYearDay(
                sourceYear,
                entry.dayOfYear.coerceIn(1, if (LocalDate.of(sourceYear, 1, 1).isLeapYear) 366 else 365),
            )
            val sourceOffset = rules.getOffset(sourceDate.atTime(stored))
            val targetOffset = rules.getOffset(date.atTime(stored))
            stored.plusSeconds((targetOffset.totalSeconds - sourceOffset.totalSeconds).toLong())
        }.getOrDefault(stored)

        return date.atTime(corrected).atZone(zone).toInstant()
    }

    private fun hebrewYearFor(date: LocalDate): Int {
        val cal = GregorianCalendar(date.year, date.monthValue - 1, date.dayOfMonth)
        return JewishDate(cal).jewishYear
    }
}
