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

    /** Alarm ids whose ring just ended. A screen still showing that id should finish(). */
    val closed = _closed.asSharedFlow()

    fun ringEnded(alarmId: Long) {
        _closed.tryEmit(alarmId)
    }
}
