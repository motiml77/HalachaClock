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

        /**
         * A full SOLAR year is 365 distinct (month, day) keys. This used to be
         * 300, which silently accepted an incomplete table: a Hebrew NON-leap
         * year is only 353–355 days, so one fetch yields 353–355 distinct
         * keys and ~10–13 calendar days are left with no data at all. The old
         * threshold then declared the cache "complete" and those days fell
         * back to the mishor sunrise forever. Only a Hebrew LEAP year
         * (383–385 days) covers a whole solar year in a single fetch.
         * 29 February is excused — SolarDayKey.fallbackFor maps it to 28 Feb.
         */
        private const val FULL_YEAR_KEYS = 365

        private const val SENTINEL_DAY = 0
        private const val SENTINEL_HOUR = -1
        /** How long a failed-fetch sentinel blocks retrying — see below. */
        private const val SENTINEL_TTL_MS = 12 * 60 * 60 * 1000L

        /** Marker row recording "Hebrew year N was already fetched". */
        private const val FETCHED_YEAR_HOUR = -2
        private fun fetchedYearKey(hebrewYear: Int) = -hebrewYear
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

        // 2. Genuinely complete table → this day really has no data
        val count = dao.getCountForLocation(locationKey)
        if (count >= FULL_YEAR_KEYS) return null

        // 3. GPS users inside Israel: try the nearest preloaded metro area
        if (cityId == null && metroMapper.isCoordinateInIsrael(location.latitude, location.longitude)) {
            metroMapper.findNearestMetro(location.latitude, location.longitude)?.let { metro ->
                dao.getSunrise(metro, dayKey)?.let { metroCached ->
                    Log.d(TAG, "Using nearest metro '$metro' for GPS location $locationKey")
                    return instantFromEntry(metroCached, date, location)
                }
            }
        }

        // 4. Sentinel — this location is known to have no ChaiTables data.
        // TTL'd: the fetch that wrote this sentinel can have failed for a
        // purely TRANSIENT reason (no network at that instant — offline
        // install, a dead cell signal). Without an expiry, that one failure
        // blocked every later in-app attempt for this location FOREVER —
        // getVisibleSunrise short-circuits here before ever reaching the
        // fetch call again. The only thing that could still repair it was
        // the once-a-day ChaiTablesRefreshWorker, which calls
        // prefetchForLocation directly and never even looks at this
        // sentinel — so a city outside the 8 bundled metros that failed once
        // while offline stayed on the mishor-sunrise fallback (~12 minutes
        // early in a hill town) for up to a day even after connectivity
        // returned.
        if (count > 0) {
            val sentinel = dao.getSunrise(locationKey, SENTINEL_DAY)
            if (sentinel?.sunriseHour == SENTINEL_HOUR) {
                val ageMs = System.currentTimeMillis() - sentinel.fetchedAt
                if (ageMs < SENTINEL_TTL_MS) return null
                Log.i(TAG, "Sentinel for $locationKey is ${ageMs / 60_000} min old — retrying fetch")
            }
        }

        if (!allowNetwork) return null

        // 5. Fetch the Hebrew year that CONTAINS the requested date. Because a
        // non-leap Hebrew year is shorter than a solar year, one fetch cannot
        // cover every calendar day — so this is keyed to the date being asked
        // for, and each Hebrew year is fetched at most once per location.
        val hebrewYear = hebrewYearFor(date)
        if (dao.getSunrise(locationKey, fetchedYearKey(hebrewYear)) != null) {
            Log.d(TAG, "Hebrew year $hebrewYear already fetched for $locationKey — no data for $date")
            return null
        }
        Log.i(TAG, "Fetching ChaiTables for $locationKey (Hebrew year $hebrewYear)")
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
                    sourceEpochDay = e.sourceEpochDay,
                )
            })
            // Remember we covered this Hebrew year so it is never refetched
            dao.insertAll(
                listOf(
                    ChaiTablesEntity(
                        locationKey = locationKey,
                        dayOfYear = fetchedYearKey(hebrewYear),
                        sunriseHour = FETCHED_YEAR_HOUR,
                        sunriseMinute = 0,
                        sunriseSecond = 0,
                        fetchedAt = now,
                    )
                )
            )
            Log.i(TAG, "Cached ${data.entries.size} entries for $locationKey (Hebrew year $hebrewYear)")
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
        if (count >= FULL_YEAR_KEYS) {
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
                    sourceEpochDay = e.sourceEpochDay,
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
            // Prefer the row's OWN recorded date. A Hebrew year spans two
            // Gregorian years, so inferring the year from fetchedAt is wrong
            // for roughly half the rows — and since Israel's DST boundary
            // moves annually, that mis-dating shows up as a full-hour error on
            // the days between the two years' boundaries. Older rows (and the
            // bundled asset) have no source date, so they keep the old
            // heuristic, which is right for the majority of the table.
            val sourceDate = entry.sourceEpochDay
                .takeIf { it > 0 }
                ?.let { LocalDate.ofEpochDay(it) }
                ?: SolarDayKey.toDate(
                    entry.dayOfYear,
                    Instant.ofEpochMilli(entry.fetchedAt).atZone(zone).year,
                )
                ?: return@runCatching stored
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
