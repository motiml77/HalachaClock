package com.zmanimclock.app.scheduling

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that owns the ringing alarm.
 *
 * Reliability/UX patterns (after yuriykulikov/AlarmClock, Apache-2.0):
 *  - Partial wake lock for the whole ring duration.
 *  - Sound on the ALARM stream (USAGE_ALARM) — unaffected by media volume.
 *  - Ramp-up volume: starts soft and climbs to full over [VOLUME_RAMP_SECONDS].
 *  - Repeating vibration waveform alongside the sound.
 *  - Auto-timeout after [AUTO_SILENCE_MINUTES] — an unattended alarm snoozes
 *    itself instead of ringing forever.
 *  - Full-screen notification (lock-screen capable) with dismiss/snooze.
 */
@AndroidEntryPoint
class AlarmSoundService : Service() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var alarmScheduler: AlarmScheduler

    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var alertId: Long = -1
    private var title: String = ""
    private var timeText: String = ""
    private var snoozeMinutes: Int = 5

    private var currentVolume = INITIAL_VOLUME

    private val volumeRampStep = object : Runnable {
        override fun run() {
            currentVolume = (currentVolume + VOLUME_STEP).coerceAtMost(1f)
            try {
                mediaPlayer?.setVolume(currentVolume, currentVolume)
            } catch (_: IllegalStateException) {
            }
            if (currentVolume < 1f) {
                handler.postDelayed(this, VOLUME_STEP_INTERVAL_MS)
            }
        }
    }

    private val autoSilence = Runnable {
        Log.i(TAG, "Auto-silence after $AUTO_SILENCE_MINUTES minutes — snoozing")
        snooze()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_DISMISS -> dismiss()
            ACTION_SNOOZE -> snooze()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        alertId = intent.getLongExtra(EXTRA_ALERT_ID, -1)
        title = intent.getStringExtra(EXTRA_TITLE) ?: "זמן הלכתי"
        timeText = intent.getStringExtra(EXTRA_TIME_TEXT) ?: ""
        snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)
        val useVibration = intent.getBooleanExtra(EXTRA_USE_VIBRATION, true)

        val notification = notificationHelper.buildAlarmNotification(
            alertId = alertId,
            title = title,
            timeText = timeText,
            snoozeMinutes = snoozeMinutes,
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

        acquireWakeLock()
        startSound()
        if (useVibration) startVibration()
        handler.postDelayed(autoSilence, AUTO_SILENCE_MINUTES * 60_000L)

        Log.i(TAG, "Ringing alert $alertId ('$title')")
    }

    private fun startSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmSoundService, uri)
                isLooping = true
                setVolume(INITIAL_VOLUME, INITIAL_VOLUME)
                prepare()
                start()
            }
            currentVolume = INITIAL_VOLUME
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
            VibrationEffect.createWaveform(pattern, /* repeat from index */ 0),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
        )
    }

    private fun dismiss() {
        Log.i(TAG, "Alarm $alertId dismissed")
        stopRinging()
        stopSelf()
    }

    private fun snooze() {
        Log.i(TAG, "Alarm $alertId snoozed for $snoozeMinutes minutes")
        stopRinging()
        if (alertId >= 0) {
            alarmScheduler.scheduleSnooze(alertId, title, timeText, snoozeMinutes)
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
            acquire((AUTO_SILENCE_MINUTES + 1) * 60_000L)
        }
    }

    override fun onDestroy() {
        stopRinging()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AlarmSoundService"

        const val ACTION_START = "com.zmanimclock.app.alarm.START"
        const val ACTION_DISMISS = "com.zmanimclock.app.alarm.DISMISS"
        const val ACTION_SNOOZE = "com.zmanimclock.app.alarm.SNOOZE"

        const val EXTRA_ALERT_ID = "alert_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TIME_TEXT = "time_text"
        const val EXTRA_USE_VIBRATION = "use_vibration"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"

        /** Ring at most this long unattended, then self-snooze. */
        const val AUTO_SILENCE_MINUTES = 10L

        private const val INITIAL_VOLUME = 0.15f
        private const val VOLUME_STEP = 0.09f
        private const val VOLUME_STEP_INTERVAL_MS = 6_000L // full volume in ~1 minute
    }
}
