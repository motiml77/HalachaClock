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

    /**
     * An uncaught throw here would kill the process WHILE THE ALARM RINGS —
     * the worst possible moment. SupervisorJob alone does not swallow
     * exceptions, so an explicit handler is required.
     */
    private val ringErrorHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Uncaught error in alarm pipeline — keeping the ring alive", e)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + ringErrorHandler)

    /**
     * Outlives the service. dismiss()/snooze() call stopSelf() immediately
     * after persisting the snooze counter; on [scope] that write would be
     * cancelled by onDestroy before it reached Room, making the snooze budget
     * unreliable. These writes must survive the service.
     */
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + ringErrorHandler)
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var alarm: AlarmEntity? = null
    private var currentVolume = 0f
    private var targetVolume = 1f
    /** System ALARM-stream level before we forced it up, to restore on stop. */
    private var savedAlarmVolume: Int? = null

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
        // Ring duration elapsed. NEVER route through snooze() here: with the
        // "no snooze" default, the snooze-limit gate blocked this path and the
        // alarm rang forever. If snoozing is still permitted, auto-snooze
        // (classic missed-alarm behavior); otherwise stop outright — the
        // duration the user set is final.
        val a = alarm
        val canSnooze = a != null && !previewMode &&
            a.maxSnoozes != 0 && (a.maxSnoozes < 0 || a.snoozeCount < a.maxSnoozes)
        if (canSnooze) {
            Log.i(TAG, "Ring duration elapsed — auto-snoozing")
            snooze()
        } else {
            Log.i(TAG, "Ring duration elapsed — stopping")
            stopRinging()
            stopSelf()
        }
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
            ringDurationSeconds = 30, // a preview never rings longer than 30s
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
        // A real alarm always leaves preview mode — otherwise a live preview
        // service would make the real alarm take the preview code paths
        // (no snooze, no wake-check, no reschedule).
        previewMode = false
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
            val loaded = runCatching { alarmDao.getAlarmById(alarmId) }
                .onFailure { Log.e(TAG, "Alarm lookup failed for $alarmId", it) }
                .getOrNull()
            if (loaded == null) {
                Log.w(TAG, "Alarm $alarmId vanished"); stopSelf(); return@launch
            }
            alarm = loaded
            ring(loaded)
            // Everything past this point is bookkeeping — it must never be
            // able to take the ringing alarm down with it.
            runCatching {
                if (loaded.isOneTime) alarmDao.setActive(loaded.id, false)
            }.onFailure { Log.e(TAG, "Failed to deactivate one-time alarm", it) }
            runCatching {
                // WorkManager lives in credential-encrypted storage, so this
                // is unavailable before the first unlock after a reboot.
                WorkManager.getInstance(this@AlarmSoundService)
                    .enqueue(OneTimeWorkRequestBuilder<RescheduleWorker>().build())
            }.onFailure { Log.e(TAG, "Reschedule enqueue failed", it) }
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

        // Open the ringing screen DIRECTLY. The notification's full-screen
        // intent only auto-opens when the screen is off/locked — with the
        // phone unlocked and in use, Android demotes it to a heads-up, so the
        // alarm never covered the screen. A direct start from this foreground
        // service (triggered by a setAlarmClock PendingIntent) covers every
        // state; if an OEM blocks it, the FSI notification stays as fallback.
        runCatching {
            startActivity(
                Intent(this, com.zmanimclock.app.feature.alarm.presentation.AlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_ALARM_ID, alarm.id)
                    putExtra(EXTRA_TITLE, titleOf(alarm))
                    putExtra(EXTRA_TIME_TEXT, timeTextOf(alarm))
                    putExtra(EXTRA_SNOOZE_MINUTES, alarm.snoozeMinutes)
                    putExtra(EXTRA_CHALLENGE, alarm.dismissChallenge.name)
                    putExtra(EXTRA_SHABBAT, alarm.shabbatMode)
                    putExtra(EXTRA_SNOOZES_LEFT, snoozesLeft(alarm))
                }
            )
        }.onFailure { Log.w(TAG, "Direct full-screen start blocked: ${it.message}") }

        if (alarm.soundEnabled) startSound(alarm)
        if (alarm.vibrate) startVibration()
        handler.postDelayed(autoSilence, alarm.ringDurationSeconds.coerceIn(10, 180) * 1_000L)
        Log.i(TAG, "Ringing alarm ${alarm.id} ('${titleOf(alarm)}')")
    }

    private fun startSound(alarm: AlarmEntity) {
        // CRITICAL: force the system ALARM stream up to the chosen level.
        // The alarm stream is independent of the ringer, but if the phone is
        // on vibrate/silent its ALARM volume is often left at 0 — then a
        // MediaPlayer scalar has nothing to amplify and the alarm is silent.
        // Raising it here makes the alarm audible regardless of ringer mode.
        forceAlarmStreamVolume(alarm.volumePercent)

        // The chosen loudness lives ONLY in the stream level above. The
        // MediaPlayer scalar just implements the gentle ramp, always ending
        // at 1.0 — previously the percent was applied twice (stream × scalar),
        // which made the volume slider feel like it did nothing.
        // A preview skips the ramp so the user hears the true loudness now.
        targetVolume = 1f
        currentVolume = if (previewMode) 1f else 0.2f

        // Custom URI first; if it vanished (file deleted / permission lost),
        // FALL BACK to the system default — a silent alarm is the worst bug.
        val candidates = listOfNotNull(
            alarm.soundUri?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        // Off the main thread: a slow content:// ringtone provider must not
        // ANR the service that is currently ringing.
        scope.launch {
            val played = kotlinx.coroutines.withContext(Dispatchers.IO) {
                candidates.firstOrNull { tryPlay(it) }
            }
            if (played != null) {
                handler.postDelayed(volumeRampStep, VOLUME_STEP_INTERVAL_MS)
            } else {
                Log.e(TAG, "All sound sources failed — vibration only")
            }
        }
    }

    /**
     * NOTE: setDataSource/prepare touch a content:// provider and can block;
     * callers run this off the main thread (see [startSound]) so a slow
     * ringtone provider cannot ANR the ringing service.
     */
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

    /**
     * Raise the system ALARM stream so the alarm is heard even on vibrate.
     * Saves the previous level so [restoreAlarmStreamVolume] can put it back
     * when the alarm stops — we don't want to permanently change the setting.
     */
    private fun forceAlarmStreamVolume(volumePercent: Int) {
        val am = getSystemService<android.media.AudioManager>() ?: return
        runCatching {
            val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            if (savedAlarmVolume == null) savedAlarmVolume =
                am.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
            val target = (max * volumePercent.coerceIn(10, 100) / 100).coerceAtLeast(1)
            am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, target, 0)
        }
    }

    private fun restoreAlarmStreamVolume() {
        val prev = savedAlarmVolume ?: return
        savedAlarmVolume = null
        val am = getSystemService<android.media.AudioManager>() ?: return
        runCatching { am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, prev, 0) }
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
            persistScope.launch { runCatching { alarmDao.setSnoozeCount(a.id, 0) } }
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
        persistScope.launch { runCatching { alarmDao.setSnoozeCount(a.id, a.snoozeCount + 1) } }
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
        restoreAlarmStreamVolume()
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

        // Ramp 0.2 → 1.0 in ~20s (ring durations are now 10s–3min, so the old
        // one-minute ramp meant short alarms never reached full loudness)
        private const val VOLUME_STEP = 0.1f
        private const val VOLUME_STEP_INTERVAL_MS = 2_500L
    }
}
