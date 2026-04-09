package com.zmanimclock.app.feature.alerts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zmanimclock.app.feature.alerts.data.local.AlertDao
import com.zmanimclock.app.feature.alerts.data.local.AlertEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val alertDao: AlertDao,
) : ViewModel() {

    val alerts: StateFlow<List<AlertEntity>> = alertDao.getAllAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleAlert(id: Long, isActive: Boolean) {
        viewModelScope.launch {
            alertDao.toggleAlert(id, isActive)
        }
    }

    fun deleteAlert(id: Long) {
        viewModelScope.launch {
            alertDao.deleteById(id)
        }
    }

    fun createAlert(
        zmanId: String,
        offsetMinutes: Int = 0,
        offsetBefore: Boolean = true,
        useSound: Boolean = true,
        useVibration: Boolean = true,
        label: String = "",
    ) {
        viewModelScope.launch {
            val alert = AlertEntity(
                zmanId = zmanId,
                offsetMinutes = offsetMinutes,
                offsetBefore = offsetBefore,
                useSound = useSound,
                useVibration = useVibration,
                label = label,
            )
            alertDao.insertAlert(alert)
        }
    }
}
