package com.zmanimclock.app.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zmanimclock.app.feature.settings.data.UserPreferences
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.location.CityRepository
import com.zmanimclock.app.location.model.CityInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CityPickerViewModel @Inject constructor(
    private val cityRepository: CityRepository,
    private val prefsRepository: UserPreferencesRepository,
) : ViewModel() {

    val currentPrefs: StateFlow<UserPreferences> = prefsRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val israeliCities: List<CityInfo> = cityRepository.getIsraeliCities()
    val worldwideCities: List<CityInfo> = cityRepository.getWorldwideCities()

    fun search(query: String): List<CityInfo> = cityRepository.searchCities(query)

    fun selectCity(city: CityInfo) {
        viewModelScope.launch {
            prefsRepository.setDefaultCity(
                cityId = city.id,
                nameHebrew = city.nameHebrew,
                nameEnglish = city.nameEnglish,
                latitude = city.latitude,
                longitude = city.longitude,
                elevation = city.elevation,
                timeZoneId = city.timeZoneId,
            )
        }
    }
}
