package com.zmanimclock.app.scheduling

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.zmanim.model.OmerCount
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

    /**
     * Weekday enabled, not skipped by the Shabbat/YomTov flags, and — for an
     * omer-count alarm — [date] is actually one of the 49 nights AND not a
     * night the count would ring on Shabbat or Yom Tov. omerMode alarms keep
     * daysOfWeek at its ALL_DAYS default (they are meant to fire every night),
     * so this is the gate that turns "every night" into "every night of the
     * omer": every OTHER night, [date] is simply not allowed, same as a
     * day-of-week the user never enabled.
     *
     * The Shabbat/Yom-Tov carve-out is omer-specific and deliberately NOT the
     * generic [AlarmEntity.skipShabbat] / [AlarmEntity.skipYomTov] rules: an
     * omer alert fires at TZEIT, i.e. as the NEXT Hebrew day comes in, so the
     * day whose kedusha matters is the one being ENTERED — [date] + 1, not
     * [date]. If that night is Shabbat or Yom Tov the phone would go off on a
     * day using it is forbidden, and the owner's ruling is: no omer alert
     * then. Two counts fall out as a result — the eve of Pesach VII, and the
     * final count on the eve of Shavuot (both nights on which the count is
     * still said, just without this alert). Saturday's fire, by contrast, is
     * at motzaei-Shabbat tzeit (Shabbat already out) and rings normally.
     */
    fun isDayAllowed(alarm: AlarmEntity, date: LocalDate, zone: ZoneId): Boolean {
        if (!alarm.isEnabledOn(date.dayOfWeek)) return false
        if (alarm.omerMode) {
            if (OmerCount.dayOfOmerAtTzeit(date, zone) == null) return false
            val enteredNight = JewishCalendar(GregorianCalendar.from(date.plusDays(1).atStartOfDay(zone)))
                .apply { inIsrael = true }
            if (enteredNight.isAssurBemelacha) return false
        }
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
