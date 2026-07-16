package com.zmanimclock.app.feature.chaitables.data.remote

import com.zmanimclock.app.feature.chaitables.data.model.ChaiTablesParams
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Constructs the CGI URL for ChaiTables.com requests.
 *
 * Based on the Zemaneh-Yosef open source implementation.
 * See: https://github.com/Zemaneh-Yosef/RabbiOvadiahYosefCalendarAndroidApp
 */
@Singleton
class ChaiTablesUrlBuilder @Inject constructor() {

    companion object {
        private const val BASE_URL = "https://chaitables.com/cgi-bin/ChaiTables.cgi/"
        private const val USER_NUMBER = "413"
    }

    /**
     * Build the full URL for fetching visible sunrise data.
     */
    fun buildUrl(params: ChaiTablesParams): String {
        val sb = StringBuilder(BASE_URL).append("?")

        // Common parameters
        sb.appendParam("cgi_country", params.country)
        sb.appendParam("cgi_USAcities2", "0")
        sb.appendParam("cgi_eroshgt", "0.0")
        sb.appendParam("cgi_geotz", computeTimezoneOffset(params.timeZoneId))
        sb.appendParam("cgi_DST", "ON")
        sb.appendParam("cgi_exactcoord", "OFF")
        sb.appendParam("cgi_types", "0")  // 0 = visible sunrise
        sb.appendParam("cgi_RoundSecond", "1")
        sb.appendParam("cgi_AddCushion", "2")
        sb.appendParam("cgi_24hr", "")
        sb.appendParam("cgi_typezman", "-1")
        sb.appendParam("cgi_yrheb", params.hebrewYear.toString())
        sb.appendParam("cgi_optionheb", "1")
        sb.appendParam("cgi_UserNumber", USER_NUMBER)
        sb.appendParam("cgi_Language", "English")
        sb.appendParam("cgi_AllowShaving", "OFF")

        if (params.isIsrael) {
            // Israeli mode: use metro area (TableType=BY)
            sb.appendParam("cgi_TableType", "BY")
            sb.appendParam("cgi_USAcities1", "1")
            sb.appendParam("cgi_MetroArea", params.metroAreaName ?: "jerusalem")
            sb.appendParam("cgi_searchradius", params.searchRadius)
            sb.appendParam("cgi_eroslatitude", formatCoord(params.latitude))
            sb.appendParam("cgi_eroslongitude", formatCoord(-params.longitude))
        } else {
            // Worldwide mode: use coordinates (TableType=Chai)
            sb.appendParam("cgi_TableType", "Chai")
            sb.appendParam("cgi_USAcities1", "1")
            sb.appendParam("cgi_MetroArea", "jerusalem")  // placeholder for non-Israel
            sb.appendParam("cgi_searchradius", params.searchRadius)
            sb.appendParam("cgi_eroslatitude", formatCoord(params.latitude))
            sb.appendParam("cgi_eroslongitude", formatCoord(-params.longitude))
        }

        return sb.toString()
    }

    /**
     * Build a URL with a specific search radius (for radius auto-detection).
     */
    fun buildUrlWithRadius(params: ChaiTablesParams, radius: String): String {
        val modifiedParams = params.copy(searchRadius = radius)
        return buildUrl(modifiedParams)
    }

    /**
     * Compute raw timezone offset in hours (no DST).
     * ChaiTables expects the standard (non-DST) offset.
     */
    private fun computeTimezoneOffset(timeZoneId: String): String {
        val tz = TimeZone.getTimeZone(timeZoneId)
        val rawOffsetHours = tz.rawOffset / 3_600_000.0
        return String.format(Locale.US, "%.1f", rawOffsetHours)
    }

    private fun formatCoord(value: Double): String {
        return String.format(Locale.US, "%.6f", value)
    }

    private fun StringBuilder.appendParam(key: String, value: String): StringBuilder {
        if (this.last() != '?') append("&")
        append(key).append("=").append(value)
        return this
    }
}
