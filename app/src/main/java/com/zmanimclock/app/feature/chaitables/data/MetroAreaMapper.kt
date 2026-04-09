package com.zmanimclock.app.feature.chaitables.data

import com.zmanimclock.app.feature.chaitables.data.model.ChaiTablesParams
import com.zmanimclock.app.location.model.AppGeoLocation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps cities to ChaiTables metro area parameters.
 *
 * Israeli cities use TableType=BY with a metro area name.
 * Worldwide cities use TableType=Chai with coordinates and search radius.
 */
@Singleton
class MetroAreaMapper @Inject constructor() {

    /**
     * Israeli city ID -> ChaiTables cgi_MetroArea value.
     * These are the lowercase, underscore-separated names that ChaiTables recognizes.
     */
    private val israeliMetroMap = mapOf(
        "jerusalem" to "jerusalem",
        "tel_aviv" to "tel_aviv",
        "haifa" to "haifa",
        "beer_sheva" to "beer_sheva",
        "eilat" to "eilat",
        "tzfat" to "tzfat",
        "tiberias" to "tverya",
        "netanya" to "netanya",
        "ashdod" to "ashdod",
        "petach_tikva" to "petach_tikva",
        "bnei_brak" to "bnei_brak",
        "ramat_gan" to "ramat_gan",
        "herzliya" to "herzliya",
        "kfar_saba" to "kfar_sava",
        "raanana" to "raanana",
        "modiin" to "modiin",
        "ashkelon" to "ashkelon",
        "afula" to "afula",
        "kiryat_shmona" to "kiryat_shmona",
        "nahariya" to "nahariya",
        "akko" to "akko",
        "maale_adumim" to "maale_adumim",
        "ariel" to "ariel",
        "gush_etzion" to "gush_etzion",
        "karnei_shomron" to "karnei_shomron",
        "beit_shemesh" to "beit_shemesh",
        "rehovot" to "rechovot",
        "rishon_lezion" to "rishon_lezion",
        "holon" to "holon",
        "bat_yam" to "bat_yam",
    )

    /**
     * Worldwide city ID -> ChaiTables country name + metro area index.
     * The index is 1-based position in ChaiTables' metro area list for that country.
     */
    private data class WorldwideMetro(
        val country: String,
        val metroIndex: Int,
    )

    private val worldwideMetroMap = mapOf(
        "new_york" to WorldwideMetro("USA", 31),        // New York
        "brooklyn" to WorldwideMetro("USA", 31),         // Same metro as NY
        "lakewood" to WorldwideMetro("USA", 31),         // NJ near NY metro
        "los_angeles" to WorldwideMetro("USA", 32),      // Los Angeles
        "miami" to WorldwideMetro("USA", 17),            // Miami
        "chicago" to WorldwideMetro("USA", 10),           // Chicago
        "toronto" to WorldwideMetro("Canada", 1),         // Toronto
        "montreal" to WorldwideMetro("Canada", 2),        // Montreal
        "london" to WorldwideMetro("England", 1),         // London
        "manchester" to WorldwideMetro("England", 2),     // Manchester
        "paris" to WorldwideMetro("France", 1),           // Paris
        "antwerp" to WorldwideMetro("Belgium", 1),        // Antwerp
        "melbourne" to WorldwideMetro("Australia", 1),    // Melbourne
        "sydney" to WorldwideMetro("Australia", 2),       // Sydney
        "buenos_aires" to WorldwideMetro("Argentina", 1), // Buenos Aires
        "sao_paulo" to WorldwideMetro("Brazil", 1),       // São Paulo
    )

    fun isIsraeliCity(cityId: String): Boolean = cityId in israeliMetroMap

    fun getMetroArea(cityId: String): String? = israeliMetroMap[cityId]

    /**
     * Detect if coordinates are in Israel (rough bounding box).
     */
    fun isCoordinateInIsrael(latitude: Double, longitude: Double): Boolean {
        return latitude in 29.3..33.4 && longitude in 34.0..36.0
    }

    /**
     * Build ChaiTablesParams for a given location.
     */
    fun buildParams(
        location: AppGeoLocation,
        cityId: String?,
        hebrewYear: Int,
    ): ChaiTablesParams {
        // Determine if Israel
        val isIsrael = if (cityId != null) {
            isIsraeliCity(cityId)
        } else {
            isCoordinateInIsrael(location.latitude, location.longitude)
        }

        // Get metro area and country
        val metroAreaName: String?
        val country: String
        val searchRadius: String

        if (isIsrael) {
            metroAreaName = cityId?.let { getMetroArea(it) }
            country = "Eretz_Yisroel"
            searchRadius = "2"
        } else {
            val worldwideInfo = cityId?.let { worldwideMetroMap[it] }
            metroAreaName = null // Worldwide uses coordinates, not metro name
            country = worldwideInfo?.country ?: guessCountry(location)
            searchRadius = "8" // Default; fetcher may optimize
        }

        return ChaiTablesParams(
            latitude = location.latitude,
            longitude = location.longitude,
            elevation = location.elevation,
            timeZoneId = location.timeZone.id,
            hebrewYear = hebrewYear,
            isIsrael = isIsrael,
            metroAreaName = metroAreaName,
            searchRadius = searchRadius,
            country = country,
        )
    }

    /**
     * Compute a stable location key for caching.
     */
    fun computeLocationKey(cityId: String?, location: AppGeoLocation): String {
        if (cityId != null) return cityId
        val lat = "%.3f".format(location.latitude)
        val lon = "%.3f".format(location.longitude)
        return "gps_${lat}_${lon}"
    }

    /**
     * Pre-loaded metro areas with their coordinates (for GPS nearest-metro lookup).
     */
    private val preloadedMetros = mapOf(
        "jerusalem" to Pair(31.778, 35.235),
        "haifa" to Pair(32.794, 34.990),
        "eilat" to Pair(29.558, 34.952),
        "ashdod" to Pair(31.804, 34.655),
        "modiin" to Pair(31.899, 35.010),
        "ariel" to Pair(32.107, 35.173),
        "karnei_shomron" to Pair(32.176, 35.096),
        "rechovot" to Pair(31.894, 34.810),
    )

    /**
     * Find the nearest pre-loaded metro area for a GPS coordinate.
     * Used as fallback for GPS users in Israel.
     */
    fun findNearestMetro(latitude: Double, longitude: Double): String? {
        if (!isCoordinateInIsrael(latitude, longitude)) return null

        return preloadedMetros.minByOrNull { (_, coords) ->
            val dLat = latitude - coords.first
            val dLon = longitude - coords.second
            dLat * dLat + dLon * dLon  // Squared distance (no need for sqrt)
        }?.key
    }

    /**
     * Get the worldwide metro index for ChaiTables URL.
     */
    fun getWorldwideMetroIndex(cityId: String?): Int {
        return cityId?.let { worldwideMetroMap[it]?.metroIndex } ?: 0
    }

    private fun guessCountry(location: AppGeoLocation): String {
        // Simple bounding box checks for major countries
        val lat = location.latitude
        val lon = location.longitude
        return when {
            lat in 24.0..50.0 && lon in -125.0..-66.0 -> "USA"
            lat in 42.0..84.0 && lon in -141.0..-52.0 -> "Canada"
            lat in 49.0..61.0 && lon in -8.0..2.0 -> "England"
            lat in 42.0..51.0 && lon in -5.0..8.0 -> "France"
            lat in 50.0..51.5 && lon in 2.5..6.5 -> "Belgium"
            lat in -44.0..-10.0 && lon in 112.0..154.0 -> "Australia"
            lat in -55.0..-22.0 && lon in -73.0..-53.0 -> "Argentina"
            lat in -34.0..5.0 && lon in -74.0..-34.0 -> "Brazil"
            else -> "USA" // fallback
        }
    }
}
