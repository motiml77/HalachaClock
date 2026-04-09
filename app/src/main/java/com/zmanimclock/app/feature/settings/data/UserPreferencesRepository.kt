package com.zmanimclock.app.feature.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zmanimclock.app.location.model.AppGeoLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

data class UserPreferences(
    val cityId: String = "jerusalem",
    val cityNameHebrew: String = "ירושלים",
    val cityNameEnglish: String = "Jerusalem",
    val latitude: Double = 31.778,
    val longitude: Double = 35.235,
    val elevation: Double = 800.0,
    val timeZoneId: String = "Asia/Jerusalem",
    val useGps: Boolean = false,
    val useElevation: Boolean = true,
    val candleLightingMinutes: Int = 20,
    val nusach: String = "sephardi", // ashkenazi, sephardi
    val primaryShita: String = "both", // gra, mga, both
    val darkMode: String = "system", // light, dark, system
    val use24HourFormat: Boolean = true,
    val isFirstLaunch: Boolean = true,
    val persistentNotification: Boolean = true,
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val CITY_ID = stringPreferencesKey("city_id")
        val CITY_NAME_HE = stringPreferencesKey("city_name_he")
        val CITY_NAME_EN = stringPreferencesKey("city_name_en")
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val ELEVATION = doublePreferencesKey("elevation")
        val TIMEZONE_ID = stringPreferencesKey("timezone_id")
        val USE_GPS = booleanPreferencesKey("use_gps")
        val USE_ELEVATION = booleanPreferencesKey("use_elevation")
        val CANDLE_LIGHTING_MIN = intPreferencesKey("candle_lighting_min")
        val NUSACH = stringPreferencesKey("nusach")
        val PRIMARY_SHITA = stringPreferencesKey("primary_shita")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val USE_24H = booleanPreferencesKey("use_24h")
        val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        val PERSISTENT_NOTIFICATION = booleanPreferencesKey("persistent_notification")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            cityId = prefs[Keys.CITY_ID] ?: "jerusalem",
            cityNameHebrew = prefs[Keys.CITY_NAME_HE] ?: "ירושלים",
            cityNameEnglish = prefs[Keys.CITY_NAME_EN] ?: "Jerusalem",
            latitude = prefs[Keys.LATITUDE] ?: 31.778,
            longitude = prefs[Keys.LONGITUDE] ?: 35.235,
            elevation = prefs[Keys.ELEVATION] ?: 800.0,
            timeZoneId = prefs[Keys.TIMEZONE_ID] ?: "Asia/Jerusalem",
            useGps = prefs[Keys.USE_GPS] ?: false,
            useElevation = prefs[Keys.USE_ELEVATION] ?: true,
            candleLightingMinutes = prefs[Keys.CANDLE_LIGHTING_MIN] ?: 20,
            nusach = prefs[Keys.NUSACH] ?: "sephardi",
            primaryShita = prefs[Keys.PRIMARY_SHITA] ?: "both",
            darkMode = prefs[Keys.DARK_MODE] ?: "system",
            use24HourFormat = prefs[Keys.USE_24H] ?: true,
            isFirstLaunch = prefs[Keys.FIRST_LAUNCH] ?: true,
            persistentNotification = prefs[Keys.PERSISTENT_NOTIFICATION] ?: true,
        )
    }

    suspend fun setDefaultCity(
        cityId: String,
        nameHebrew: String,
        nameEnglish: String,
        latitude: Double,
        longitude: Double,
        elevation: Double,
        timeZoneId: String,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CITY_ID] = cityId
            prefs[Keys.CITY_NAME_HE] = nameHebrew
            prefs[Keys.CITY_NAME_EN] = nameEnglish
            prefs[Keys.LATITUDE] = latitude
            prefs[Keys.LONGITUDE] = longitude
            prefs[Keys.ELEVATION] = elevation
            prefs[Keys.TIMEZONE_ID] = timeZoneId
        }
    }

    suspend fun setUseGps(useGps: Boolean) {
        context.dataStore.edit { it[Keys.USE_GPS] = useGps }
    }

    suspend fun setUseElevation(useElevation: Boolean) {
        context.dataStore.edit { it[Keys.USE_ELEVATION] = useElevation }
    }

    suspend fun setCandleLightingMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.CANDLE_LIGHTING_MIN] = minutes }
    }

    suspend fun setNusach(nusach: String) {
        context.dataStore.edit { it[Keys.NUSACH] = nusach }
    }

    suspend fun setPrimaryShita(shita: String) {
        context.dataStore.edit { it[Keys.PRIMARY_SHITA] = shita }
    }

    suspend fun setDarkMode(mode: String) {
        context.dataStore.edit { it[Keys.DARK_MODE] = mode }
    }

    suspend fun setFirstLaunchDone() {
        context.dataStore.edit { it[Keys.FIRST_LAUNCH] = false }
    }

    suspend fun setPersistentNotification(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PERSISTENT_NOTIFICATION] = enabled }
    }

    fun prefsToGeoLocation(prefs: UserPreferences): AppGeoLocation {
        return AppGeoLocation(
            cityNameHebrew = prefs.cityNameHebrew,
            cityNameEnglish = prefs.cityNameEnglish,
            latitude = prefs.latitude,
            longitude = prefs.longitude,
            elevation = prefs.elevation,
            timeZone = TimeZone.getTimeZone(prefs.timeZoneId),
            isFromGps = prefs.useGps,
        )
    }
}
