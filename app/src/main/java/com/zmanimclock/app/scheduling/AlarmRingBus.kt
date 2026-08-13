package com.zmanimclock.app.scheduling

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-process signal from [AlarmSoundService] to [AlarmActivity]: "the ring
 * for this alarm id just ended — if you are still showing it, close".
 *
 * Needed because nothing used to close the ringing screen except the user's
 * own tap on it. When the service silenced itself unattended (ring duration
 * elapsed, no snoozes left) or a dismiss/snooze command was recovered from
 * Room by a freshly-restarted service instance, the full-screen UI was left
 * sitting over the lock screen indefinitely — silent, stale, and (because
 * AlarmActivity is launchMode="singleInstance" with no onNewIntent override)
 * reused as-is for the NEXT alarm that fires, showing the wrong name, time
 * and dismiss challenge.
 *
 * A plain in-process bus rather than a system broadcast: the service and the
 * activity always run in the same process (no android:process split in the
 * manifest), so this is simpler and faster than going through the OS.
 */
object AlarmRingBus {
    private val _closed = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    private val _silenced = MutableSharedFlow<Long>(extraBufferCapacity = 8)

    /** Alarm ids whose ring just ended. A screen still showing that id should finish(). */
    val closed = _closed.asSharedFlow()

    /**
     * Alarm ids whose SOUND AND VIBRATION stopped, while the occurrence is
     * still awaiting the user's acknowledgement.
     *
     * Deliberately separate from [closed]. "The noise is over" and "the screen
     * may go away" used to be the same event, which meant a ring that timed
     * out unattended also erased the only evidence it had ever happened: the
     * user came back to a phone that had rung, stopped, and closed itself, with
     * nothing on screen to say so. Now the duration the user configured governs
     * only the noise, and the screen stays until they press אישור.
     */
    val silenced = _silenced.asSharedFlow()

    fun ringEnded(alarmId: Long) {
        _closed.tryEmit(alarmId)
    }

    fun ringSilenced(alarmId: Long) {
        _silenced.tryEmit(alarmId)
    }
}
