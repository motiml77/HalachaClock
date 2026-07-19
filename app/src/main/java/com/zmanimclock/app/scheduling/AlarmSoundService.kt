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

    private var previewMode = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent.getLongExtra(EXTRA_ALARM_ID, -1))
            ACTION_PREVIEW -> startPreview(intent)
            ACTION_DISMISS -> dismiss()
            ACTION_SNOOZE -> snooze()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    /**
     * Item I — ring a transient alarm built straight from the editor's current
     * settings, no DB and no scheduling. The full ringing screen opens so the
     * user can hear the real volume/sound and feel the vibration; the math
     * challenge is intentionally skipped so a preview is always easy to stop.
     */
    private fun startPreview(intent: Intent) {
        previewMode = true
        val transient = AlarmEntity(
            id = PREVIEW_ID,
            type = AlarmType.FIXED,
            soundEnabled = intent.getBooleanExtra(EXTRA_PREVIEW_SOUND_ENABLED, true),
            soundUri = intent.getStringExtra(EXTRA_PREVIEW_SOUND_URI),
            volumePercent = intent.getIntExtra(EXTRA_PREVIEW_VOLUME, 100),
            vibrate = intent.getBooleanExtra(EXTRA_PREVIEW_VIBRATE, true),
            ringDurationMinutes = 1, // a preview never rings longer than a minute
            shabbatMode = intent.getBooleanExtra(EXTRA_PREVIEW_SHABBAT, false),
            dismissChallenge = com.zmanimclock.app.feature.alarms.data.DismissChallenge.NONE,
            maxSnoozes = 0,
            label = intent.getStringExtra(EXTRA_PREVIEW_TITLE) ?: "תצוגה מקדימה",
        )
        alarm = transient
        goForeground(
            notificationHelper.buildAlarmNotification(
                alertId = PREVIEW_ID, title = titleOf(transient), timeText = "",
                snoozeMinutes = 0, shabbatMode = transient.shabbatMode, snoozesLeft = 0,
            )
        )
        acquireWakeLock()
        ring(transient)
    }

    private fun start(alarmId: Long) {
        if (alarmId < 0) {
            stopSelf(); return
        }
        // startForegroundService() gives us ~5s to call startForeground —
        // post a placeholder IMMEDIATELY (before any DB work), otherwise a
        // slow query or a deleted alarm crashes with
        // ForegroundServiceDidNotStartInTimeException on real devices.
        goForeground(
            notificationHelper.buildAlarmNotification(
                alertId = alarmId, title = "שעון מעורר", timeText = "",
                snoozeMinutes = 5,
            )
        )
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

    private fun goForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.ALARM_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NotificationHelper.ALARM_NOTIFICATION_ID, notification)
        }
    }

    private fun ring(alarm: AlarmEntity) {
        // Replace the placeholder posted in start() with the real content
        goForeground(
            notificationHelper.buildAlarmNotification(
                alertId = alarm.id,
                title = titleOf(alarm),
                timeText = timeTextOf(alarm),
                snoozeMinutes = alarm.snoozeMinutes,
                challenge = alarm.dismissChallenge.name,
                shabbatMode = alarm.shabbatMode,
                snoozesLeft = snoozesLeft(alarm),
            )
        )

        if (alarm.soundEnabled) startSound(alarm)
        if (alarm.vibrate) startVibration()
        handler.postDelayed(autoSilence, alarm.ringDurationMinutes.coerceIn(1, 30) * 60_000L)
        Log.i(TAG, "Ringing alarm ${alarm.id} ('${titleOf(alarm)}')")
    }

    private fun startSound(alarm: AlarmEntity) {
        targetVolume = (alarm.volumePercent.coerceIn(10, 100)) / 100f
        currentVolume = (targetVolume * 0.15f).coerceAtLeast(0.05f)

        // Custom URI first; if it vanished (file deleted / permission lost),
        // FALL BACK to the system default — a silent alarm is the worst bug.
        val candidates = listOfNotNull(
            alarm.soundUri?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in candidates) {
            if (tryPlay(uri)) {
                handler.postDelayed(volumeRampStep, VOLUME_STEP_INTERVAL_MS)
                return
            }
        }
        Log.e(TAG, "All sound sources failed — vibration only")
    }

    private fun tryPlay(uri: Uri): Boolean = try {
        mediaPlayer?.release()
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
        true
    } catch (e: Exception) {
        Log.w(TAG, "Sound source failed: $uri (${e.message})")
        mediaPlayer?.release()
        mediaPlayer = null
        false
    }

    /** -1 = unlimited; otherwise remaining snoozes for this firing. */
    private fun snoozesLeft(alarm: AlarmEntity): Int =
        if (alarm.maxSnoozes < 0) -1 else (alarm.maxSnoozes - alarm.snoozeCount).coerceAtLeast(0)

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
        if (previewMode) {
            Log.i(TAG, "Preview dismissed")
            stopRinging(); stopSelf(); return
        }
        val a = alarm
        Log.i(TAG, "Alarm ${a?.id} acknowledged")
        stopRinging()
        if (a != null) {
            scope.launch { alarmDao.setSnoozeCount(a.id, 0) }
            // B1: schedule a wake-up check if enabled
            if (a.wakeCheckMinutes > 0) {
                WakeCheckReceiver.schedule(this, a.id, a.wakeCheckMinutes)
            }
        }
        stopSelf()
    }

    private fun snooze() {
        if (previewMode) { // a preview self-silences instead of snoozing
            stopRinging(); stopSelf(); return
        }
        val a = alarm ?: run { stopRinging(); stopSelf(); return }
        // B3: enforce the snooze limit (maxSnoozes: -1 = unlimited, 0 = none)
        if (a.maxSnoozes in 0..a.snoozeCount) {
            Log.i(TAG, "Alarm ${a.id} snooze limit reached (${a.maxSnoozes}) — ignoring")
            return // keep ringing; the user must acknowledge
        }
        stopRinging()
        scope.launch { alarmDao.setSnoozeCount(a.id, a.snoozeCount + 1) }
        alarmScheduler.scheduleSnooze(a.id, a.snoozeMinutes)
        Log.i(TAG, "Alarm ${a.id} snoozed for ${a.snoozeMinutes} min (#${a.snoozeCount + 1})")
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
        const val ACTION_PREVIEW = "com.zmanimclock.app.alarm.PREVIEW"
        const val ACTION_DISMISS = "com.zmanimclock.app.alarm.DISMISS"
        const val ACTION_SNOOZE = "com.zmanimclock.app.alarm.SNOOZE"

        /** Sentinel id for the transient preview alarm (item I). */
        const val PREVIEW_ID = -100L

        const val EXTRA_PREVIEW_SOUND_ENABLED = "preview_sound_enabled"
        const val EXTRA_PREVIEW_SOUND_URI = "preview_sound_uri"
        const val EXTRA_PREVIEW_VOLUME = "preview_volume"
        const val EXTRA_PREVIEW_VIBRATE = "preview_vibrate"
        const val EXTRA_PREVIEW_SHABBAT = "preview_shabbat"
        const val EXTRA_PREVIEW_TITLE = "preview_title"

        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TIME_TEXT = "time_text"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        const val EXTRA_CHALLENGE = "challenge"
        const val EXTRA_SHABBAT = "shabbat_mode"
        const val EXTRA_SNOOZES_LEFT = "snoozes_left"

        private const val VOLUME_STEP = 0.09f
        private const val VOLUME_STEP_INTERVAL_MS = 6_000L // target in ~1 minute
    }
}
