package com.zmanimclock.app.feature.alarms.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zmanimclock.app.feature.alarms.data.AlarmDao
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.alarms.data.AlarmType
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.scheduling.AlarmScheduler
import com.zmanimclock.app.scheduling.RescheduleWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class AlarmEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler,
    private val prefsRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _alarm = MutableStateFlow(AlarmEntity())
    val alarm: StateFlow<AlarmEntity> = _alarm.asStateFlow()

    /** Live preview for ZMAN alarms: "מחר: 05:14". */
    private val _zmanPreview = MutableStateFlow<String?>(null)
    val zmanPreview: StateFlow<String?> = _zmanPreview.asStateFlow()

    private var loadedId: Long? = null

    fun initialize(
        type: AlarmType,
        alarmId: Long?,
        preselectedZman: String?,
        shabbatPreset: Boolean = false,
    ) {
        if (loadedId != null || _initialized) return
        _initialized = true
        if (alarmId != null && alarmId >= 0) {
            loadedId = alarmId
            viewModelScope.launch {
                alarmDao.getAlarmById(alarmId)?.let {
                    _alarm.value = it
                    refreshPreview()
                }
            }
        } else if (shabbatPreset) {
            // Shabbat entry: every Friday, 4 minutes before the location's
            // shkia, with its own special sound and the candles screen
            _alarm.value = AlarmEntity(
                type = AlarmType.ZMAN,
                zmanId = "SHKIA",
                offsetMinutes = 4,
                offsetBefore = true,
                daysOfWeek = AlarmEntity.FRIDAY_ONLY,
                shabbatMode = true,
                label = "כניסת שבת",
                ringDurationMinutes = 1,
            )
            refreshPreview()
        } else {
            _alarm.value = AlarmEntity(
                type = type,
                zmanId = preselectedZman ?: "HANETZ",
                offsetMinutes = if (type == AlarmType.ZMAN) 30 else 0,
                skipShabbat = type == AlarmType.ZMAN,
            )
            refreshPreview()
        }
    }

    private var _initialized = false

    fun update(transform: (AlarmEntity) -> AlarmEntity) {
        _alarm.value = transform(_alarm.value)
        refreshPreview()
    }

    /**
     * Switch the alarm's anchor between a fixed clock time and a halachic
     * zman, in place, preserving everything else. Choosing the zman anchor
     * seeds a sensible default offset (30 דק' לפני) so the user only has to
     * pick which zman.
     */
    fun setType(newType: AlarmType) = update { a ->
        when {
            a.type == newType -> a
            newType == AlarmType.ZMAN -> a.copy(
                type = AlarmType.ZMAN,
                offsetMinutes = if (a.offsetMinutes == 0) 30 else a.offsetMinutes,
                offsetBefore = true,
            )
            else -> a.copy(type = AlarmType.FIXED)
        }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val a = _alarm.value
            // Blank name → sensible default ("זמן ק"ש מג"א", "השכמה"…)
            val toSave = if (a.label.isBlank()) a.copy(label = defaultAlarmLabel(a)) else a
            alarmDao.insertAlarm(toSave)
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<RescheduleWorker>().build())
            onDone()
        }
    }

    private fun refreshPreview() {
        val a = _alarm.value
        if (a.type != AlarmType.ZMAN) {
            _zmanPreview.value = null
            return
        }
        viewModelScope.launch {
            val prefs = prefsRepository.preferences.first()
            val location = prefsRepository.prefsToGeoLocation(prefs)
            val cityId = if (prefs.useGps) null else prefs.cityId
            val zone = ZoneId.of(prefs.timeZoneId)
            val tomorrow = LocalDate.now(zone).plusDays(1)
            val fire = alarmScheduler.zmanInstantFor(a, location, cityId, tomorrow)
            _zmanPreview.value = fire?.let {
                "מחר: ${DateTimeFormatter.ofPattern("HH:mm").format(it.atZone(zone))}"
            }
        }
    }
}

/**
 * The name the app suggests for an alarm ("לפי מה שהגיוני"):
 * zman alerts get the anchor description, wake-ups get "השכמה".
 */
fun defaultAlarmLabel(a: AlarmEntity): String = when {
    a.shabbatMode -> "כניסת שבת"
    a.type == AlarmType.ZMAN -> {
        val name = com.zmanimclock.app.feature.zmanim.model.ZmanKind
            .fromNameOrNull(a.zmanId)?.hebrewName ?: a.zmanId
        if (a.offsetMinutes == 0) {
            name
        } else {
            "${a.offsetMinutes} דק' ${if (a.offsetBefore) "לפני" else "אחרי"} $name"
        }
    }
    else -> "השכמה"
}
