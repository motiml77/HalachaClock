package com.zmanimclock.app.feature.womensarea.model

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.calendar.model.CalendarDayMeta
import com.zmanimclock.app.feature.calendar.model.toGregorianCalendar
import java.time.DayOfWeek
import java.time.LocalDate

/** Every Hebrew string the Women's Area shows for a day, a kind or an onah — in one place. */
object WomensAreaLabels {

    val VesetKind.hebrewName: String
        get() = when (this) {
            VesetKind.ONAH_BEINONIT -> "עונה בינונית"
            VesetKind.HAFLAGA -> "הפלגה"
            VesetKind.YOM_HACHODESH -> "יום החודש"
            VesetKind.YOM_HACHODESH_PREVIOUS -> "יום החודש מראייה קודמת"
            VesetKind.HAFLAGA_NOT_UPROOTED -> "הפלגה שלא נעקרה"
        }

    /** "ביום" / "בלילה". */
    val Onah.hebrewName: String
        get() = when (this) {
            Onah.DAY -> "ביום"
            Onah.NIGHT -> "בלילה"
        }

    private fun formatter() = HebrewDateFormatter().apply {
        isHebrewFormat = true
        isUseGershGershayim = true
    }

    private fun jewishDateOf(date: LocalDate) = JewishDate(date.toGregorianCalendar())

    /** "ט״ו ניסן תשפ״ו". */
    fun hebrewDate(date: LocalDate): String {
        val fmt = formatter()
        val jd = jewishDateOf(date)
        return "${fmt.formatHebrewNumber(jd.jewishDayOfMonth)} ${fmt.formatMonth(jd)} ${fmt.formatHebrewNumber(jd.jewishYear)}"
    }

    /** "ט״ו ניסן" — no year, for tight spots. */
    fun hebrewDayAndMonth(date: LocalDate): String {
        val fmt = formatter()
        val jd = jewishDateOf(date)
        return "${fmt.formatHebrewNumber(jd.jewishDayOfMonth)} ${fmt.formatMonth(jd)}"
    }

    /** "שלישי" — the weekday of the Hebrew day itself (its daytime). */
    fun weekdayName(date: LocalDate): String = weekdayName(date.dayOfWeek)

    private fun weekdayName(day: DayOfWeek): String = when (day) {
        DayOfWeek.SUNDAY -> "ראשון"
        DayOfWeek.MONDAY -> "שני"
        DayOfWeek.TUESDAY -> "שלישי"
        DayOfWeek.WEDNESDAY -> "רביעי"
        DayOfWeek.THURSDAY -> "חמישי"
        DayOfWeek.FRIDAY -> "שישי"
        DayOfWeek.SATURDAY -> "שבת"
    }

    /**
     * "2/11" — the civil date in a calendar cell: day on the LEFT of the
     * slash, month on the right. Drawn left-to-right (see the cells), so the
     * app's RTL layout can never swap the two. Every cell carries its own
     * month, so a Hebrew month that starts in October and ends in November
     * reads 30/10, 31/10, 1/11 … with no separate header to keep in step.
     */
    fun gregorianDayMonth(date: LocalDate): String = "${date.dayOfMonth}/${date.monthValue}"

    /** "2.4" — the civil date, secondary everywhere. */
    fun gregorianShort(date: LocalDate): String = "${date.dayOfMonth}.${date.monthValue}"

    /**
     * When an onah of the Hebrew day [date] actually falls, in civil terms.
     *
     * DAY: "יום חמישי ט״ו ניסן (2.4)".
     * NIGHT: "ליל חמישי ט״ו ניסן — הערב של יום רביעי 1.4, אחרי השקיעה" — the
     * night belongs to the Hebrew day that FOLLOWS it, so its civil evening is
     * the day before [date].
     */
    fun onahTiming(date: LocalDate, onah: Onah): String = when (onah) {
        Onah.DAY -> "יום ${weekdayName(date)} ${hebrewDayAndMonth(date)} (${gregorianShort(date)})"
        Onah.NIGHT -> {
            val evening = date.minusDays(1)
            "ליל ${weekdayName(date)} ${hebrewDayAndMonth(date)} — " +
                "הערב של ${eveningDay(evening)} ${gregorianShort(evening)}, אחרי השקיעה"
        }
    }

    /** "יום שלישי ט״ו ניסן (2.4), לפני השקיעה" — when the הפסק טהרה was made. */
    fun hefsekTiming(date: LocalDate): String =
        "יום ${weekdayName(date)} ${hebrewDayAndMonth(date)} (${gregorianShort(date)}), לפני השקיעה"

    /**
     * When the tevila is, from the 7th clean day [seventhDay]:
     * "ביום ראשון כ״ז ניסן (14.4), לאחר צאת הכוכבים בלבד — ליל שני כ״ח ניסן".
     * On Shabbat: "במוצאי שבת …".
     */
    fun tevilaTiming(seventhDay: LocalDate): String {
        val night = seventhDay.plusDays(1)
        val day = if (seventhDay.dayOfWeek == DayOfWeek.SATURDAY) "במוצאי שבת" else "ביום ${weekdayName(seventhDay)}"
        return "$day ${hebrewDayAndMonth(seventhDay)} (${gregorianShort(seventhDay)}), לאחר צאת הכוכבים בלבד — " +
            "ליל ${weekdayName(night)} ${hebrewDayAndMonth(night)}"
    }

    /**
     * The heading of one separation day, each by what actually defines it:
     *   עונה בינונית · יום 30       — a count, so the day's number
     *   הפלגה (29 יום)              — the gap between the two vesets
     *   יום החודש · ט״ו אייר        — a DATE, not a count: no day number,
     *                                 which would read as "31 days" whenever
     *                                 the month in between has 30.
     */
    fun prishaTitle(day: PrishaDay): String = when (day.kind) {
        VesetKind.ONAH_BEINONIT -> "${day.kind.hebrewName} · יום ${day.dayNumber}"
        VesetKind.HAFLAGA -> "${day.kind.hebrewName} (${day.dayNumber} יום)"
        VesetKind.YOM_HACHODESH -> "${day.kind.hebrewName} · ${hebrewDayAndMonth(day.date)}"
        VesetKind.YOM_HACHODESH_PREVIOUS ->
            "יום החודש מהראייה של ${day.fromVeset?.let(::hebrewDayAndMonth) ?: "ראייה קודמת"} · ${hebrewDayAndMonth(day.date)}"
        VesetKind.HAFLAGA_NOT_UPROOTED -> "הפלגה שלא נעקרה (${day.interval} יום) · יום ${day.dayNumber}"
    }

    /**
     * The festival / fast / Rosh Chodesh of a day, short enough for the top of
     * a calendar cell — from the same CalendarDayMeta the Calendar tab uses,
     * so both tabs name every day identically. Null on an ordinary day.
     * Rosh Chodesh that is also a Chanukah day carries both.
     */
    fun moedLabel(meta: CalendarDayMeta): String? {
        val name = meta.yomTovName?.let(::shorten)
        return when {
            meta.isRoshChodesh && name != null -> "ר״ח · $name"
            meta.isRoshChodesh -> "ר״ח"
            else -> name
        }
    }

    private val SHORT_FORMS = listOf(
        "חול המועד" to "חוה״מ",
        "ערב ראש השנה" to "ערב ר״ה",
        "ערב יום כיפור" to "ערב יוה״כ",
        "שבעה עשר בתמוז" to "י״ז בתמוז",
    )

    private fun shorten(name: String): String =
        SHORT_FORMS.fold(name) { acc, (long, short) -> acc.replace(long, short) }

    /**
     * One pattern the history found, in plain words — what repeated, nothing
     * more. It never names a status for it; conclusions are hers, not the app's.
     */
    fun patternText(pattern: HistoryPattern): String = when (pattern) {
        is HistoryPattern.SameHaflaga ->
            "הפלגה של ${pattern.days} יום חזרה ${pattern.count} פעמים ברצף"
        is HistoryPattern.SameDayOfMonth ->
            "הראייה הופיעה ${pattern.count} חודשים ברצף ב${hebrewNumber(pattern.dayOfMonth)} בחודש"
        is HistoryPattern.SteadyHaflagaStep -> {
            val by = kotlin.math.abs(pattern.step)
            val days = if (by == 1) "ביום אחד" else "ב־$by ימים"
            "ההפלגות ${if (pattern.step > 0) "גדלות" else "קטנות"} $days בכל פעם (${pattern.count} הפלגות ברצף)"
        }
    } + if (pattern.onah == Onah.DAY) " — כולן ביום" else " — כולן בלילה"

    /** The warning when ליל הטבילה is a night with no tevila. */
    fun tevilaBlockText(block: TevilaBlock): String =
        "ליל הטבילה חל ב" + (if (block == TevilaBlock.YOM_KIPPUR) "ליל יום הכיפורים" else "ליל תשעה באב") +
            " — אין טובלים בלילה זה. יש לשאול רב."

    /** "ט״ו" — KosherJava's gematria, as everywhere else. */
    fun hebrewNumber(n: Int): String = formatter().formatHebrewNumber(n)

    /** "יום רביעי" / "שבת" — "יום שבת" reads wrongly. */
    private fun eveningDay(date: LocalDate): String =
        if (date.dayOfWeek == DayOfWeek.SATURDAY) "שבת" else "יום ${weekdayName(date)}"
}
