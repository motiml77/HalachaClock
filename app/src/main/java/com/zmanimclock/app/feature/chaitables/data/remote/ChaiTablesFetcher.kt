package com.zmanimclock.app.feature.chaitables.data.remote

import android.util.Log
import com.zmanimclock.app.feature.chaitables.data.model.ChaiTablesParams
import com.zmanimclock.app.feature.chaitables.data.model.VisibleSunriseData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches visible sunrise data from ChaiTables.com.
 *
 * Handles:
 * - HTTP request with proper User-Agent
 * - Search radius auto-detection for non-Israel locations
 * - HTML parsing delegation
 * - Error handling with Result type
 */
@Singleton
class ChaiTablesFetcher @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val urlBuilder: ChaiTablesUrlBuilder,
    private val htmlParser: ChaiTablesHtmlParser,
) {
    companion object {
        private const val TAG = "ChaiTablesFetcher"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"

        // Search radii to try, from smallest to largest
        private val SEARCH_RADII = listOf(
            "0", "0.5", "1", "1.5", "2", "3", "4", "5",
            "6", "7", "8", "9", "10", "11", "12", "13", "14", "15"
        )
    }

    /**
     * Fetch visible sunrise data for the given parameters.
     * For Israel: uses the metro area directly.
     * For worldwide: auto-detects the optimal search radius.
     */
    suspend fun fetch(
        params: ChaiTablesParams,
        locationKey: String,
    ): Result<VisibleSunriseData> = withContext(Dispatchers.IO) {
        try {
            if (params.isIsrael) {
                // Israel: straightforward fetch with radius 2
                fetchSingle(params, locationKey)
            } else {
                // Worldwide: find optimal radius
                fetchWithRadiusSearch(params, locationKey)
            }
        } catch (e: Exception) {
            Log.e(TAG, "ChaiTables fetch failed", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch with a single URL (used for Israel and when radius is known).
     */
    private fun fetchSingle(
        params: ChaiTablesParams,
        locationKey: String,
    ): Result<VisibleSunriseData> {
        val url = urlBuilder.buildUrl(params)
        Log.d(TAG, "Fetching ChaiTables: $url")

        val html = executeRequest(url)
            ?: return Result.failure(Exception("Failed to fetch HTML from ChaiTables"))

        val data = htmlParser.parse(html, params, locationKey)
            ?: return Result.failure(Exception("Failed to parse ChaiTables HTML"))

        return Result.success(data.copy(fetchUrl = url))
    }

    /**
     * For worldwide locations: try increasing radii until valid data is found.
     * First check if data exists at all (radius 15), then find the smallest valid radius.
     */
    private fun fetchWithRadiusSearch(
        params: ChaiTablesParams,
        locationKey: String,
    ): Result<VisibleSunriseData> {
        // First: check if any data exists at the maximum radius
        val maxUrl = urlBuilder.buildUrlWithRadius(params, "15")
        val maxHtml = executeRequest(maxUrl)
        if (maxHtml == null) {
            return Result.failure(Exception("No response from ChaiTables at max radius"))
        }

        val maxData = htmlParser.parse(maxHtml, params, locationKey)
        if (maxData == null || maxData.entries.isEmpty()) {
            return Result.failure(NoDataAvailableException(
                "No ChaiTables data available for location ${params.latitude}, ${params.longitude}"
            ))
        }

        // Data exists at radius 15. Now find the smallest valid radius.
        for (radius in SEARCH_RADII) {
            val url = urlBuilder.buildUrlWithRadius(params, radius)
            val html = executeRequest(url) ?: continue
            val data = htmlParser.parse(html, params, locationKey)
            // A Hebrew year is 353–385 days, so ANY complete table clears
            // this bar; it only rejects a truncated/garbled response. (Full
            // SOLAR-year coverage is asserted in the repository, not here —
            // a non-leap year legitimately returns ~354 rows.)
            if (data != null && data.entries.size >= 300) {
                Log.i(TAG, "Found valid data at radius $radius with ${data.entries.size} entries")
                return Result.success(data.copy(fetchUrl = url))
            }
        }

        // Fallback: use the data from radius 15
        Log.w(TAG, "Using max radius data as fallback (${maxData.entries.size} entries)")
        return Result.success(maxData.copy(fetchUrl = maxUrl))
    }

    /**
     * Execute an HTTP GET request and return the response body as a string.
     */
    private fun executeRequest(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.google.com")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                Log.w(TAG, "HTTP ${response.code} from ChaiTables")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP request failed: ${e.message}")
            null
        }
    }
}

/**
 * Exception indicating that ChaiTables has no data for the requested location.
 */
class NoDataAvailableException(message: String) : Exception(message)
