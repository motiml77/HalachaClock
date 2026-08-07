package com.zmanimclock.app.feature.calendar.model

import com.zmanimclock.app.feature.zmanim.model.FastDays
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Everything one cell of the calendar grid needs to draw itself.
 *
 * Built entirely from the Hebrew calendar — integer arithmetic, no astronomy,
 * no database, no network. That is what lets a whole month be produced in
 * microseconds and lets the grid stay identical regardless of the user's
 * location. Only the SELECTED day pays for zmanim.
 */
data class CalendarDayMeta(
    val date: LocalDate,
    val hebrewYear: Int,
    val hebrewMonth: Int,
    val hebrewDayOfMonth: Int,

    /** "כ״א" — from KosherJava, never hand-rolled gematria. */
    val hebrewDayLabel: String,
    /** "21" — kept as a separate field because it must render in its own
     *  Text composable; interpolating a Hebrew and a Latin run into one
     *  string hands the result to the bidi algorithm, which reorders them
     *  inconsistently from month to month. */
    val gregorianDayLabel: String,

    val dayOfWeek: DayOfWeek,
    val isShabbat: Boolean,
    val isRoshChodesh: Boolean,
    /** Yom Tov on which melacha is forbidden — the strongest cell marker. */
    val isYomTovAssurBemelacha: Boolean,
    val isCholHamoed: Boolean,
    val isErevYomTov: Boolean,
    /** Yom Ha'atzmaut / Yom HaZikaron / Yom HaShoah / Yom Yerushalayim.
     *  Marked separately and softly: they are not assur bemelacha. */
    val isModernHoliday: Boolean,

    val fast: FastDays.FastDay?,
    /** 1..8 during Chanukah, else null. */
    val dayOfChanukah: Int?,
    /** 1..49 during the omer, else null. */
    val omerDay: Int?,

    /** "פסח" / "יום העצמאות" — null when the day has no name. */
    val yomTovName: String?,
    /** The parsha, on Shabbat only. */
    val parshaName: String?,
    /** שקלים / זכור / פרה / החודש / הגדול / שובה / שירה. */
    val specialShabbatName: String?,

    /** false for the dimmed leading/trailing days of the adjacent months. */
    val isInDisplayedMonth: Boolean,
) {
    /** Whether this day carries any marker worth a chip in the month ribbon. */
    val isNotable: Boolean
        get() = isRoshChodesh || isYomTovAssurBemelacha || isCholHamoed ||
            isModernHoliday || fast != null || dayOfChanukah != null
}

/** One entry in the month's event ribbon. */
data class MonthEvent(
    val date: LocalDate,
    /** "ראש חודש אלול" / "תשעה באב" */
    val title: String,
    /** "א׳" — the Hebrew day, so the chip can say when without a second line. */
    val hebrewDayLabel: String,
    val kind: Kind,
) {
    enum class Kind { ROSH_CHODESH, YOM_TOV, CHOL_HAMOED, FAST, MODERN, CHANUKAH }
}
