package com.zmanimclock.app.location

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.zmanimclock.app.R
import com.zmanimclock.app.location.model.CityInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = false)
private data class CityJson(
    val id: String,
    val nameHebrew: String,
    val nameEnglish: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double = 0.0,
    val timeZoneId: String,
    val region: String = "",
)

/**
 * Loads the bundled city list (res/raw/cities.json): 415 Israeli localities
 * sorted alef-bet (ids are exact ChaiTables cgi_MetroArea values) + world
 * cities. Loaded once and cached in memory.
 */
@Singleton
class CityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Volatile
    private var cache: List<CityInfo>? = null

    suspend fun allCities(): List<CityInfo> = cache ?: withContext(Dispatchers.IO) {
        val json = context.resources.openRawResource(R.raw.cities)
            .bufferedReader().use { it.readText() }
        val type = Types.newParameterizedType(List::class.java, CityJson::class.java)
        val parsed = moshi.adapter<List<CityJson>>(type).fromJson(json).orEmpty()
        parsed.map {
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
    }

    /** Simple substring search over Hebrew and English names. */
    suspend fun search(query: String): List<CityInfo> {
        val all = allCities()
        if (query.isBlank()) return all
        val q = query.trim()
        return all.filter {
            it.nameHebrew.contains(q) || it.nameEnglish.contains(q, ignoreCase = true)
        }
    }
}
