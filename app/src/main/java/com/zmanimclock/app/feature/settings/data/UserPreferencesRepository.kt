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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

data class UserPreferences(
    val cityId: String = "ירושלים",
    val cityNameHebrew: String = "ירושלים",
    val cityNameEnglish: String = "Jerusalem",
    val latitude: Double = 31.778,
    val longitude: Double = 35.235,
    val elevation: Double = 800.0,
    val timeZoneId: String = "Asia/Jerusalem",
    val useGps: Boolean = false,
    val useElevation: Boolean = true,
    val candleLightingMinutes: Int = 20,
    /** צאת שבת — fixed minutes after shkia. Minhag, not a ruling; the
     *  Ohr HaChaim luach itself prints 30. See MaranZmanimEngine. */
    val tzeitShabbatMinutes: Int = 40,
    /**
     * Which zmanim may appear as the headline "הזמן הבא" — ZmanKind names.
     * EMPTY means no preference: every zman is eligible, which is the
     * original behaviour and the default. A user who only cares about a
     * few zmanim can narrow it so the headline jumps straight to the next
     * one they actually want, skipping the ones in between.
     */
    val nextZmanFilter: Set<String> = emptySet(),
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
    /**
     * Direct-boot shadow (A3): SharedPreferences on device-protected storage
     * hold the fields the scheduler needs, so alarms can be re-armed after a
     * reboot BEFORE first unlock (DataStore CE storage is inaccessible then).
     */
    private val dpsPrefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("scheduling_shadow", Context.MODE_PRIVATE)

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
        val TZEIT_SHABBAT_MIN = intPreferencesKey("tzeit_shabbat_min")
        val NEXT_ZMAN_FILTER = stringPreferencesKey("next_zman_filter")
        val NUSACH = stringPreferencesKey("nusach")
        val PRIMARY_SHITA = stringPreferencesKey("primary_shita")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val USE_24H = booleanPreferencesKey("use_24h")
        val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        val PERSISTENT_NOTIFICATION = booleanPreferencesKey("persistent_notification")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            cityId = prefs[Keys.CITY_ID] ?: "ירושלים",
            cityNameHebrew = prefs[Keys.CITY_NAME_HE] ?: "ירושלים",
            cityNameEnglish = prefs[Keys.CITY_NAME_EN] ?: "Jerusalem",
            latitude = prefs[Keys.LATITUDE] ?: 31.778,
            longitude = prefs[Keys.LONGITUDE] ?: 35.235,
            elevation = prefs[Keys.ELEVATION] ?: 800.0,
            timeZoneId = prefs[Keys.TIMEZONE_ID] ?: "Asia/Jerusalem",
            useGps = prefs[Keys.USE_GPS] ?: false,
            useElevation = prefs[Keys.USE_ELEVATION] ?: true,
            candleLightingMinutes = prefs[Keys.CANDLE_LIGHTING_MIN] ?: 20,
            tzeitShabbatMinutes = prefs[Keys.TZEIT_SHABBAT_MIN] ?: 40,
            nextZmanFilter = prefs[Keys.NEXT_ZMAN_FILTER]
                ?.split(",")?.filter { it.isNotBlank() }?.toSet().orEmpty(),
            nusach = prefs[Keys.NUSACH] ?: "sephardi",
            primaryShita = prefs[Keys.PRIMARY_SHITA] ?: "both",
            darkMode = prefs[Keys.DARK_MODE] ?: "system",
            use24HourFormat = prefs[Keys.USE_24H] ?: true,
            isFirstLaunch = prefs[Keys.FIRST_LAUNCH] ?: true,
            persistentNotification = prefs[Keys.PERSISTENT_NOTIFICATION] ?: true,
        ).also(::mirrorToDps)
    }

    /**
     * The scheduling-critical prefs, safe to read before first unlock. Tries
     * the CE DataStore; if the device is still locked (throws), falls back to
     * the device-protected shadow.
     */
    suspend fun schedulingPreferences(): UserPreferences =
        runCatching { preferences.first() }
            .getOrElse { readDpsShadow() }

    private fun mirrorToDps(p: UserPreferences) {
        dpsPrefs.edit()
            .putString("cityId", p.cityId)
            .putString("cityNameHe", p.cityNameHebrew)
            .putString("cityNameEn", p.cityNameEnglish)
            .putString("lat", p.latitude.toString())
            .putString("lon", p.longitude.toString())
            .putString("elev", p.elevation.toString())
            .putString("tz", p.timeZoneId)
            .putBoolean("useGps", p.useGps)
            .putInt("candle", p.candleLightingMinutes)
            .putInt("tzeitShabbat", p.tzeitShabbatMinutes)
            .putString("nextZmanFilter", p.nextZmanFilter.joinToString(","))
            .putBoolean("persistent", p.persistentNotification)
            .apply()
    }

    private fun readDpsShadow(): UserPreferences = UserPreferences(
        cityId = dpsPrefs.getString("cityId", "ירושלים")!!,
        cityNameHebrew = dpsPrefs.getString("cityNameHe", "ירושלים")!!,
        cityNameEnglish = dpsPrefs.getString("cityNameEn", "Jerusalem")!!,
        latitude = dpsPrefs.getString("lat", "31.778")!!.toDouble(),
        longitude = dpsPrefs.getString("lon", "35.235")!!.toDouble(),
        elevation = dpsPrefs.getString("elev", "800.0")!!.toDouble(),
        timeZoneId = dpsPrefs.getString("tz", "Asia/Jerusalem")!!,
        useGps = dpsPrefs.getBoolean("useGps", false),
        candleLightingMinutes = dpsPrefs.getInt("candle", 20),
        tzeitShabbatMinutes = dpsPrefs.getInt("tzeitShabbat", 40),
        nextZmanFilter = dpsPrefs.getString("nextZmanFilter", "")
            .orEmpty().split(",").filter { it.isNotBlank() }.toSet(),
        persistentNotification = dpsPrefs.getBoolean("persistent", true),
    )

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

    suspend fun setTzeitShabbatMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.TZEIT_SHABBAT_MIN] = minutes }
    }

    suspend fun setNextZmanFilter(kinds: Set<String>) {
        context.dataStore.edit { it[Keys.NEXT_ZMAN_FILTER] = kinds.joinToString(",") }
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
