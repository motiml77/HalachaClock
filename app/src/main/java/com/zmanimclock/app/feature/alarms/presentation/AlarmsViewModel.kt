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

/** When the alarm's next ring falls — drives the list's grouping tags. */
enum class FireBucket { TODAY, TOMORROW, LATER, OFF }

/**
 * One alarm + its computed next fire: a label ("היום: 06:30 · בעוד…"), the
 * absolute instant (to sort within a group) and the [FireBucket] it lands in.
 */
data class AlarmListItem(
    val alarm: AlarmEntity,
    val nextFireLabel: String?,
    val nextFireEpochMs: Long?,
    val bucket: FireBucket,
)

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
            list.map { computeItem(it) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun computeItem(alarm: AlarmEntity): AlarmListItem {
        if (!alarm.isActive) return AlarmListItem(alarm, null, null, FireBucket.OFF)
        return runCatching {
            val prefs = prefsRepository.preferences.first()
            val location = prefsRepository.prefsToGeoLocation(prefs)
            val cityId = if (prefs.useGps) null else prefs.cityId
            val zone = ZoneId.of(prefs.timeZoneId)
            val fire = alarmScheduler.computeNextOccurrence(alarm, location, cityId)
                ?: return AlarmListItem(alarm, null, null, FireBucket.OFF)
            val local = fire.atZone(zone)
            val today = LocalDate.now(zone)
            val bucket = when (local.toLocalDate()) {
                today -> FireBucket.TODAY
                today.plusDays(1) -> FireBucket.TOMORROW
                else -> FireBucket.LATER
            }
            // Later alarms name their weekday so "שבוע הבא" stays concrete
            val day = when (bucket) {
                FireBucket.TODAY -> "היום"
                FireBucket.TOMORROW -> "מחר"
                else -> hebrewWeekday(local.dayOfWeek)
            }
            val time = DateTimeFormatter.ofPattern("HH:mm").format(local)
            AlarmListItem(
                alarm = alarm,
                nextFireLabel = "$day: $time · ${remainingText(fire)}",
                nextFireEpochMs = fire.toEpochMilli(),
                bucket = bucket,
            )
        }.getOrElse { AlarmListItem(alarm, null, null, FireBucket.OFF) }
    }

    private fun hebrewWeekday(d: java.time.DayOfWeek): String = when (d) {
        java.time.DayOfWeek.SUNDAY -> "יום א'"
        java.time.DayOfWeek.MONDAY -> "יום ב'"
        java.time.DayOfWeek.TUESDAY -> "יום ג'"
        java.time.DayOfWeek.WEDNESDAY -> "יום ד'"
        java.time.DayOfWeek.THURSDAY -> "יום ה'"
        java.time.DayOfWeek.FRIDAY -> "יום ו'"
        java.time.DayOfWeek.SATURDAY -> "שבת"
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

    /** B2: toggle skip-next for this alarm. */
    fun toggleSkipNext(alarm: AlarmEntity) {
        viewModelScope.launch {
            if (alarm.skipUntilEpochMs > System.currentTimeMillis()) {
                alarmScheduler.undoSkip(alarm.id)
            } else {
                alarmScheduler.skipNext(alarm.id)
            }
        }
    }

    private fun requestReschedule() {
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<RescheduleWorker>().build())
    }
}
