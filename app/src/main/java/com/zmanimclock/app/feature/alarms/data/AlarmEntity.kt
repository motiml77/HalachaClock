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
    val soundUri: String? = null,      // null = system default alarm sound
    val volumePercent: Int = 100,      // 10..100, ramp climbs to this target
    val ringDurationMinutes: Int = 5,  // auto-silence (self-snooze) after this
    val vibrate: Boolean = true,

    // Dismissal
    val dismissChallenge: DismissChallenge = DismissChallenge.NONE,
    val snoozeMinutes: Int = 5,

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

        /** Sunday-first bit for a java.time.DayOfWeek (SUNDAY=bit0 … SATURDAY=bit6). */
        fun bitFor(dayOfWeek: java.time.DayOfWeek): Int =
            1 shl (dayOfWeek.value % 7) // MONDAY(1)->bit1 … SATURDAY(6)->bit6, SUNDAY(7)->bit0
    }

    fun isEnabledOn(dayOfWeek: java.time.DayOfWeek): Boolean =
        daysOfWeek == 0 || (daysOfWeek and bitFor(dayOfWeek)) != 0

    val isOneTime: Boolean get() = daysOfWeek == 0
}
