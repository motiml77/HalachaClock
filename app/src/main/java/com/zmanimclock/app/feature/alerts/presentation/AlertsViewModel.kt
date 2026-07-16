package com.zmanimclock.app.feature.alerts.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zmanimclock.app.feature.alerts.data.local.AlertDao
import com.zmanimclock.app.feature.alerts.data.local.AlertEntity
import com.zmanimclock.app.scheduling.AlarmScheduler
import com.zmanimclock.app.scheduling.RescheduleWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alertDao: AlertDao,
    private val alarmScheduler: AlarmScheduler,
) : ViewModel() {

    val alerts: StateFlow<List<AlertEntity>> = alertDao.getAllAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addAlert(draft: AlertDraft) {
        viewModelScope.launch {
            alertDao.insertAlert(
                AlertEntity(
                    zmanId = draft.kind.name,
                    offsetMinutes = draft.offsetMinutes,
                    offsetBefore = draft.offsetBefore,
                    useSound = draft.fullScreenAlarm,
                    useVibration = draft.vibrate,
                    isFullScreenAlarm = draft.fullScreenAlarm,
                    skipShabbat = draft.skipShabbat,
                    skipYomTov = draft.skipYomTov,
                    snoozeDurationMinutes = draft.snoozeMinutes,
                )
            )
            requestReschedule()
        }
    }

    fun toggleAlert(alert: AlertEntity, isActive: Boolean) {
        viewModelScope.launch {
            alertDao.toggleAlert(alert.id, isActive)
            if (!isActive) alarmScheduler.cancelAlert(alert.id)
            requestReschedule()
        }
    }

    fun deleteAlert(alert: AlertEntity) {
        viewModelScope.launch {
            alarmScheduler.cancelAlert(alert.id)
            alertDao.deleteAlert(alert)
        }
    }

    private fun requestReschedule() {
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<RescheduleWorker>().build())
    }
}
