package com.zmanimclock.app.location

import android.content.Context
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.zmanimclock.app.R
import com.zmanimclock.app.location.model.AppGeoLocation
import com.zmanimclock.app.location.model.CityInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var cities: List<CityInfo> = emptyList()

    fun getAllCities(): List<CityInfo> {
        if (cities.isEmpty()) {
            cities = loadCitiesFromJson()
        }
        return cities
    }

    fun getIsraeliCities(): List<CityInfo> = getAllCities().filter { it.country == "IL" }

    fun getWorldwideCities(): List<CityInfo> = getAllCities().filter { it.country != "IL" }

    fun searchCities(query: String): List<CityInfo> {
        val q = query.trim().lowercase()
        return getAllCities().filter { city ->
            city.nameHebrew.contains(q) ||
            city.nameEnglish.lowercase().contains(q) ||
            city.region.lowercase().contains(q)
        }
    }

    fun cityToGeoLocation(city: CityInfo): AppGeoLocation {
        return AppGeoLocation(
            cityNameHebrew = city.nameHebrew,
            cityNameEnglish = city.nameEnglish,
            latitude = city.latitude,
            longitude = city.longitude,
            elevation = city.elevation,
            timeZone = TimeZone.getTimeZone(city.timeZoneId),
            isFromGps = false,
        )
    }

    private fun loadCitiesFromJson(): List<CityInfo> {
        return try {
            val jsonString = context.resources.openRawResource(R.raw.cities)
                .bufferedReader()
                .use { it.readText() }

            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val type = Types.newParameterizedType(List::class.java, CityInfo::class.java)
            val adapter: JsonAdapter<List<CityInfo>> = moshi.adapter(type)
            adapter.fromJson(jsonString) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
