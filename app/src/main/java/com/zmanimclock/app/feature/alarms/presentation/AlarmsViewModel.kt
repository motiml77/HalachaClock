package com.zmanimclock.app.feature.alarms.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zmanimclock.app.feature.alarms.data.AlarmDao
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
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
class AlarmsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler,
) : ViewModel() {

    val alarms: StateFlow<List<AlarmEntity>> = alarmDao.getAllAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleAlarm(alarm: AlarmEntity, isActive: Boolean) {
        viewModelScope.launch {
            alarmDao.setActive(alarm.id, isActive)
            if (!isActive) alarmScheduler.cancelAlarm(alarm.id)
            requestReschedule()
        }
    }

    fun deleteAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            alarmScheduler.cancelAlarm(alarm.id)
            alarmDao.deleteAlarm(alarm)
        }
    }

    private fun requestReschedule() {
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<RescheduleWorker>().build())
    }
}
