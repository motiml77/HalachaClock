package com.zmanimclock.app.scheduling

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.GregorianCalendar

/**
 * Pure occurrence math for alarms — no Android dependencies, fully unit
 * tested. The scheduler feeds it "now" and a zone and gets fire times back.
 */
object AlarmTimeCalculator {

    const val MAX_LOOKAHEAD_DAYS = 15

    /**
     * The next fire time of a FIXED alarm: the earliest date whose weekday is
     * enabled, not skipped (Shabbat/YomTov), and whose wall-clock time is
     * still in the future.
     */
    fun nextFixedOccurrence(
        alarm: AlarmEntity,
        zone: ZoneId,
        now: Instant,
    ): Instant? {
        var date = LocalDate.ofInstant(now, zone)
        repeat(MAX_LOOKAHEAD_DAYS) {
            val fire = date.atTime(alarm.hour, alarm.minute).atZone(zone).toInstant()
            if (fire.isAfter(now) && isDayAllowed(alarm, date, zone)) return fire
            date = date.plusDays(1)
        }
        return null
    }

    /** Weekday enabled AND not skipped by the Shabbat/YomTov flags. */
    fun isDayAllowed(alarm: AlarmEntity, date: LocalDate, zone: ZoneId): Boolean {
        if (!alarm.isEnabledOn(date.dayOfWeek)) return false
        if (!alarm.skipShabbat && !alarm.skipYomTov) return true
        val isShabbat = date.dayOfWeek == DayOfWeek.SATURDAY
        if (alarm.skipShabbat && isShabbat) return false
        if (alarm.skipYomTov) {
            val cal = GregorianCalendar.from(date.atStartOfDay(zone))
            val jewish = JewishCalendar(cal).apply { inIsrael = true }
            if (jewish.isYomTovAssurBemelacha) return false
        }
        return true
    }
}
