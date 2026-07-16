package com.zmanimclock.app.feature.settings.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.location.CityRepository
import com.zmanimclock.app.location.model.CityInfo
import com.zmanimclock.app.scheduling.RescheduleWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class CityPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cityRepository: CityRepository,
    private val prefsRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val allCities = flow { emit(cityRepository.allCities()) }

    val cities: StateFlow<List<CityInfo>> =
        combine(allCities, _query.debounce(150)) { all, q ->
            if (q.isBlank()) all
            else all.filter {
                it.nameHebrew.contains(q.trim()) ||
                    it.nameEnglish.contains(q.trim(), ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(q: String) {
        _query.value = q
    }

    fun selectCity(city: CityInfo, onDone: () -> Unit) {
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
            // Re-arm alarms against the new location's zmanim
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<RescheduleWorker>().build())
            onDone()
        }
    }
}
