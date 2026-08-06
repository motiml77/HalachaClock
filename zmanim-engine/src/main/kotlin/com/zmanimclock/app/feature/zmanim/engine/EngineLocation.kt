package com.zmanimclock.app.feature.zmanim.engine

import com.kosherjava.zmanim.util.GeoLocation
import java.util.TimeZone

/**
 * Pure (Android-free) location input for the zmanim engine, so the engine
 * can run in plain JVM unit tests and be verified against the luach.
 *
 * Note: per the Ohr HaChaim / Chazon Yosef luach convention for Eretz Yisrael,
 * elevation is NOT fed into the astronomical calculation (sea-level "mishor"
 * horizon is used); observed terrain is accounted for by the ChaiTables
 * visible-sunrise layer instead. [elevationMeters] is retained for the
 * worldwide fallback path.
 */
data class EngineLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double = 0.0,
    val timeZoneId: String = "Asia/Jerusalem",
) {
    val timeZone: TimeZone get() = TimeZone.getTimeZone(timeZoneId)

    fun toKosherJavaGeoLocation(): GeoLocation =
        GeoLocation(name, latitude, longitude, elevationMeters, timeZone)
}
