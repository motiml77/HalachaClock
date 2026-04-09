package com.zmanimclock.app.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zmanimclock.app.feature.settings.data.UserPreferences
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = prefsRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    fun setUseGps(useGps: Boolean) {
        viewModelScope.launch { prefsRepository.setUseGps(useGps) }
    }

    fun setUseElevation(useElevation: Boolean) {
        viewModelScope.launch { prefsRepository.setUseElevation(useElevation) }
    }

    fun setCandleLightingMinutes(minutes: Int) {
        viewModelScope.launch { prefsRepository.setCandleLightingMinutes(minutes) }
    }

    fun setNusach(nusach: String) {
        viewModelScope.launch { prefsRepository.setNusach(nusach) }
    }

    fun setPrimaryShita(shita: String) {
        viewModelScope.launch { prefsRepository.setPrimaryShita(shita) }
    }

    fun setDarkMode(mode: String) {
        viewModelScope.launch { prefsRepository.setDarkMode(mode) }
    }

    fun setPersistentNotification(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setPersistentNotification(enabled) }
    }

    fun setDefaultCity(
        cityId: String,
        nameHebrew: String,
        nameEnglish: String,
        latitude: Double,
        longitude: Double,
        elevation: Double,
        timeZoneId: String,
    ) {
        viewModelScope.launch {
            prefsRepository.setDefaultCity(
                cityId, nameHebrew, nameEnglish, latitude, longitude, elevation, timeZoneId,
            )
        }
    }
}
