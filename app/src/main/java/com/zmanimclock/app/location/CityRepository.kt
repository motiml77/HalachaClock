package com.zmanimclock.app.location

import android.content.Context
import android.util.Log
import com.zmanimclock.app.R
import com.zmanimclock.app.location.model.CityInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the bundled city list (res/raw/cities.json): 415 Israeli localities
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
            val json = context.resources.openRawResource(R.raw.cities)
                .bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        CityInfo(
                            id = o.getString("id"),
                            nameHebrew = o.getString("nameHebrew"),
                            nameEnglish = o.getString("nameEnglish"),
                            country = o.getString("country"),
                            latitude = o.getDouble("latitude"),
                            longitude = o.getDouble("longitude"),
                            elevation = o.optDouble("elevation", 0.0),
                            timeZoneId = o.getString("timeZoneId"),
                            region = o.optString("region", ""),
                        )
                    )
                }
            }.also { cache = it }
        } catch (e: Exception) {
            Log.e("CityRepository", "Failed to load cities.json", e)
            emptyList()
        }
    }
}
