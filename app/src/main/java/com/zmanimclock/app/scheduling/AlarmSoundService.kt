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
import com.zmanimclock.app.feature.zmanim.format.asZmanTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Foreground service that owns the ringing alarm.
 *
 * Per-alarm ring configuration (loaded from Room by id):
 *  - custom sound URI (system default when null), ALARM audio stream
 *  - target volume, constant by default; an optional per-alarm gentle
 *    climb paced to finish inside the ring duration
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
    /**
     * Written from Dispatchers.IO in [tryPlay] and read/cleared on the main
     * thread in [stopRinging] — @Volatile so the two threads cannot see a
     * stale value. Paired with [stopped] below.
     */
    @Volatile private var mediaPlayer: MediaPlayer? = null
    /**
     * Set the moment the alarm is told to stop.
     *
     * A slow content:// ringtone provider can leave prepare()/start() still
     * running on the IO thread when the user dismisses. Without this flag
     * that thread then publishes a freshly STARTED looping player into a
     * service that has already torn down — nothing is left to release it,
     * and the alarm loops forever with no UI to stop it.
     */
    @Volatile private var stopped = false
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var alarm: AlarmEntity? = null
    // 1f, not 0f: if any future path ever reads this before startSound
    // sets it, the failure mode should be 'full volume' and not a SILENT
    // alarm, which produces no exception and no log line to notice.
    private var currentVolume = 1f
    private var targetVolume = 1f
    /** System ALARM-stream level before we forced it up, to restore on stop. */
    private var savedAlarmVolume: Int? = null
    /** Above-100% boost. Null whenever the device cannot provide it. */
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null
    /** The ringing alarm's chosen loudness, needed when the player is built. */
    private var boostPercent: Int = 100
    /** Ramp pacing for the current alarm; see [rampIntervalFor]. */
    private var rampIntervalMs: Long = VOLUME_STEP_INTERVAL_MS

    private val volumeRampStep = object : Runnable {
        override fun run() {
            currentVolume = (currentVolume + AlarmVolume.VOLUME_STEP).coerceAtMost(targetVolume)
            try {
                mediaPlayer?.setVolume(currentVolume, currentVolume)
            } catch (_: IllegalStateException) {
            }
            if (currentVolume < targetVolume) {
                handler.postDelayed(this, rampIntervalMs)
            }
            // NOTE: the boost is deliberately NOT attached here any more.
            // Hanging it off ramp COMPLETION meant a gradual alarm whose ring
            // duration was shorter than the ramp never got it at all — see
            // startSound.
        }
    }

    /** Attaches the >100% boost, on its own timer for every alarm. */
    private val attachBoostStep = Runnable { attachBoost(mediaPlayer) }

    private val autoSilence = Runnable {
        // Ring duration elapsed. NEVER route through snooze() here: with the
        // "no snooze" default, the snooze-limit gate blocked this path and the
        // alarm rang forever. If snoozing is still permitted, auto-snooze
        // (classic missed-alarm behavior); otherwise stop outright — the
        // duration the user set is final.
        val a = alarm
        val autoSnoozeAllowed = a != null && !previewMode && a.maxSnoozes != 0 && (
            // maxSnoozes < 0 means "unlimited" for the user's OWN button
            // presses (snooze()'s own gate, unchanged below, always lets
            // those through). It must NOT also mean unlimited AUTOMATIC
            // re-rings: an alarm nobody is there to answer would then ring
            // forever, every snoozeMinutes, with nothing to stop it short of
            // physically handling the phone. Cap the unattended case at a
            // fixed number of rounds regardless of the user's own budget.
            if (a!!.maxSnoozes < 0) a.snoozeCount < MAX_UNATTENDED_AUTO_SNOOZE_ROUNDS
            else a.snoozeCount < a.maxSnoozes
        )
        if (autoSnoozeAllowed) {
            Log.i(TAG, "Ring duration elapsed — auto-snoozing")
            performSnooze(a!!)
        } else {
            // The duration the user set governs the NOISE, and only the noise.
            // The occurrence stays open until they acknowledge it: a ring that
            // times out unattended used to also close its own screen, so
            // someone who walked back to their phone found no trace that it
            // had gone off at all.
            Log.i(TAG, "Ring duration elapsed — silencing, screen stays until acknowledged")
            stopRinging()
            // The budget resets here, not in rescheduleAll (which used to reset
            // it unconditionally on every reschedule, for every active alarm,
            // racing a live snooze increment from a completely unrelated
            // trigger — see AlarmScheduler.rescheduleAll for the full story).
            if (previewMode && a != null) {
                // A preview is a demonstration, not an occurrence. Leaving it
                // parked "awaiting acknowledgement" would strand a fake alarm
                // in the notification shade with an אישור button for an alarm
                // that never fired.
                AlarmRingBus.ringEnded(a.id)
                stopSelf()
            } else if (a != null) {
                persistScope.launch { runCatching { alarmDao.setSnoozeCount(a.id, 0) } }
                AlarmRingBus.ringSilenced(a.id)
                // Stay in the foreground with a changed notification rather
                // than stopSelf(): if the user leaves the full-screen activity
                // the notification is the only way back to acknowledge, and a
                // service that stopped would take it with it.
                runCatching {
                    notificationHelper.notifySilencedAwaitingAck(this, a)
                }
            } else {
                stopSelf()
            }
        }
    }

    private var previewMode = false
    /**
     * True when THIS ring is a wake-check's own re-ring (armed by
     * [WakeCheckReceiver] after a previous dismissal). Read by
     * [performDismiss] so dismissing it does not arm YET ANOTHER wake-check —
     * without this a wake-check re-ring's own dismiss looked identical to any
     * other, and re-armed another check, forever: every wakeCheckMinutes +
     * RE_RING_MINUTES, all day, until the user happened to tap "אני ער"
     * inside its 2-minute window or switched the alarm off entirely.
     */
    private var isWakeCheckRering = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(
                intent.getLongExtra(EXTRA_ALARM_ID, -1),
                intent.getBooleanExtra(EXTRA_IS_WAKE_CHECK_RERING, false),
            )
            ACTION_PREVIEW -> startPreview(intent)
            // The alarm id travels on EVERY dismiss/snooze intent (AlarmActivity
            // has always sent it) so a FRESH service instance — recreated after
            // the previous one auto-silenced and stopSelf()'d — can still
            // recover the real alarm from Room instead of silently no-op'ing
            // against a null in-memory field. See dismiss()/snooze() below.
            ACTION_DISMISS -> dismiss(intent.getLongExtra(EXTRA_ALARM_ID, -1))
            ACTION_SNOOZE -> snooze(intent.getLongExtra(EXTRA_ALARM_ID, -1))
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
            gradualVolume = intent.getBooleanExtra(EXTRA_PREVIEW_GRADUAL, false),
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

    private fun start(alarmId: Long, isWakeCheckRering: Boolean = false) {
        if (alarmId < 0) {
            stopSelf(); return
        }
        // A real alarm always leaves preview mode — otherwise a live preview
        // service would make the real alarm take the preview code paths
        // (no snooze, no wake-check, no reschedule).
        previewMode = false
        this.isWakeCheckRering = isWakeCheckRering
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
            // isOneTime is EXCLUDED from this guard on purpose: start() itself
            // deactivates a one-time alarm (isActive=false) right after its
            // first ring, before the user has even had a chance to snooze —
            // so a snoozed one-time alarm's re-ring legitimately arrives here
            // with isActive already false. Rejecting that would silence a
            // snooze the user explicitly asked for.
            if (!loaded.isActive && !loaded.isOneTime) {
                // Defends against a real, observed race: RescheduleWorker runs
                // unsynchronized (multiple instances can overlap — see
                // AlarmScheduler.rescheduleAll), so an in-flight run started
                // BEFORE the user switched this alarm off can still arm it
                // AFTER the switch-off already cancelled it. Re-validating the
                // freshly-loaded row here means a PendingIntent that slipped
                // through that race rings a stale alarm for nobody rather than
                // a real one the user still wants.
                Log.w(TAG, "Alarm $alarmId fired while inactive — ignoring")
                stopSelf(); return@launch
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
                RescheduleWorker.enqueueUnique(this@AlarmSoundService)
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

        // Reset the stop latch HERE, not inside startSound: a vibrate-only
        // alarm never calls startSound, so the flag would keep whatever the
        // previous ring left behind. It happens to be harmless today (only
        // tryPlay reads it, and that is reachable only via startSound), but
        // relying on that is exactly the kind of ordering dependency that
        // turns into a silent alarm the next time this method is edited.
        stopped = false

        if (alarm.soundEnabled) startSound(alarm)
        if (alarm.vibrate) startVibration()
        // Drop any PREVIOUSLY posted deadline before posting this alarm's own.
        // Without this, two alarms firing near the same minute (distinct
        // PendingIntents, both delivered to this one service) each post the
        // SAME Runnable via postDelayed — Handler queues a second Message
        // rather than replacing the first, so both fire, and the EARLIER
        // (often much shorter) deadline silences whichever alarm is actually
        // live by then, regardless of its own configured ring duration.
        handler.removeCallbacks(autoSilence)
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
        boostPercent = alarm.volumePercent

        // The chosen loudness lives ONLY in the stream level above. The
        // MediaPlayer scalar always ENDS at 1.0 — previously the percent was
        // applied twice (stream × scalar), which made the volume slider feel
        // like it did nothing.
        //
        // Constant by default: an alarm exists to wake someone, and a fade
        // makes the first seconds — the ones a deep sleeper most needs — the
        // quietest ones. The gentle climb is now opt-in per alarm.
        //
        // The preview HONOURS the setting rather than forcing constant. It
        // used to always skip the ramp, which made sense when the ramp was
        // unconditional and the preview existed to demonstrate loudness; now
        // that the user chooses, a preview that ignored the choice would
        // simply be showing them the wrong alarm.
        targetVolume = 1f
        val ramp = alarm.gradualVolume
        currentVolume = if (ramp) AlarmVolume.RAMP_START_VOLUME else 1f
        // Pace the climb to THIS alarm's ring duration. At the old fixed
        // 2.5 s per step the climb took 20 s, but the ring duration slider
        // starts at 10 s — so a short gradual alarm stopped while still
        // half-volume, having never reached the loudness the user chose.
        // The ramp now always completes inside the ring.
        rampIntervalMs = AlarmVolume.rampIntervalMs(alarm.ringDurationSeconds)

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
                if (ramp) handler.postDelayed(volumeRampStep, rampIntervalMs)
                // The boost is attached on its own timer in BOTH cases, never
                // off the end of the ramp. Tying it to ramp completion meant a
                // gradual alarm that stopped before the climb finished rang
                // with no boost at all — the user asked for +6 dB and got
                // nothing, silently. A compressor very slightly flattening the
                // climb is a cosmetic cost next to that.
                // The delay lets the output track exist first: attaching a
                // session effect before it does takes AudioFlinger's
                // orphan-chain path, which works but is the fragile one.
                handler.postDelayed(attachBoostStep, BOOST_ATTACH_DELAY_MS)
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
    private fun tryPlay(uri: Uri): Boolean {
        return try {
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
            if (stopped) {
                // Told to stop while this was still preparing. Release the
                // player we just started rather than publishing it into a
                // service that has already torn down — nothing else would ever
                // release it, and it would loop forever with no UI to stop it.
                Log.i(TAG, "Sound became ready after stop — releasing it")
                runCatching { mediaPlayer?.stop() }
                mediaPlayer?.release()
                mediaPlayer = null
                return false
            }
            // The boost is deliberately NOT created here. The catch below
            // swallows Exception, and LoudnessEnhancer's constructor throws
            // RuntimeException — so a device that cannot provide the effect
            // would be treated as a FAILED SOUND SOURCE. Every candidate URI
            // would fall through the same way and the alarm would ring
            // silently. It is attached from attachBoostStep instead, well
            // away from this path.
            true
        } catch (e: Exception) {
            Log.w(TAG, "Sound source failed: $uri (${e.message})")
            releaseBoost()
            mediaPlayer?.release()
            mediaPlayer = null
            false
        }
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
            // 100 and above both mean the hardware maximum; the extra
            // loudness above 100 comes from the LoudnessEnhancer, not from
            // the stream, which has nothing left to give.
            val target = (max * AlarmVolume.streamPercent(volumePercent) / 100)
                .coerceAtLeast(1)
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

    /**
     * [alarmId] recovers the real alarm from Room when this Service instance
     * has no live ring for it — e.g. it already auto-silenced (autoSilence
     * calls stopSelf(), tearing the instance down) and the tap arrived at a
     * freshly-recreated instance whose [alarm] field starts null, from a
     * stale screen still showing the old alarm. The old code only ever read
     * the in-memory field here, so that tap silently did nothing at all: no
     * snooze-count reset, no wake-check armed, and — worst of all — a
     * previously auto-snoozed re-ring was left fully armed, so the alarm the
     * user had just "acknowledged" rang again five minutes later regardless.
     */
    private fun dismiss(alarmId: Long) {
        if (previewMode) {
            Log.i(TAG, "Preview dismissed")
            stopRinging(); stopSelf(); return
        }
        val current = alarm
        if (current != null) {
            performDismiss(current)
            return
        }
        if (alarmId < 0) { stopRinging(); stopSelf(); return }
        scope.launch {
            val loaded = runCatching { alarmDao.getAlarmById(alarmId) }.getOrNull()
            if (loaded != null) performDismiss(loaded) else { stopRinging(); stopSelf() }
        }
    }

    private fun performDismiss(a: AlarmEntity) {
        Log.i(TAG, "Alarm ${a.id} acknowledged")
        stopRinging()
        cancelStuckNotification()
        persistScope.launch { runCatching { alarmDao.setSnoozeCount(a.id, 0) } }
        // A dismissal always clears any pending snooze re-ring. Previously
        // this never happened: if the alarm had already auto-snoozed once
        // (ring duration elapsed, unattended) and the user then acknowledged
        // it from the stale screen that was left behind, dismiss() stopped
        // the (already-stopped) sound and returned — the armed SLOT_SNOOZE
        // survived untouched and rang again on schedule regardless.
        alarmScheduler.cancelSnooze(a.id)
        // Arm ONE follow-up check, never a second one for the check's own
        // re-ring — see the isWakeCheckRering doc comment.
        if (a.wakeCheckMinutes > 0 && !isWakeCheckRering) {
            WakeCheckReceiver.schedule(this, a.id, a.wakeCheckMinutes)
        }
        // Close a stale ringing screen for THIS alarm, if one is still up —
        // see AlarmRingBus.
        AlarmRingBus.ringEnded(a.id)
        stopSelf()
    }

    /** See [dismiss] — same Room-recovery reasoning applies to snooze. */
    private fun snooze(alarmId: Long) {
        if (previewMode) { // a preview self-silences instead of snoozing
            stopRinging(); stopSelf(); return
        }
        val current = alarm
        if (current != null) {
            // B3: enforce the snooze limit (maxSnoozes: -1 = unlimited, 0 = none)
            if (current.maxSnoozes in 0..current.snoozeCount) {
                Log.i(TAG, "Alarm ${current.id} snooze limit reached (${current.maxSnoozes}) — ignoring")
                return // keep ringing; the user must acknowledge
            }
            performSnooze(current)
            return
        }
        if (alarmId < 0) { stopRinging(); stopSelf(); return }
        // Nothing is actually ringing in THIS instance either way (it would
        // be the `current != null` branch above if it were), so there is no
        // "keep ringing" outcome to preserve here — recover the row purely
        // for the bookkeeping and always finish afterward.
        scope.launch {
            val loaded = runCatching { alarmDao.getAlarmById(alarmId) }.getOrNull()
            if (loaded != null && loaded.maxSnoozes !in 0..loaded.snoozeCount) {
                performSnooze(loaded)
            } else {
                stopRinging(); stopSelf()
            }
        }
    }

    private fun performSnooze(a: AlarmEntity) {
        stopRinging()
        cancelStuckNotification()
        persistScope.launch { runCatching { alarmDao.setSnoozeCount(a.id, a.snoozeCount + 1) } }
        alarmScheduler.scheduleSnooze(a.id, a.snoozeMinutes)
        Log.i(TAG, "Alarm ${a.id} snoozed for ${a.snoozeMinutes} min (#${a.snoozeCount + 1})")
        stopSelf()
    }

    /**
     * Belt-and-braces: the ONLY notification manager call that can leave
     * [NotificationHelper.ALARM_NOTIFICATION_ID] stuck. stopForeground() (run
     * implicitly by stopSelf() tearing down this service) clears it for the
     * normal FGS-ringing path, but the exact-alarm-unavailable FALLBACK
     * notification (AlarmTriggerReceiver) is posted directly via
     * NotificationManager.notify — it was never tied to a foreground
     * service — so dismissing/snoozing that one via this same service left
     * an un-swipeable "שעון מעורר" sitting in the shade indefinitely.
     */
    private fun cancelStuckNotification() {
        getSystemService<android.app.NotificationManager>()
            ?.cancel(NotificationHelper.ALARM_NOTIFICATION_ID)
    }

    private fun stopRinging() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        try {
            mediaPlayer?.stop()
        } catch (_: IllegalStateException) {
        }
        // The effect is bound to the player's audio session — release it FIRST,
        // otherwise it outlives the session it is attached to.
        releaseBoost()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
        restoreAlarmStreamVolume()
    }

    /**
     * Attach the above-100% boost, if this alarm asked for one.
     *
     * The system ALARM stream is already at its maximum by the time we get
     * here, so "louder than 100%" cannot come from the stream — it has to come
     * from amplifying the signal itself. [android.media.audiofx.LoudnessEnhancer]
     * does exactly that, and it COMPRESSES anything that would exceed the
     * sample range rather than hard-clipping it, so the result stays usable
     * rather than turning into a buzz.
     *
     * Gain is in millibels (100 mB = 1 dB). 120% maps to +6 dB — a clearly
     * audible step up, not the literal 1.58 dB that "20% more amplitude" would
     * give, which nobody would notice through a pillow.
     *
     * EVERY failure path here is swallowed on purpose. This is an alarm: a
     * device without the effect, an OEM that throws from the constructor, or a
     * session id that is not ready must all end with a normal alarm ringing at
     * 100%, never with an exception escaping into the ring path. The four
     * exception types are the ones the AOSP constructor declares
     * (IllegalState / IllegalArgument / UnsupportedOperation / Runtime), and
     * runCatching covers all of them plus anything an OEM adds.
     */
    private fun attachBoost(player: android.media.MediaPlayer?) {
        releaseBoost()
        val percent = boostPercent
        if (!AlarmVolume.needsBoost(percent)) return
        val player = player ?: return
        runCatching {
            val sessionId = player.audioSessionId
            if (sessionId == 0) return@runCatching  // no session to attach to
            val gainMb = AlarmVolume.boostMillibels(percent)
            val effect = android.media.audiofx.LoudnessEnhancer(sessionId)
            // The native default target gain is 0 — without this call the
            // effect attaches and does precisely nothing.
            effect.setTargetGain(gainMb)
            val status = effect.setEnabled(true)
            if (status != android.media.audiofx.AudioEffect.SUCCESS) {
                // Attached but refused to engage (another app holds control,
                // OEM policy…). Let go rather than leave a dead effect bound
                // to the session.
                Log.w(TAG, "Loudness boost refused to enable (status=$status)")
                runCatching { effect.release() }
                loudnessEnhancer = null
                return@runCatching
            }
            loudnessEnhancer = effect
            Log.i(TAG, "Loudness boost on: $percent% (+${gainMb / 100} dB)")
        }.onFailure {
            Log.w(TAG, "Loudness boost unavailable on this device: ${it.message}")
            loudnessEnhancer = null
        }
    }

    private fun releaseBoost() {
        runCatching {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        }
        loudnessEnhancer = null
    }

    private fun acquireWakeLock() {
        // A second alarm firing while the first is still ringing calls this a
        // second time on the same Service instance. Overwriting `wakeLock`
        // without releasing the old reference first orphaned the FIRST lock —
        // nothing ever released it, and it stayed held for its full 35-minute
        // timeout, showing up as a battery-drain warning on several OEMs.
        wakeLock?.let { if (it.isHeld) it.release() }
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
            java.time.Instant.now().asZmanTime(ZoneId.systemDefault())
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
        const val EXTRA_PREVIEW_GRADUAL = "preview_gradual"
        const val EXTRA_PREVIEW_VIBRATE = "preview_vibrate"
        const val EXTRA_PREVIEW_SHABBAT = "preview_shabbat"
        const val EXTRA_PREVIEW_TITLE = "preview_title"

        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_IS_WAKE_CHECK_RERING = "is_wake_check_rering"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TIME_TEXT = "time_text"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        const val EXTRA_CHALLENGE = "challenge"
        const val EXTRA_SHABBAT = "shabbat_mode"
        const val EXTRA_SNOOZES_LEFT = "snoozes_left"

        // Ramp 0.2 → 1.0 in ~20s (ring durations are now 10s–3min, so the old
        // one-minute ramp meant short alarms never reached full loudness)
        /** Settling time before attaching the boost. */
        private const val BOOST_ATTACH_DELAY_MS = 500L

        private const val VOLUME_STEP_INTERVAL_MS = AlarmVolume.MAX_RAMP_STEP_MS

        /**
         * Caps how many times an UNATTENDED alarm may auto-snooze itself when
         * maxSnoozes is -1 ("unlimited"). That setting is meant to describe the
         * user's own נודניק-button budget, not how many times the app may
         * re-ring itself with nobody there to answer — reusing it for both
         * meant a phone left in another room rang for a minute every
         * snoozeMinutes, forever. Manual button presses are unaffected: they
         * are still genuinely unlimited (see the `current.maxSnoozes in
         * 0..current.snoozeCount` gate in snooze(), which for -1 is always
         * false).
         */
        private const val MAX_UNATTENDED_AUTO_SNOOZE_ROUNDS = 4
    }
}
