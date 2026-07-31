package com.zmanimclock.app.feature.alarms.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** What anchors the alarm's fire time. */
enum class AlarmType {
    /** A regular wake-up alarm at a fixed wall-clock time (e.g. 06:30). */
    FIXED,

    /** Anchored to a halachic zman (e.g. 30 minutes before hanetz) — the
     *  fire time moves every day with the zmanim of the user's location. */
    ZMAN,
}

/** Optional dismiss gate — the alarm stops only after solving a problem. */
enum class DismissChallenge {
    NONE,
    MATH_EASY,   // 7 + 5
    MATH_MEDIUM, // 23 + 48
    MATH_HARD,   // 17 × 6
}

/**
 * One alarm clock — either a regular fixed-time alarm or a zman-anchored one.
 * Everything else (repeat days, sound, volume, ring duration, challenge,
 * snooze, vibration) is shared between the two types.
 *
 * [daysOfWeek] is a bitmask: bit 0 = Sunday … bit 6 = Saturday.
 * 0 means one-time — the alarm deactivates itself after ringing.
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val type: AlarmType = AlarmType.FIXED,

    // FIXED anchor
    val hour: Int = 6,
    val minute: Int = 0,

    // ZMAN anchor
    val zmanId: String = "",
    val offsetMinutes: Int = 0,
    val offsetBefore: Boolean = true,

    // Repeat
    val daysOfWeek: Int = ALL_DAYS,
    val skipShabbat: Boolean = false,
    val skipYomTov: Boolean = false,

    // Ring
    val soundEnabled: Boolean = true,  // false = vibrate-only alert
    val soundUri: String? = null,      // null = system default alarm sound
    // 10..120. 100 = the device maximum; above that the AlarmSoundService
    // amplifies the signal itself with a LoudnessEnhancer. The default
    // stays at plain 100 — boosting is opt-in per alarm.
    val volumePercent: Int = 100,
    @Deprecated("Superseded by ringDurationSeconds") val ringDurationMinutes: Int = 1,
    /** Auto-silence (self-snooze) after this many seconds. 10..180 (max 3 min). */
    val ringDurationSeconds: Int = 60,
    val vibrate: Boolean = true,

    /**
     * Shabbat-entry mode: the special Friday alert (default: 4 minutes
     * before the location's shkia) — candles screen, its own sound.
     */
    val shabbatMode: Boolean = false,

    // Dismissal
    val dismissChallenge: DismissChallenge = DismissChallenge.NONE,
    val snoozeMinutes: Int = 5,

    /** Anti-snooze: max snoozes allowed. -1 = unlimited, 0 = no snooze (default). */
    val maxSnoozes: Int = 0,
    /** Snoozes used for the current firing; reset on dismiss/reschedule. */
    val snoozeCount: Int = 0,

    /** Wake-up check: re-ring after this many minutes unless confirmed. 0 = off. */
    val wakeCheckMinutes: Int = 0,

    /**
     * Skip-next: the alarm's next occurrence is suppressed up to this epoch-ms.
     * Occurrences at or before it are skipped; 0 = not skipping.
     */
    val skipUntilEpochMs: Long = 0,

    val label: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val ALL_DAYS = 0b1111111

        /** ימי חול בישראל: ראשון-שישי (בלי שבת). */
        const val SUNDAY_TO_FRIDAY = 0b0111111

        /** א'-ה'. */
        const val SUNDAY_TO_THURSDAY = 0b0011111

        /** יום שישי בלבד — התראת כניסת שבת. */
        const val FRIDAY_ONLY = 0b0100000

        /** Sunday-first bit for a java.time.DayOfWeek (SUNDAY=bit0 … SATURDAY=bit6). */
        fun bitFor(dayOfWeek: java.time.DayOfWeek): Int =
            1 shl (dayOfWeek.value % 7) // MONDAY(1)->bit1 … SATURDAY(6)->bit6, SUNDAY(7)->bit0
    }

    fun isEnabledOn(dayOfWeek: java.time.DayOfWeek): Boolean =
        daysOfWeek == 0 || (daysOfWeek and bitFor(dayOfWeek)) != 0

    val isOneTime: Boolean get() = daysOfWeek == 0
}
