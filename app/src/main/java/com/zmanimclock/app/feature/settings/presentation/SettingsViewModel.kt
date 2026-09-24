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
import com.zmanimclock.app.feature.womensarea.security.WomensAreaSecurity
import com.zmanimclock.app.feature.womensarea.security.WomensAreaSecurityRepository
import com.zmanimclock.app.scheduling.RescheduleWorker
import com.zmanimclock.app.scheduling.StatusNotificationReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepository: UserPreferencesRepository,
    private val alarmDao: AlarmDao,
    private val omerAlerts: com.zmanimclock.app.feature.omer.OmerAlertManager,
    private val womensAreaSecurity: WomensAreaSecurityRepository,
    billingRepository: com.zmanimclock.app.feature.subscription.BillingRepository,
) : ViewModel() {

    /** Current entitlement, for the subscription card. Decides nothing. */
    val entitlement = billingRepository.entitlement

    val preferences: StateFlow<UserPreferences> = prefsRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    /** Whether the Sefirat HaOmer nightly alert is currently on. */
    val omerAlertEnabled: StateFlow<Boolean> = omerAlerts.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setOmerAlert(enabled: Boolean) {
        viewModelScope.launch { omerAlerts.setEnabled(enabled) }
    }

    /** "איזור נשי" — whether its tab is shown. */
    val womensArea: StateFlow<WomensAreaSecurity> = womensAreaSecurity.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WomensAreaSecurity())

    /**
     * Called only AFTER the device lock was passed, for ON (see
     * rememberWomensAreaToggle). Turning OFF just hides the tab; her entries
     * are left alone (matches how disabling the Omer alert doesn't delete
     * history) — the device lock on the area itself protects them.
     */
    fun setWomensAreaEnabled(enabled: Boolean) {
        viewModelScope.launch { womensAreaSecurity.setEnabled(enabled) }
    }

    fun setTzeitShabbatMinutes(minutes: Int) {
        viewModelScope.launch { prefsRepository.setTzeitShabbatMinutes(minutes) }
    }

    fun setNextZmanFilter(kinds: Set<String>) {
        viewModelScope.launch {
            prefsRepository.setNextZmanFilter(kinds)
            // The headline also lives in the status notification and the
            // widget; both recompute from a ping rather than observing prefs.
            StatusNotificationReceiver.ping(context)
            com.zmanimclock.app.feature.widget.ZmanWidgetProvider.refresh(context)
        }
    }

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
            // FIXED alarms are a wall-clock promise, so AlarmScheduler now
            // resolves their hour/minute in the DEVICE's zone (see
            // AlarmScheduler.zoneFor) — before that fix it used the
            // selected city's zone, which is what this used to compute
            // against. Left as city-zone, this self-test itself reproduces
            // exactly the bug that fix corrected: on a device whose zone
            // differs from the city's (caught live testing against an
            // emulator on UTC with Jerusalem selected), the alarm the
            // scheduler actually arms lands hours away, not one minute.
            val fire = LocalDateTime.now(java.time.ZoneId.systemDefault()).plusMinutes(1)
            alarmDao.insertAlarm(
                AlarmEntity(
                    type = AlarmType.FIXED,
                    hour = fire.hour,
                    minute = fire.minute,
                    daysOfWeek = 0, // one-time
                    label = "בדיקת צלצול",
                    ringDurationSeconds = 60,
                )
            )
            RescheduleWorker.enqueueUnique(context)
        }
    }
}
