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
import com.zmanimclock.app.scheduling.AlarmSoundService
import com.zmanimclock.app.scheduling.RescheduleWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.zmanimclock.app.feature.zmanim.format.asZmanTime
import java.time.LocalDate
import java.time.ZoneId
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
            // Shabbat entry: every Friday, 5 minutes before the location's
            // shkia, with its own special sound and the candles screen.
            // Five rather than four by the owner's ruling — this alarm is the
            // last warning before shkia itself, so the extra minute is the
            // side to err on.
            _alarm.value = AlarmEntity(
                type = AlarmType.ZMAN,
                zmanId = "SHKIA",
                offsetMinutes = SHABBAT_ENTRY_OFFSET_MINUTES,
                offsetBefore = true,
                daysOfWeek = AlarmEntity.FRIDAY_ONLY,
                shabbatMode = true,
                label = "כניסת שבת",
                ringDurationSeconds = 60,
            )
            refreshPreview()
        } else {
            _alarm.value = AlarmEntity(
                type = type,
                zmanId = preselectedZman ?: "HANETZ",
                offsetMinutes = if (type == AlarmType.ZMAN) 30 else 0,
                // הדלקת נרות and צאת שבת are the only two zmanim that do not
                // exist every day (relevantTimedZmanim hides them the rest of
                // the week) — but AlarmEntity's own default is ALL_DAYS, and
                // this branch never overrode it. Tapping the בell next to
                // הדלקת נרות (visible only on Friday) and saving without
                // touching the day picker used to create an alarm that
                // showed "כל יום" and rang every single evening, since the
                // ENGINE still computes both unconditionally for every date —
                // only their day-of-week RELEVANCE is filtered, at display
                // time. Seed the one sensible day here so the default the
                // user is most likely to just save is the correct one.
                daysOfWeek = when (preselectedZman) {
                    "CANDLE_LIGHTING" -> AlarmEntity.FRIDAY_ONLY
                    "TZEIT_SHABBAT" -> AlarmEntity.SATURDAY_ONLY
                    else -> AlarmEntity.ALL_DAYS
                },
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

    /**
     * Item I — "תצוגה מקדימה לשעון": ring right now, exactly as this alarm
     * would, using the current (possibly unsaved) sound/volume/vibrate/screen
     * settings. Runs through the real [AlarmSoundService] preview path so the
     * user hears the true volume and sees the real ringing screen; it never
     * touches the database or the schedule.
     */
    fun previewAlarm() {
        val a = _alarm.value
        val title = a.label.ifBlank { defaultAlarmLabel(a) }

        // 1) The sound/vibration/ramp via the real service (preview path).
        val svc = android.content.Intent(context, AlarmSoundService::class.java).apply {
            action = AlarmSoundService.ACTION_PREVIEW
            putExtra(AlarmSoundService.EXTRA_PREVIEW_SOUND_ENABLED, a.soundEnabled)
            putExtra(AlarmSoundService.EXTRA_PREVIEW_SOUND_URI, a.soundUri)
            putExtra(AlarmSoundService.EXTRA_PREVIEW_VOLUME, a.volumePercent)
            putExtra(AlarmSoundService.EXTRA_PREVIEW_GRADUAL, a.gradualVolume)
            putExtra(AlarmSoundService.EXTRA_PREVIEW_VIBRATE, a.vibrate)
            putExtra(AlarmSoundService.EXTRA_PREVIEW_SHABBAT, a.shabbatMode)
            putExtra(AlarmSoundService.EXTRA_PREVIEW_TITLE, title)
        }
        androidx.core.content.ContextCompat.startForegroundService(context, svc)

        // 2) The full designed ringing screen — launched directly so it shows
        //    even while the editor is in the foreground (no math gate for a
        //    preview; אישור stops it). PREVIEW_ID routes dismiss to the
        //    service's preview branch.
        val screen = android.content.Intent(
            context,
            com.zmanimclock.app.feature.alarm.presentation.AlarmActivity::class.java,
        ).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmSoundService.EXTRA_ALARM_ID, AlarmSoundService.PREVIEW_ID)
            putExtra(AlarmSoundService.EXTRA_TITLE, title)
            putExtra(AlarmSoundService.EXTRA_TIME_TEXT, "")
            putExtra(AlarmSoundService.EXTRA_SHABBAT, a.shabbatMode)
            putExtra(AlarmSoundService.EXTRA_SNOOZES_LEFT, 0)
            putExtra(AlarmSoundService.EXTRA_SNOOZE_MINUTES, 0)
        }
        context.startActivity(screen)
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val a = _alarm.value
            // Blank name → sensible default ("זמן ק"ש מג"א", "השכמה"…)
            val edited = if (a.label.isBlank()) a.copy(label = defaultAlarmLabel(a)) else a

            // insertAlarm REPLACEs the whole row, so writing the snapshot the
            // editor opened with would clobber anything that changed while it
            // was open (the alarm rang and bumped snoozeCount, the user toggled
            // it off from the list, a skip was set…). Re-read the row and copy
            // only the fields this screen actually edits.
            val current = if (edited.id != 0L) alarmDao.getAlarmById(edited.id) else null
            // skipUntilEpochMs is an ABSOLUTE EPOCH watermark computed from
            // the OLD fire time when the user tapped "דלג על הבאה" — not in
            // this whitelist, so it survives untouched by default, which is
            // right when nothing about the timing changed. But every field
            // that actually MOVES the fire time IS in this whitelist, and
            // used to be written straight over it: change the hour from 6:30
            // to 7:00 after skipping tomorrow's 6:30, and the stale
            //6:31-watermark no longer suppresses tomorrow's (now later)
            // 7:00 occurrence — it gets armed anyway. The card then showed
            // both "מחר · 07:00" AND "מדלג על הבאה — בטל" at once, and the
            // alarm rang despite it.
            val timingChanged = current != null && (
                current.type != edited.type ||
                    current.hour != edited.hour ||
                    current.minute != edited.minute ||
                    current.zmanId != edited.zmanId ||
                    current.offsetMinutes != edited.offsetMinutes ||
                    current.offsetBefore != edited.offsetBefore ||
                    current.daysOfWeek != edited.daysOfWeek
                )
            val toSave = current?.copy(
                type = edited.type,
                hour = edited.hour,
                minute = edited.minute,
                zmanId = edited.zmanId,
                offsetMinutes = edited.offsetMinutes,
                offsetBefore = edited.offsetBefore,
                daysOfWeek = edited.daysOfWeek,
                skipUntilEpochMs = if (timingChanged) 0L else current.skipUntilEpochMs,
                skipShabbat = edited.skipShabbat,
                skipYomTov = edited.skipYomTov,
                soundEnabled = edited.soundEnabled,
                soundUri = edited.soundUri,
                volumePercent = edited.volumePercent,
                gradualVolume = edited.gradualVolume,
                ringDurationSeconds = edited.ringDurationSeconds,
                vibrate = edited.vibrate,
                dismissChallenge = edited.dismissChallenge,
                snoozeMinutes = edited.snoozeMinutes,
                maxSnoozes = edited.maxSnoozes,
                wakeCheckMinutes = edited.wakeCheckMinutes,
                label = edited.label,
            ) ?: edited

            alarmDao.insertAlarm(toSave)
            com.zmanimclock.app.scheduling.RescheduleWorker.enqueueUnique(context)
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
            val fire = alarmScheduler.zmanInstantFor(
                a, location, cityId, tomorrow,
                offsets = com.zmanimclock.app.scheduling.ZmanOffsets.from(prefs),
            )
            _zmanPreview.value = fire?.let {
                "מחר: ${it.asZmanTime(zone)}"
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

/**
 * How many minutes before the location's sunset the Shabbat-entry alarm
 * rings. ONE definition, read by both the preset that builds the alarm and
 * the chooser sheet that describes it — the sheet used to carry its own
 * literal, said "4", and was wrong; the alarm has always rung at 5.
 */
internal const val SHABBAT_ENTRY_OFFSET_MINUTES = 5
