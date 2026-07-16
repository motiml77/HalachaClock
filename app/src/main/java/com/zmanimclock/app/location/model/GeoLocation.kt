package com.zmanimclock.app.location.model

import java.util.TimeZone

data class AppGeoLocation(
    val cityNameHebrew: String,
    val cityNameEnglish: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double = 0.0,
    val timeZone: TimeZone = TimeZone.getTimeZone("Asia/Jerusalem"),
    val isFromGps: Boolean = false,
) {
    fun toKosherJavaGeoLocation(): com.kosherjava.zmanim.util.GeoLocation {
        return com.kosherjava.zmanim.util.GeoLocation(
            cityNameEnglish,
            latitude,
            longitude,
            elevation,
            timeZone,
        )
    }
}

data class CityInfo(
    val id: String,
    val nameHebrew: String,
    val nameEnglish: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val timeZoneId: String,
    val region: String = "",
)
