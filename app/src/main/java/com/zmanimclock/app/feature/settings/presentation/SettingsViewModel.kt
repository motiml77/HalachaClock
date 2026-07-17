package com.zmanimclock.app.feature.settings.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zmanimclock.app.feature.alarms.data.AlarmDao
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.alarms.data.AlarmType
import com.zmanimclock.app.feature.settings.data.UserPreferences
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.scheduling.RescheduleWorker
import com.zmanimclock.app.scheduling.StatusNotificationReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepository: UserPreferencesRepository,
    private val alarmDao: AlarmDao,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = prefsRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setCandleLightingMinutes(minutes: Int) {
        viewModelScope.launch { prefsRepository.setCandleLightingMinutes(minutes) }
    }

    fun setPersistentNotification(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setPersistentNotification(enabled)
            StatusNotificationReceiver.ping(context)
        }
    }

    /**
     * Reliability self-test (A6): one-time alarm one minute from now, using
     * the exact production pipeline — deactivates itself after ringing.
     */
    fun ringInAMinute() {
        viewModelScope.launch {
            val fire = LocalDateTime.now().plusMinutes(1)
            alarmDao.insertAlarm(
                AlarmEntity(
                    type = AlarmType.FIXED,
                    hour = fire.hour,
                    minute = fire.minute,
                    daysOfWeek = 0, // one-time
                    label = "בדיקת צלצול",
                    ringDurationMinutes = 1,
                )
            )
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<RescheduleWorker>().build())
        }
    }
}
