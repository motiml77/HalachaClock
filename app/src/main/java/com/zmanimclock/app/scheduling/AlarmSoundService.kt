package com.zmanimclock.app.scheduling

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.getSystemService
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zmanimclock.app.feature.alarms.data.AlarmDao
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.alarms.data.AlarmType
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import javax.inject.Inject

/**
 * Foreground service that owns the ringing alarm.
 *
 * Per-alarm ring configuration (loaded from Room by id):
 *  - custom sound URI (system default when null), ALARM audio stream
 *  - target volume with ramp-up (starts soft, climbs to the target)
 *  - ring duration → self-snooze when unattended
 *  - vibration on/off
 * One-time alarms deactivate themselves after ringing; the reschedule chain
 * is re-enqueued from here for everything else.
 */
@AndroidEntryPoint
class AlarmSoundService : Service() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var alarmDao: AlarmDao

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var alarm: AlarmEntity? = null
    private var currentVolume = 0f
    private var targetVolume = 1f

    private val volumeRampStep = object : Runnable {
        override fun run() {
            currentVolume = (currentVolume + VOLUME_STEP).coerceAtMost(targetVolume)
            try {
                mediaPlayer?.setVolume(currentVolume, currentVolume)
            } catch (_: IllegalStateException) {
            }
            if (currentVolume < targetVolume) {
                handler.postDelayed(this, VOLUME_STEP_INTERVAL_MS)
            }
        }
    }

    private val autoSilence = Runnable {
        Log.i(TAG, "Ring duration elapsed — self-snoozing")
        snooze()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent.getLongExtra(EXTRA_ALARM_ID, -1))
            ACTION_DISMISS -> dismiss()
            ACTION_SNOOZE -> snooze()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(alarmId: Long) {
        if (alarmId < 0) {
            stopSelf(); return
        }
        acquireWakeLock()
        scope.launch {
            val loaded = alarmDao.getAlarmById(alarmId)
            if (loaded == null) {
                Log.w(TAG, "Alarm $alarmId vanished"); stopSelf(); return@launch
            }
            alarm = loaded
            ring(loaded)
            // One-time alarms are spent once they ring
            if (loaded.isOneTime) alarmDao.setActive(loaded.id, false)
            // Re-arm the chain for every other active alarm (incl. this one's next day)
            WorkManager.getInstance(this@AlarmSoundService)
                .enqueue(OneTimeWorkRequestBuilder<RescheduleWorker>().build())
        }
    }

    private fun ring(alarm: AlarmEntity) {
        val notification = notificationHelper.buildAlarmNotification(
            alertId = alarm.id,
            title = titleOf(alarm),
            timeText = timeTextOf(alarm),
            snoozeMinutes = alarm.snoozeMinutes,
            challenge = alarm.dismissChallenge.name,
            shabbatMode = alarm.shabbatMode,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.ALARM_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NotificationHelper.ALARM_NOTIFICATION_ID, notification)
        }

        if (alarm.soundEnabled) startSound(alarm)
        if (alarm.vibrate) startVibration()
        handler.postDelayed(autoSilence, alarm.ringDurationMinutes.coerceIn(1, 30) * 60_000L)
        Log.i(TAG, "Ringing alarm ${alarm.id} ('${titleOf(alarm)}')")
    }

    private fun startSound(alarm: AlarmEntity) {
        try {
            val uri: Uri = alarm.soundUri?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return

            targetVolume = (alarm.volumePercent.coerceIn(10, 100)) / 100f
            currentVolume = (targetVolume * 0.15f).coerceAtLeast(0.05f)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmSoundService, uri)
                isLooping = true
                setVolume(currentVolume, currentVolume)
                prepare()
                start()
            }
            handler.postDelayed(volumeRampStep, VOLUME_STEP_INTERVAL_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start alarm sound", e)
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService<Vibrator>()
        }
        val pattern = longArrayOf(0, 700, 400, 700, 800)
        vibrator?.vibrate(
            VibrationEffect.createWaveform(pattern, 0),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
        )
    }

    private fun dismiss() {
        Log.i(TAG, "Alarm ${alarm?.id} acknowledged")
        stopRinging()
        stopSelf()
    }

    private fun snooze() {
        val a = alarm
        stopRinging()
        if (a != null) {
            alarmScheduler.scheduleSnooze(a.id, a.snoozeMinutes)
            Log.i(TAG, "Alarm ${a.id} snoozed for ${a.snoozeMinutes} minutes")
        }
        stopSelf()
    }

    private fun stopRinging() {
        handler.removeCallbacksAndMessages(null)
        try {
            mediaPlayer?.stop()
        } catch (_: IllegalStateException) {
        }
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun acquireWakeLock() {
        val pm = getSystemService<PowerManager>() ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zmanimclock:alarm").apply {
            acquire(35 * 60_000L)
        }
    }

    override fun onDestroy() {
        stopRinging()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun titleOf(alarm: AlarmEntity): String {
        if (alarm.shabbatMode) return "שבת נכנסת!"
        if (alarm.label.isNotBlank()) return alarm.label
        return when (alarm.type) {
            AlarmType.FIXED -> "שעון מעורר"
            AlarmType.ZMAN -> {
                val kind = ZmanKind.fromNameOrNull(alarm.zmanId)
                val name = kind?.hebrewName ?: alarm.zmanId
                if (alarm.offsetMinutes == 0) {
                    name
                } else {
                    "${alarm.offsetMinutes} דק' ${if (alarm.offsetBefore) "לפני" else "אחרי"} $name"
                }
            }
        }
    }

    private fun timeTextOf(alarm: AlarmEntity): String = when (alarm.type) {
        AlarmType.FIXED -> "%02d:%02d".format(alarm.hour, alarm.minute)
        AlarmType.ZMAN ->
            DateTimeFormatter.ofPattern("HH:mm")
                .format(java.time.Instant.now().atZone(ZoneId.systemDefault()))
    }

    companion object {
        private const val TAG = "AlarmSoundService"

        const val ACTION_START = "com.zmanimclock.app.alarm.START"
        const val ACTION_DISMISS = "com.zmanimclock.app.alarm.DISMISS"
        const val ACTION_SNOOZE = "com.zmanimclock.app.alarm.SNOOZE"

        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TIME_TEXT = "time_text"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        const val EXTRA_CHALLENGE = "challenge"
        const val EXTRA_SHABBAT = "shabbat_mode"

        private const val VOLUME_STEP = 0.09f
        private const val VOLUME_STEP_INTERVAL_MS = 6_000L // target in ~1 minute
    }
}
