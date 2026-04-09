package com.zmanimclock.app.feature.zmanim.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zmanimclock.app.feature.alerts.data.local.AlertDao
import com.zmanimclock.app.feature.zmanim.data.ZmanimCalculator
import com.zmanimclock.app.feature.zmanim.data.model.ZmanCategory
import com.zmanimclock.app.feature.zmanim.data.model.ZmanId
import com.zmanimclock.app.feature.zmanim.data.model.ZmanTime
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.location.LocationProvider
import com.zmanimclock.app.location.model.AppGeoLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ZmanimUiState(
    val currentTime: String = "",
    val hebrewDate: String = "",
    val secularDate: String = "",
    val locationName: String = "לא נבחר מיקום",
    val zmanimByCategory: Map<ZmanCategory, List<ZmanTime>> = emptyMap(),
    val nextZman: ZmanTime? = null,
    val countdownToNext: String = "",
    val holiday: String? = null,
    val shaahZmanisGra: String = "",
    val shaahZmanisMga: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedInfoZman: ZmanId? = null,
)

@HiltViewModel
class ZmanimViewModel @Inject constructor(
    private val zmanimCalculator: ZmanimCalculator,
    private val locationProvider: LocationProvider,
    private val alertDao: AlertDao,
    private val prefsRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ZmanimUiState())
    val uiState: StateFlow<ZmanimUiState> = _uiState.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private var currentLocation: AppGeoLocation? = null

    // Default enabled zmanim
    private val enabledZmanim: Set<ZmanId> = ZmanId.entries.filter { it.defaultEnabled }.toSet()

    init {
        loadZmanim()
        startClock()
    }

    private fun loadZmanim() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val prefs = prefsRepository.preferences.first()
            val location = if (prefs.useGps) {
                locationProvider.getCurrentLocation()
                    ?: prefsRepository.prefsToGeoLocation(prefs)
            } else {
                prefsRepository.prefsToGeoLocation(prefs)
            }
            currentLocation = location

            try {
                val dayZmanim = zmanimCalculator.calculateZmanim(
                    location = location,
                    useElevation = prefs.useElevation,
                    candleLightingOffset = prefs.candleLightingMinutes.toDouble(),
                )
                val holiday = zmanimCalculator.getHebrewHoliday()

                // Filter to only enabled zmanim
                val filteredZmanim = dayZmanim.zmanim.filter { it.id in enabledZmanim }

                // Group by category
                val grouped = filteredZmanim.groupBy { it.id.category }

                // Get alerts status
                val activeAlerts = alertDao.getActiveAlertsList()
                val alertZmanIds = activeAlerts.map { it.zmanId }.toSet()

                val zmanimWithAlerts = grouped.mapValues { (_, zmanim) ->
                    zmanim.map { zman ->
                        zman.copy(hasAlert = zman.id.name in alertZmanIds)
                    }
                }

                val nextZman = filteredZmanim.firstOrNull { it.isNext }

                _uiState.update {
                    it.copy(
                        hebrewDate = dayZmanim.hebrewDate,
                        secularDate = dateFormat.format(dayZmanim.date),
                        locationName = dayZmanim.locationName,
                        zmanimByCategory = zmanimWithAlerts,
                        nextZman = nextZman,
                        holiday = holiday,
                        shaahZmanisGra = formatDuration(dayZmanim.shaahZmanisGra),
                        shaahZmanisMga = formatDuration(dayZmanim.shaahZmanisMga),
                        isLoading = false,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "שגיאה בחישוב הזמנים: ${e.message}")
                }
            }
        }
    }

    private fun startClock() {
        viewModelScope.launch {
            while (true) {
                val now = Date()
                val countdown = _uiState.value.nextZman?.time?.let { nextTime ->
                    val diff = nextTime.time - now.time
                    if (diff > 0) formatCountdown(diff) else "עבר"
                } ?: ""

                _uiState.update {
                    it.copy(
                        currentTime = timeFormat.format(now),
                        countdownToNext = countdown,
                    )
                }
                delay(1000)
            }
        }
    }

    fun showZmanInfo(zmanId: ZmanId) {
        _uiState.update { it.copy(selectedInfoZman = zmanId) }
    }

    fun dismissZmanInfo() {
        _uiState.update { it.copy(selectedInfoZman = null) }
    }

    fun refresh() {
        loadZmanim()
    }

    private fun formatDuration(millis: Long): String {
        if (millis == Long.MIN_VALUE) return "--"
        val totalMinutes = millis / 60000
        val seconds = (millis % 60000) / 1000
        return "${totalMinutes}:${String.format("%02d", seconds)}"
    }

    private fun formatCountdown(millis: Long): String {
        val hours = millis / 3600000
        val minutes = (millis % 3600000) / 60000
        val seconds = (millis % 60000) / 1000
        return if (hours > 0) {
            "${hours}:${String.format("%02d", minutes)}:${String.format("%02d", seconds)}"
        } else {
            "${minutes}:${String.format("%02d", seconds)}"
        }
    }
}
