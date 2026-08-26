package com.zmanimclock.desktop.data

import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import java.time.DayOfWeek

/**
 * One alert: a named moment, defined RELATIVE to a halachic zman, on chosen
 * days of the week.
 *
 * This is the desktop's answer to the phone's alarm clock, and the difference
 * is the whole design. A phone alarm is a wall-clock time. An alert here has
 * no wall-clock time of its own at all — it is "twenty minutes before שקיעה",
 * which is a different instant every single day, in every city, and follows
 * the user when they change either. There is deliberately no way to enter a
 * time directly: a zmanim app that let you type 19:11 would be an alarm clock
 * that happens to sit next to some zmanim.
 *
 * SILENT, ALWAYS. No sound, no vibration, no snooze — see ReminderPopup for
 * the banner it raises and ReminderScheduler for why none of that exists.
 *
 * [offsetMinutes] is signed: negative is BEFORE the zman, positive AFTER,
 * zero exactly on it. One signed number rather than a direction enum plus a
 * magnitude, because every arithmetic use wants the sign anyway and two fields
 * can disagree with each other.
 */
data class ZmanAlert(
    /** Stable across edits and renames; the fired-log keys off it. */
    val id: String,
    /** The user's own words. Shown on the banner — this IS the message. */
    val name: String,
    val kind: ZmanKind,
    val offsetMinutes: Int = 0,
    /**
     * Which weekdays this fires on, as a bitmask: bit 0 = Sunday … bit 6 =
     * Saturday. Same convention as the Android build's alarms, so the two
     * never have to be reasoned about differently.
     *
     * ZERO IS NEVER "EVERY DAY". The Android app shipped that bug once — an
     * empty mask read as unset and rang every day — so here an empty mask
     * means exactly what it says, the alert never fires, and the UI refuses
     * to clear the last day rather than silently reinterpreting it.
     */
    val days: Int = EVERY_DAY,
    val enabled: Boolean = true,
) {

    fun firesOn(day: DayOfWeek): Boolean = days and bitFor(day) != 0

    val isEveryDay: Boolean get() = days and EVERY_DAY == EVERY_DAY

    /** "20 דקות לפני שקיעה" / "בדיוק בשקיעה" — one phrasing, used everywhere. */
    val description: String
        get() = when {
            offsetMinutes < 0 -> "${-offsetMinutes} דקות לפני ${kind.shortName}"
            offsetMinutes > 0 -> "$offsetMinutes דקות אחרי ${kind.shortName}"
            else -> "בדיוק ב${kind.shortName}"
        }

    /** "כל יום" / "א׳, ג׳, ה׳" — null when it is every day and not worth saying. */
    val daysDescription: String?
        get() = when {
            isEveryDay -> null
            days == 0 -> "לא נבחרו ימים"
            else -> DayOfWeek.entries
                .sortedBy { bitIndex(it) }
                .filter { firesOn(it) }
                .joinToString("، ") { HEBREW_INITIALS[bitIndex(it)] }
        }

    /**
     * Serialised as `enabled|offset|KIND|days|name`, with the NAME LAST and
     * the split limited — so a name containing the separator, or anything else
     * a person might type, cannot corrupt the record. (The enclosing
     * Properties file escapes newlines itself, which is the only character
     * this scheme could not otherwise survive.)
     */
    fun encode(): String = "$enabled|$offsetMinutes|${kind.name}|$days|$name"

    companion object {
        const val MAX_OFFSET_MINUTES = 240

        /** All seven bits: 0b1111111. */
        const val EVERY_DAY = 0x7F

        private val HEBREW_INITIALS = listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳")

        /** Sunday = 0 … Saturday = 6. java.time counts Monday = 1 … Sunday = 7. */
        fun bitIndex(day: DayOfWeek): Int = day.value % 7

        fun bitFor(day: DayOfWeek): Int = 1 shl bitIndex(day)

        fun decode(id: String, raw: String): ZmanAlert? {
            val parts = raw.split('|', limit = 5)
            if (parts.size < 4) return null
            // Through fromNameOrNull, so a ZmanKind renamed in a later release
            // resolves via its legacy name instead of silently dropping the
            // user's alert. Same rule the prefs' zman sets already follow.
            val kind = ZmanKind.fromNameOrNull(parts[2]) ?: return null

            // Four fields is the pre-weekday format, where every alert fired
            // daily. Read it rather than discard it.
            val days = if (parts.size == 5) parts[3].toIntOrNull() ?: EVERY_DAY else EVERY_DAY
            val name = if (parts.size == 5) parts[4] else parts[3]

            return ZmanAlert(
                id = id,
                name = name,
                kind = kind,
                offsetMinutes = parts[1].toIntOrNull()
                    ?.coerceIn(-MAX_OFFSET_MINUTES, MAX_OFFSET_MINUTES) ?: 0,
                days = days and EVERY_DAY,
                enabled = parts[0].toBooleanStrictOrNull() ?: true,
            )
        }

        /**
         * Not a UUID: the id ends up as a Properties KEY and in the fired-log,
         * both of which are files a person may open. A short monotonic string
         * keeps those readable, and uniqueness only has to hold within one
         * user's own list.
         */
        fun newId(existing: Collection<ZmanAlert>): String {
            val used = existing.mapNotNull { it.id.removePrefix("a").toIntOrNull() }.toSet()
            return "a" + generateSequence(1) { it + 1 }.first { it !in used }
        }
    }
}
