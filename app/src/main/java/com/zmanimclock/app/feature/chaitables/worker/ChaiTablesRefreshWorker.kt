package com.zmanimclock.app.feature.chaitables.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.zmanimclock.app.feature.chaitables.data.ChaiTablesRepository
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Background worker that pre-fetches ChaiTables visible sunrise data.
 *
 * Since sunrise data is cached by Gregorian day-of-year and is valid forever,
 * this worker only needs to run ONCE per location to populate the cache.
 * It runs periodically just to ensure data is available if the user changed
 * cities or if the initial fetch failed.
 */
@HiltWorker
class ChaiTablesRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val chaiTablesRepository: ChaiTablesRepository,
    private val prefsRepository: UserPreferencesRepository,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ChaiTablesWorker"
        const val WORK_NAME = "chai_tables_refresh"
    }

    override suspend fun doWork(): Result {
        return try {
            val prefs = prefsRepository.preferences.first()
            val location = prefsRepository.prefsToGeoLocation(prefs)
            val cityId = if (prefs.useGps) null else prefs.cityId

            val jewishCal = JewishCalendar()
            val currentYear = jewishCal.getJewishYear()

            Log.i(TAG, "Starting ChaiTables refresh for $cityId")

            // Fetch data (one-time per location, cached permanently)
            val success = chaiTablesRepository.prefetchForLocation(location, cityId, currentYear)

            if (success) {
                Log.i(TAG, "ChaiTables data ready for $cityId")
                Result.success()
            } else {
                Log.w(TAG, "ChaiTables fetch failed, will retry")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ChaiTables refresh error", e)
            Result.retry()
        }
    }
}
