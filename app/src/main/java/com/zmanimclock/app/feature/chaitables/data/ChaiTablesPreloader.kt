package com.zmanimclock.app.feature.chaitables.data

import android.content.Context
import android.util.Log
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesDao
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads pre-bundled ChaiTables visible sunrise data from assets on first launch.
 *
 * The data covers 8 Israeli metro areas with 365 entries each (one per Gregorian day-of-year).
 * Cities without direct ChaiTables terrain data are mapped to the nearest metro area.
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
        private const val ASSET_FILE = "chai_tables_preloaded.json"
        private const val MIN_ENTRIES_PER_METRO = 300
    }

    /**
     * Load pre-bundled data into Room database if not already loaded.
     * Should be called once at app startup.
     *
     * @return true if data was loaded or already exists
     */
    suspend fun ensureDataLoaded(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check if data already exists for Jerusalem (our canary city)
            val existingCount = dao.getCountForLocation("jerusalem")
            if (existingCount >= MIN_ENTRIES_PER_METRO) {
                Log.d(TAG, "Pre-loaded data already exists ($existingCount entries for jerusalem)")
                return@withContext true
            }

            Log.i(TAG, "Loading pre-bundled ChaiTables data from assets...")
            val jsonStr = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)

            val metros = json.getJSONObject("metros")
            val cityToMetro = json.getJSONObject("cityToMetro")
            var totalEntries = 0

            // Load each metro area's data
            val metroNames = metros.keys()
            while (metroNames.hasNext()) {
                val metroName = metroNames.next()
                val entries = metros.getJSONArray(metroName)

                val entities = mutableListOf<ChaiTablesEntity>()
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONArray(i)
                    entities.add(
                        ChaiTablesEntity(
                            locationKey = metroName,
                            dayOfYear = entry.getInt(0),
                            sunriseHour = entry.getInt(1),
                            sunriseMinute = entry.getInt(2),
                            sunriseSecond = entry.getInt(3),
                            fetchedAt = System.currentTimeMillis(),
                        )
                    )
                }

                dao.insertAll(entities)
                totalEntries += entities.size
                Log.d(TAG, "Loaded $metroName: ${entities.size} entries")
            }

            // Now create entries for mapped cities (aliases)
            val cityKeys = cityToMetro.keys()
            while (cityKeys.hasNext()) {
                val cityId = cityKeys.next()
                val metroName = cityToMetro.getString(cityId)

                // Skip if cityId == metroName (already loaded)
                if (cityId == metroName) continue

                // Check if city data already exists
                val cityCount = dao.getCountForLocation(cityId)
                if (cityCount >= MIN_ENTRIES_PER_METRO) continue

                // Copy metro data to city key
                val metroEntries = metros.optJSONArray(metroName) ?: continue
                val cityEntities = mutableListOf<ChaiTablesEntity>()
                for (i in 0 until metroEntries.length()) {
                    val entry = metroEntries.getJSONArray(i)
                    cityEntities.add(
                        ChaiTablesEntity(
                            locationKey = cityId,
                            dayOfYear = entry.getInt(0),
                            sunriseHour = entry.getInt(1),
                            sunriseMinute = entry.getInt(2),
                            sunriseSecond = entry.getInt(3),
                            fetchedAt = System.currentTimeMillis(),
                        )
                    )
                }
                dao.insertAll(cityEntities)
                totalEntries += cityEntities.size
                Log.d(TAG, "Loaded $cityId (mapped from $metroName): ${cityEntities.size} entries")
            }

            Log.i(TAG, "Pre-loaded $totalEntries total entries from assets")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load pre-bundled ChaiTables data", e)
            false
        }
    }
}
