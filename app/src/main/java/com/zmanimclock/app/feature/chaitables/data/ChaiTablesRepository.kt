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
     */
    suspend fun getVisibleSunrise(
        location: AppGeoLocation,
        cityId: String?,
        date: LocalDate,
    ): Instant? {
        val locationKey = metroMapper.computeLocationKey(cityId, location)
        val dayOfYear = date.dayOfYear

        // 1. Cache hit by Gregorian day-of-year
        dao.getSunrise(locationKey, dayOfYear)?.let { cached ->
            return instantFromEntry(cached, date, location)
        }

        // 2. Full year already cached — this specific day simply has no data
        val count = dao.getCountForLocation(locationKey)
        if (count >= FULL_YEAR_THRESHOLD) return null

        // 3. GPS users inside Israel: try the nearest preloaded metro area
        if (cityId == null && metroMapper.isCoordinateInIsrael(location.latitude, location.longitude)) {
            metroMapper.findNearestMetro(location.latitude, location.longitude)?.let { metro ->
                dao.getSunrise(metro, dayOfYear)?.let { metroCached ->
                    Log.d(TAG, "Using nearest metro '$metro' for GPS location $locationKey")
                    return instantFromEntry(metroCached, date, location)
                }
            }
        }

        // 4. Sentinel — this location is known to have no ChaiTables data
        if (count > 0 && dao.getSunrise(locationKey, SENTINEL_DAY)?.sunriseHour == SENTINEL_HOUR) {
            return null
        }

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
            dao.getSunrise(locationKey, dayOfYear)?.let { entry ->
                return instantFromEntry(entry, date, location)
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

    /** Combine a cached local time-of-day with the real date in the location's zone. */
    private fun instantFromEntry(
        entry: ChaiTablesEntity,
        date: LocalDate,
        location: AppGeoLocation,
    ): Instant? {
        if (entry.sunriseHour < 0) return null // sentinel
        val zone = ZoneId.of(location.timeZone.id)
        val time = LocalTime.of(entry.sunriseHour, entry.sunriseMinute, entry.sunriseSecond)
        return date.atTime(time).atZone(zone).toInstant()
    }

    private fun hebrewYearFor(date: LocalDate): Int {
        val cal = GregorianCalendar(date.year, date.monthValue - 1, date.dayOfMonth)
        return JewishDate(cal).jewishYear
    }
}
