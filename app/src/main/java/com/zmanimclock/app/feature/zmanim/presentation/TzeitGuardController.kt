package com.zmanimclock.app.feature.zmanim.presentation

import android.content.Context
import com.zmanimclock.app.feature.alarms.data.AlarmDao
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.alarms.data.AlarmType
import com.zmanimclock.app.scheduling.RescheduleWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The alarm a שומר לערבית is: tonight, once, ten seconds, then gone.
 *
 * Built HERE and nowhere else, so the zmanim screen's badge and the home-screen
 * widget's button arm exactly the same thing — TzeitGuardTest pins this
 * function rather than a copy of it.
 *
 * A FIXED alarm, deliberately, even when the chosen moment IS the zman. A
 * ZMAN-anchored alarm re-derives its time every day and would roll to
 * tomorrow's tzeit the instant tonight's passed; this alert exists only for
 * tonight, so it is pinned to tonight's wall clock and nothing about it moves
 * afterwards.
 */
internal fun buildGuardAlarm(hour: Int, minute: Int) = AlarmEntity(
    type = AlarmType.FIXED,
    hour = hour,
    minute = minute,
    // 0 = one-time. Bit-for-bit the same "fires once" the alarms screen uses,
    // so the scheduler needs no new case.
    daysOfWeek = 0,
    soundEnabled = true,
    vibrate = true,
    ringDurationSeconds = ZmanimViewModel.TZEIT_GUARD_RING_SECONDS,
    // No snooze: this is a nudge for one moment, and a snooze would also keep
    // the row alive past the ring it is supposed to disappear with.
    maxSnoozes = 0,
    label = ZmanimViewModel.TZEIT_GUARD_LABEL,
    deleteAfterFiring = true,
)

/** "HH:mm" of an armed guard, or null when there is none. */
internal fun guardArmedAt(guard: AlarmEntity?): String? =
    guard?.let { "%02d:%02d".format(it.hour, it.minute) }

/**
 * Arms, replaces and cancels tonight's שומר לערבית.
 *
 * Shared by the zmanim screen ([ZmanimViewModel]) and the widget's button, and
 * suspending on purpose: the widget's dialog closes the moment it is answered,
 * so the write must finish BEFORE the caller finishes — a fire-and-forget
 * launch in a scope that dies with that screen could be cancelled half-way.
 */
@Singleton
class TzeitGuardController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmDao: AlarmDao,
) {

    /** The armed guard, if any — the one active alarm that erases itself after firing. */
    suspend fun current(): AlarmEntity? =
        alarmDao.getActiveAlarmsList().firstOrNull { it.deleteAfterFiring && it.isActive }

    /**
     * Arms tonight's guard at [hour]:[minute].
     *
     * Replaces rather than stacks: arming it twice is the user changing their
     * mind, not asking for two alarms.
     */
    suspend fun arm(hour: Int, minute: Int) {
        current()?.let { runCatching { alarmDao.deleteById(it.id) } }
        alarmDao.insertAlarm(buildGuardAlarm(hour, minute))
        RescheduleWorker.enqueueUnique(context)
    }

    /** Disarms it and removes every trace, the same as firing would. */
    suspend fun cancel() {
        current()?.let {
            runCatching { alarmDao.deleteById(it.id) }
            RescheduleWorker.enqueueUnique(context)
        }
    }
}
