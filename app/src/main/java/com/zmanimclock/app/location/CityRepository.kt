package com.zmanimclock.app.location

import android.content.Context
import android.util.Log
import com.zmanimclock.app.feature.location.CityCatalog
import com.zmanimclock.app.location.model.CityInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the bundled city list (:zmanim-engine resources): 415 Israeli localities
 * sorted alef-bet (ids are exact ChaiTables cgi_MetroArea values) + world
 * cities. Loaded once and cached in memory.
 */
@Singleton
class CityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var cache: List<CityInfo>? = null

    suspend fun allCities(): List<CityInfo> = cache ?: withContext(Dispatchers.IO) {
        try {
            // Reads the SAME cities.json the desktop build reads — it lives
            // in :zmanim-engine's resources, not in res/raw, because the
            // coordinates are zmanim INPUT: two copies drifting apart would
            // give one user different times on their phone and their computer.
            CityCatalog.cities.map {
                CityInfo(
                    id = it.id,
                    nameHebrew = it.nameHebrew,
                    nameEnglish = it.nameEnglish,
                    country = it.country,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    elevation = it.elevation,
                    timeZoneId = it.timeZoneId,
                    region = it.region,
                )
            }.also { cache = it }
        } catch (e: Exception) {
            Log.e("CityRepository", "Failed to load cities.json", e)
            emptyList()
        }
    }
}
