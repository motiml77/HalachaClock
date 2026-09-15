package com.zmanimclock.app.feature.chaitables.data

import android.content.Context
import android.util.Log
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesDao
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads pre-bundled ChaiTables visible sunrise data from the shared module on first launch.
 *
 * The data covers 8 Israeli metro areas with one row per (month, day) for a
 * full solar year each. Cities without direct ChaiTables terrain data are
 * mapped to the nearest metro area.
 *
 * This eliminates the need for network access for Israeli cities.
 */
@Singleton
class ChaiTablesPreloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ChaiTablesDao,
) {
    companion object {
        private const val TAG = "ChaiTablesPreloader"
        // On the classpath, in :zmanim-engine — one copy, shared with the
        // desktop build, for the same reason cities.json moved there.
        private const val ASSET_FILE = "/chai_tables_preloaded.json"

        // A reserved location key — never a real city or metro id — that
        // holds one row recording which asset VERSION is already loaded.
        // Older code gated on "does jerusalem already have ≥300 rows",
        // which could never tell a stale table from a fresh one: an app
        // update that ships a corrected asset (e.g. fixing the 2026-10-25
        // DST bug) needs its rows to overwrite the old ones on every
        // existing install, not just on a clean database. The version
        // number is stashed in the row's sunriseHour field, the same way
        // ChaiTablesRepository reuses entity fields for its own markers
        // (FETCHED_YEAR_HOUR, the sentinel).
        private const val VERSION_LOCATION_KEY = "__preloaded_version__"
        private const val VERSION_MARKER_DAY = 1
    }

    /**
     * Load pre-bundled data into Room database if the bundled asset is newer
     * than what is already loaded. Should be called once at app startup.
     *
     * @return true if data is loaded (already current, or loaded just now)
     */
    suspend fun ensureDataLoaded(): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonStr = javaClass.getResourceAsStream(ASSET_FILE)!!.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)
            val assetVersion = json.optInt("version", 1)

            val installedVersion = dao.getSunrise(VERSION_LOCATION_KEY, VERSION_MARKER_DAY)?.sunriseHour ?: 0
            if (installedVersion >= assetVersion) {
                Log.d(TAG, "Pre-loaded data already at version $installedVersion")
                return@withContext true
            }

            Log.i(TAG, "Loading pre-bundled ChaiTables data (asset version $assetVersion, installed $installedVersion)...")
            val metros = json.getJSONObject("metros")
            val cityToMetro = json.getJSONObject("cityToMetro")
            val now = System.currentTimeMillis()
            var totalEntries = 0

            // Load each metro area's data. insertAll REPLACEs by primary key
            // (locationKey, dayOfYear), so this naturally overwrites only the
            // rows for these 8 keys — a live-fetched city under any other key
            // is untouched.
            val metroNames = metros.keys()
            while (metroNames.hasNext()) {
                val metroName = metroNames.next()
                val entities = entitiesFromRows(metros.getJSONArray(metroName), metroName, now)
                dao.insertAll(entities)
                totalEntries += entities.size
                Log.d(TAG, "Loaded $metroName: ${entities.size} entries")
            }

            // Now create entries for mapped cities (aliases) — same overwrite reasoning.
            val cityKeys = cityToMetro.keys()
            while (cityKeys.hasNext()) {
                val cityId = cityKeys.next()
                val metroName = cityToMetro.getString(cityId)

                // Skip if cityId == metroName (already loaded)
                if (cityId == metroName) continue

                val metroRows = metros.optJSONArray(metroName) ?: continue
                val cityEntities = entitiesFromRows(metroRows, cityId, now)
                dao.insertAll(cityEntities)
                totalEntries += cityEntities.size
                Log.d(TAG, "Loaded $cityId (mapped from $metroName): ${cityEntities.size} entries")
            }

            // Record the version LAST: if the process dies mid-load, the next
            // launch retries the whole thing (insertAll is idempotent REPLACE)
            // instead of getting stuck believing a half-written table is current.
            dao.insertAll(
                listOf(
                    ChaiTablesEntity(
                        locationKey = VERSION_LOCATION_KEY,
                        dayOfYear = VERSION_MARKER_DAY,
                        sunriseHour = assetVersion,
                        sunriseMinute = 0,
                        sunriseSecond = 0,
                        fetchedAt = now,
                    )
                )
            )

            Log.i(TAG, "Pre-loaded $totalEntries total entries at version $assetVersion")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load pre-bundled ChaiTables data", e)
            false
        }
    }

    /**
     * Each row is `[month, day, hour, minute, second, sourceEpochDay]`. The
     * (month, day) pair keys the cache directly (that pair IS what
     * [SolarDayKey] encodes), and sourceEpochDay is the row's own real
     * Gregorian date — required so [ChaiTablesRepository] can re-base
     * Israel's DST offset correctly instead of guessing the year from
     * whenever the app happened to load this asset.
     */
    private fun entitiesFromRows(rows: JSONArray, locationKey: String, fetchedAt: Long): List<ChaiTablesEntity> =
        (0 until rows.length()).map { i ->
            val row = rows.getJSONArray(i)
            ChaiTablesEntity(
                locationKey = locationKey,
                dayOfYear = SolarDayKey.of(month = row.getInt(0), dayOfMonth = row.getInt(1)),
                sunriseHour = row.getInt(2),
                sunriseMinute = row.getInt(3),
                sunriseSecond = row.getInt(4),
                fetchedAt = fetchedAt,
                sourceEpochDay = row.getLong(5),
            )
        }
}
