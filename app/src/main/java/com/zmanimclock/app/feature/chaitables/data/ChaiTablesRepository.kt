package com.zmanimclock.app.feature.chaitables.data

import android.util.Log
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesDao
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesEntity
import com.zmanimclock.app.feature.chaitables.data.remote.ChaiTablesFetcher
import com.zmanimclock.app.location.model.AppGeoLocation
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for visible sunrise data.
 *
 * KEY OPTIMIZATION: Visible sunrise depends on the sun's position relative to
 * terrain, which is determined by the Gregorian/solar date. April 9 has the
 * same visible sunrise every year (±seconds). Therefore:
 *
 * - Data is cached by Gregorian day-of-year (1-366) + location
 * - One fetch per location is valid FOREVER (no yearly refresh needed!)
 * - To use: convert requested date → Gregorian day-of-year → lookup time → combine with actual date
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
        // Expect ~365 entries for a full year
        private const val FULL_YEAR_THRESHOLD = 300
        // Sentinel values
        private const val SENTINEL_DAY = 0
        private const val SENTINEL_HOUR = -1
    }

    /**
     * Get visible sunrise for a specific location and date.
     *
     * Accepts Hebrew date parameters (for ChaiTables fetch if needed),
     * but looks up by Gregorian day-of-year in cache.
     *
     * @return Visible sunrise Date, or null if not available
     */
    suspend fun getVisibleSunrise(
        location: AppGeoLocation,
        cityId: String?,
        hebrewYear: Int,
        hebrewMonth: Int,
        hebrewDay: Int,
    ): Date? {
        val locationKey = metroMapper.computeLocationKey(cityId, location)

        // Convert Hebrew date to Gregorian day-of-year
        val jewishDate = try {
            com.kosherjava.zmanim.hebrewcalendar.JewishDate(hebrewYear, hebrewMonth, hebrewDay)
        } catch (e: Exception) {
            return null
        }
        val gregCal = jewishDate.getGregorianCalendar()
        val dayOfYear = gregCal.get(Calendar.DAY_OF_YEAR)

        // 1. Check cache by Gregorian day-of-year
        val cached = dao.getSunrise(locationKey, dayOfYear)
        if (cached != null) {
            return buildDateFromEntry(cached, gregCal, location.timeZone)
        }

        // 2. Check if we already have a full year of data (this day just doesn't exist, e.g., Feb 29)
        val count = dao.getCountForLocation(locationKey)
        if (count >= FULL_YEAR_THRESHOLD) {
            return null
        }

        // 3. For GPS users in Israel, try the nearest pre-loaded metro area
        if (cityId == null && metroMapper.isCoordinateInIsrael(location.latitude, location.longitude)) {
            val nearestMetro = metroMapper.findNearestMetro(location.latitude, location.longitude)
            if (nearestMetro != null) {
                val metroCached = dao.getSunrise(nearestMetro, dayOfYear)
                if (metroCached != null) {
                    Log.d(TAG, "Using nearest metro '$nearestMetro' data for GPS location $locationKey")
                    return buildDateFromEntry(metroCached, gregCal, location.timeZone)
                }
            }
        }

        // 4. Check sentinel (location has no ChaiTables data at all)
        if (count > 0) {
            val sentinel = dao.getSunrise(locationKey, SENTINEL_DAY)
            if (sentinel?.sunriseHour == SENTINEL_HOUR) {
                return null
            }
        }

        // 5. Fetch from network
        Log.i(TAG, "Fetching ChaiTables for $locationKey")
        val params = metroMapper.buildParams(location, cityId, hebrewYear)
        val result = fetcher.fetch(params, locationKey)

        result.onSuccess { data ->
            val entities = data.entries.map { entry ->
                ChaiTablesEntity(
                    locationKey = locationKey,
                    dayOfYear = entry.dayOfYear,
                    sunriseHour = entry.hour,
                    sunriseMinute = entry.minute,
                    sunriseSecond = entry.second,
                    fetchedAt = System.currentTimeMillis(),
                )
            }
            dao.insertAll(entities)
            Log.i(TAG, "Cached ${entities.size} entries for $locationKey (valid forever!)")

            // Now look up the specific day
            val entry = dao.getSunrise(locationKey, dayOfYear)
            if (entry != null) {
                return buildDateFromEntry(entry, gregCal, location.timeZone)
            }
        }

        result.onFailure { error ->
            Log.w(TAG, "Failed to fetch ChaiTables for $locationKey: ${error.message}")
            // Store sentinel
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

    /**
     * Pre-fetch data for a location. Returns true if data exists or was fetched.
     */
    suspend fun prefetchForLocation(
        location: AppGeoLocation,
        cityId: String?,
        hebrewYear: Int,
    ): Boolean {
        val locationKey = metroMapper.computeLocationKey(cityId, location)

        val count = dao.getCountForLocation(locationKey)
        if (count >= FULL_YEAR_THRESHOLD) {
            Log.d(TAG, "Already have $count entries for $locationKey (cached permanently)")
            return true
        }

        val params = metroMapper.buildParams(location, cityId, hebrewYear)
        val result = fetcher.fetch(params, locationKey)

        result.onSuccess { data ->
            val entities = data.entries.map { entry ->
                ChaiTablesEntity(
                    locationKey = locationKey,
                    dayOfYear = entry.dayOfYear,
                    sunriseHour = entry.hour,
                    sunriseMinute = entry.minute,
                    sunriseSecond = entry.second,
                    fetchedAt = System.currentTimeMillis(),
                )
            }
            dao.insertAll(entities)
            Log.i(TAG, "Prefetched ${entities.size} entries for $locationKey")
            return true
        }

        return false
    }

    /**
     * Clear cache for a specific location (when user changes city).
     */
    suspend fun clearLocationCache(cityId: String?, location: AppGeoLocation) {
        val locationKey = metroMapper.computeLocationKey(cityId, location)
        dao.deleteForLocation(locationKey)
    }

    /**
     * Build a full Date object from cached time components + the actual Gregorian date.
     *
     * The cached hour:minute:second is the local sunrise time for that day-of-year.
     * We combine it with the actual year's Gregorian date to get a proper Date.
     */
    private fun buildDateFromEntry(
        entry: ChaiTablesEntity,
        gregorianDate: Calendar,
        timeZone: TimeZone,
    ): Date? {
        if (entry.sunriseHour < 0) return null // sentinel

        val cal = Calendar.getInstance(timeZone)
        cal.set(Calendar.YEAR, gregorianDate.get(Calendar.YEAR))
        cal.set(Calendar.MONTH, gregorianDate.get(Calendar.MONTH))
        cal.set(Calendar.DAY_OF_MONTH, gregorianDate.get(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, entry.sunriseHour)
        cal.set(Calendar.MINUTE, entry.sunriseMinute)
        cal.set(Calendar.SECOND, entry.sunriseSecond)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }
}
