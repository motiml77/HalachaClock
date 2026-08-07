package com.zmanimclock.app.feature.calendar.model

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.zmanim.model.FastDays
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Turns a [HebrewMonthRef] into a drawable [MonthGrid]. Pure: no I/O, no
 * dispatcher, no Android.
 *
 * TWO SETTINGS THAT ARE DELIBERATE, NOT DEFAULTS
 *
 * `inIsrael = true` matches what FastDays and ZmanKind already hardcode. It is
 * a known limitation for the 16 foreign cities in the city list, but the grid
 * MUST agree with the zmanim list — a calendar that marks one day as Yom Tov
 * while the zmanim screen disagrees is worse than a consistent limitation.
 *
 * `isUseModernHolidays = true` is on at the user's instruction, so Yom
 * Ha'atzmaut, Yom HaZikaron, Yom HaShoah and Yom Yerushalayim appear. They are
 * flagged separately via [CalendarDayMeta.isModernHoliday] so the UI can mark
 * them softly — they are not assur bemelacha and must not look like Yom Tov.
 */
object MonthGridBuilder {

    private const val WEEK = 7
    private const val ROWS = 6

    /** Hebrew names of the Gregorian months, for the secondary header line. */
    private val GREGORIAN_MONTHS = arrayOf(
        "ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני",
        "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר",
    )

    private val MODERN_HOLIDAYS = setOf(
        JewishCalendar.YOM_HASHOAH,
        JewishCalendar.YOM_HAZIKARON,
        JewishCalendar.YOM_HAATZMAUT,
        JewishCalendar.YOM_YERUSHALAYIM,
    )

    private fun formatter() = HebrewDateFormatter().apply {
        isHebrewFormat = true
        isUseGershGershayim = true
    }

    private fun calendarFor(year: Int, month: Int, day: Int) =
        JewishCalendar(year, month, day).apply {
            inIsrael = true
            isUseModernHolidays = true
        }

    fun build(ref: HebrewMonthRef): MonthGrid {
        val fmt = formatter()
        val daysInMonth = ref.daysInMonth
        // KosherJava reports 1 = Sunday … 7 = Shabbat.
        val firstColumnOffset = JewishDate(ref.year, ref.month, 1).dayOfWeek - 1

        val firstCellDate = ref.firstDay.minusDays(firstColumnOffset.toLong())
        val cells = (0 until ROWS * WEEK).map { i ->
            metaFor(firstCellDate.plusDays(i.toLong()), ref, fmt)
        }

        return MonthGrid(
            ref = ref,
            hebrewMonthLabel = fmt.formatMonth(JewishDate(ref.year, ref.month, 1)),
            hebrewYearLabel = fmt.formatHebrewNumber(ref.year),
            gregorianSpanLabel = gregorianSpan(ref),
            firstColumnOffset = firstColumnOffset,
            daysInMonth = daysInMonth,
            weeks = cells.chunked(WEEK),
            events = cells.filter { it.isInDisplayedMonth }.mapNotNull { eventFor(it, fmt) },
        )
    }

    /** The metadata for a single Gregorian day. */
    fun metaFor(
        date: LocalDate,
        displayedMonth: HebrewMonthRef? = null,
        formatter: HebrewDateFormatter = formatter(),
    ): CalendarDayMeta {
        val jd = JewishDate(date.toGregorianCalendar())
        val jc = calendarFor(jd.jewishYear, jd.jewishMonth, jd.jewishDayOfMonth)
        val idx = jc.yomTovIndex
        val omer = jc.dayOfOmer
        val chanukah = jc.dayOfChanukah

        return CalendarDayMeta(
            date = date,
            hebrewYear = jd.jewishYear,
            hebrewMonth = jd.jewishMonth,
            hebrewDayOfMonth = jd.jewishDayOfMonth,
            hebrewDayLabel = formatter.formatHebrewNumber(jd.jewishDayOfMonth),
            gregorianDayLabel = date.dayOfMonth.toString(),
            dayOfWeek = date.dayOfWeek,
            isShabbat = date.dayOfWeek == DayOfWeek.SATURDAY,
            isRoshChodesh = jc.isRoshChodesh,
            isYomTovAssurBemelacha = jc.isYomTovAssurBemelacha,
            isCholHamoed = jc.isCholHamoed,
            isErevYomTov = jc.isErevYomTov,
            isModernHoliday = idx in MODERN_HOLIDAYS,
            fast = FastDays.fastOn(jc),
            dayOfChanukah = chanukah.takeIf { it >= 1 },
            omerDay = omer.takeIf { it >= 1 },
            yomTovName = formatter.formatYomTov(jc).ifBlank { null },
            // formatParsha reads the calendar's OWN parsha, which is empty on
            // any day that is not Shabbat — so this is Shabbat-only for free.
            parshaName = formatter.formatParsha(jc).ifBlank { null },
            specialShabbatName = formatter.formatSpecialParsha(jc).ifBlank { null },
            isInDisplayedMonth = displayedMonth == null ||
                (jd.jewishYear == displayedMonth.year && jd.jewishMonth == displayedMonth.month),
        )
    }

    /** "יולי–אוגוסט 2026" / "דצמבר 2026 – ינואר 2027" / "אוגוסט 2026". */
    private fun gregorianSpan(ref: HebrewMonthRef): String {
        val from = ref.firstDay
        val to = ref.lastDay
        val fromName = GREGORIAN_MONTHS[from.monthValue - 1]
        val toName = GREGORIAN_MONTHS[to.monthValue - 1]
        return when {
            from.year != to.year -> "$fromName ${from.year} – $toName ${to.year}"
            from.monthValue != to.monthValue -> "$fromName–$toName ${to.year}"
            else -> "$fromName ${to.year}"
        }
    }

    /**
     * The ribbon chip for a notable day, if it has one.
     *
     * Chanukah contributes only its first day: eight consecutive chips would
     * push everything else off the ribbon, which is the same reason the omer
     * gets no cell marker at all.
     */
    private fun eventFor(meta: CalendarDayMeta, fmt: HebrewDateFormatter): MonthEvent? {
        fun event(title: String, kind: MonthEvent.Kind) =
            MonthEvent(meta.date, title, meta.hebrewDayLabel, kind)

        return when {
            meta.fast != null -> event(meta.fast.name, MonthEvent.Kind.FAST)
            meta.isYomTovAssurBemelacha && meta.yomTovName != null ->
                event(meta.yomTovName, MonthEvent.Kind.YOM_TOV)
            meta.isModernHoliday && meta.yomTovName != null ->
                event(meta.yomTovName, MonthEvent.Kind.MODERN)
            meta.dayOfChanukah == 1 -> event("חנוכה", MonthEvent.Kind.CHANUKAH)
            meta.isCholHamoed && meta.yomTovName != null ->
                event(meta.yomTovName, MonthEvent.Kind.CHOL_HAMOED)
            meta.isRoshChodesh -> event(roshChodeshTitle(meta, fmt), MonthEvent.Kind.ROSH_CHODESH)
            else -> null
        }
    }

    /**
     * "ראש חודש אלול".
     *
     * On day 30 of a 30-day month the Rosh Chodesh belongs to the month that
     * is about to begin, not the one ending — so the name is read off the
     * FOLLOWING day rather than from this one.
     */
    private fun roshChodeshTitle(meta: CalendarDayMeta, fmt: HebrewDateFormatter): String {
        val nameSource =
            if (meta.hebrewDayOfMonth == 1) meta.date else meta.date.plusDays(1)
        val jd = JewishDate(nameSource.toGregorianCalendar())
        return "ראש חודש ${fmt.formatMonth(jd)}"
    }
}
