package com.zmanimclock.app.feature.alarms.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zmanimclock.app.feature.alarms.data.AlarmDao
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.scheduling.AlarmScheduler
import com.zmanimclock.app.scheduling.RescheduleWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** One alarm + its computed next-fire label ("מחר: 05:15" / "היום: 06:30"). */
data class AlarmListItem(val alarm: AlarmEntity, val nextFireLabel: String?)

@HiltViewModel
class AlarmsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler,
    private val prefsRepository: UserPreferencesRepository,
) : ViewModel() {

    /** Re-emits every minute so the "בעוד X ש' Y דק'" labels stay fresh. */
    private val minuteTicker = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(60_000)
        }
    }

    val alarms: StateFlow<List<AlarmListItem>> =
        kotlinx.coroutines.flow.combine(alarmDao.getAllAlarms(), minuteTicker) { list, _ ->
            list.map { AlarmListItem(it, nextFireLabel(it)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun nextFireLabel(alarm: AlarmEntity): String? {
        if (!alarm.isActive) return null
        return runCatching {
            val prefs = prefsRepository.preferences.first()
            val location = prefsRepository.prefsToGeoLocation(prefs)
            val cityId = if (prefs.useGps) null else prefs.cityId
            val zone = ZoneId.of(prefs.timeZoneId)
            val fire = alarmScheduler.computeNextOccurrence(alarm, location, cityId) ?: return null
            val local = fire.atZone(zone)
            val day = when (local.toLocalDate()) {
                LocalDate.now(zone) -> "היום"
                LocalDate.now(zone).plusDays(1) -> "מחר"
                else -> DateTimeFormatter.ofPattern("dd/MM").format(local)
            }
            val time = DateTimeFormatter.ofPattern("HH:mm").format(local)
            "$day: $time · ${remainingText(fire)}"
        }.getOrNull()
    }

    /** "בעוד 9 ש' ו-33 דק'" — the user always sees how far the alarm is. */
    private fun remainingText(fire: java.time.Instant): String {
        val minutes = java.time.Duration.between(java.time.Instant.now(), fire)
            .toMinutes().coerceAtLeast(0)
        val h = minutes / 60
        val m = minutes % 60
        // U+05BE (maqaf) keeps the hyphen RTL-safe next to digits
        return when {
            h > 0 && m > 0 -> "בעוד $h ש' ו־$m דק'"
            h > 0 -> "בעוד $h ש'"
            else -> "בעוד $m דק'"
        }
    }

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
