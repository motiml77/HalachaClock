package com.zmanimclock.app.feature.zmanim.model

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.zmanimclock.app.feature.zmanim.engine.DayZmanim
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.GregorianCalendar

/**
 * Stable identifiers for alert-able zmanim. Alert rows store [name], so these
 * names must never be renamed once released.
 *
 * [hebrewName] is used in notifications; screens localize via resources.
 */
/**
 * [hebrewName] is the full list label (with the shita qualifier);
 * [shortName] is the compact display used in notifications — no degree or
 * minute suffixes, per the user's preference.
 */
enum class ZmanKind(val hebrewName: String, val shortName: String = hebrewName) {
    CHATZOT_LAYLA("חצות לילה"),
    ALOT_HASHACHAR("עלות השחר"),
    MISHEYAKIR("משיכיר"),
    HANETZ("הנץ החמה"),
    SOF_ZMAN_SHMA_MGA("ק\"ש מג\"א 16.1°", "ק\"ש מג\"א"),
    SOF_ZMAN_SHMA_MGA_72("ק\"ש מג\"א 72'", "ק\"ש מג\"א"),
    SOF_ZMAN_SHMA_GRA("סוף זמן ק\"ש גר\"א", "ק\"ש גר\"א"),
    SOF_ZMAN_TFILA_MGA("תפילה מג\"א 16.1°", "תפילה מג\"א"),
    SOF_ZMAN_TFILA_MGA_72("תפילה מג\"א 72'", "תפילה מג\"א"),
    SOF_ZMAN_TFILA_GRA("סוף זמן תפילה גר\"א", "תפילה גר\"א"),
    CHATZOT("חצות היום"),
    MINCHA_GEDOLA("מנחה גדולה"),
    MINCHA_KETANA("מנחה קטנה"),
    PLAG_HAMINCHA("פלג המנחה"),
    SHKIA("שקיעה"),
    // Naming per the user's ruling: the DEFAULT tzeit shown is the 6.2° one
    // (three medium stars); the 13.5-zmaniyot Geonim time is the kulah.
    // Enum names are persisted in alarms — only the labels change.
    TZEIT_HAKOCHAVIM("צאת הכוכבים לקולא"),
    TZEIT_LECHUMRA("צאת הכוכבים"),
    TZEIT_SHABBAT("צאת שבת"),
    TZEIT_RABBEINU_TAM("רבנו תם"),
    CANDLE_LIGHTING("הדלקת נרות");

    companion object {
        fun fromNameOrNull(name: String): ZmanKind? = entries.firstOrNull { it.name == name }
    }
}

/**
 * The zmanim to actually SHOW/rank for [date] at this location — sorted by
 * time. Day-specific zmanim are hidden when they are meaningless:
 *  - הדלקת נרות only on erev Shabbat (Friday) / erev Yom Tov.
 *  - צאת שבת only on Shabbat itself / a Yom Tov that is assur bemelacha.
 * Everything else shows every day. This is the single source of truth used
 * by the home screen, the status notification and the widget so "הזמן הבא"
 * never points at candle-lighting in the middle of the week.
 */
fun DayZmanim.relevantTimedZmanim(date: LocalDate): List<Pair<ZmanKind, Instant>> {
    val zone = ZoneId.of(location.timeZoneId)
    val jc = JewishCalendar(GregorianCalendar.from(date.atStartOfDay(zone))).apply { inIsrael = true }
    val isErevShabbatOrChag =
        date.dayOfWeek == DayOfWeek.FRIDAY || jc.isErevYomTov || jc.isErevYomTovSheni
    val isShabbatOrChag =
        date.dayOfWeek == DayOfWeek.SATURDAY || jc.isYomTovAssurBemelacha

    return ZmanKind.entries
        .filter { kind ->
            when (kind) {
                ZmanKind.CANDLE_LIGHTING -> isErevShabbatOrChag
                ZmanKind.TZEIT_SHABBAT -> isShabbatOrChag
                else -> true
            }
        }
        .mapNotNull { kind -> instantOf(kind)?.let { kind to it } }
        .sortedBy { (_, instant) -> instant }
}

/** The concrete time of [kind] on this day (visible-netz-based when available). */
fun DayZmanim.instantOf(kind: ZmanKind): Instant? = when (kind) {
    ZmanKind.CHATZOT_LAYLA -> chatzotLayla
    ZmanKind.ALOT_HASHACHAR -> alotHashachar
    ZmanKind.MISHEYAKIR -> misheyakir60 // luach standard: one shaah zmanit before the netz
    ZmanKind.HANETZ -> hanetzVisible ?: hanetzMishor
    ZmanKind.SOF_ZMAN_SHMA_MGA -> sofZmanShmaMga
    ZmanKind.SOF_ZMAN_SHMA_MGA_72 -> sofZmanShmaMga72
    ZmanKind.SOF_ZMAN_SHMA_GRA -> sofZmanShmaGra
    ZmanKind.SOF_ZMAN_TFILA_MGA -> sofZmanTfilaMga
    ZmanKind.SOF_ZMAN_TFILA_MGA_72 -> sofZmanTfilaMga72
    ZmanKind.SOF_ZMAN_TFILA_GRA -> sofZmanTfilaGra
    ZmanKind.CHATZOT -> chatzot
    ZmanKind.MINCHA_GEDOLA -> minchaGedola
    ZmanKind.MINCHA_KETANA -> minchaKetana
    ZmanKind.PLAG_HAMINCHA -> plagHaminchaYalkutYosef
    ZmanKind.SHKIA -> shkia
    ZmanKind.TZEIT_HAKOCHAVIM -> tzeitHakochavim
    ZmanKind.TZEIT_LECHUMRA -> tzeitLechumra
    ZmanKind.TZEIT_SHABBAT -> tzeitShabbat
    ZmanKind.TZEIT_RABBEINU_TAM -> tzeitRabbeinuTam
    ZmanKind.CANDLE_LIGHTING -> candleLighting
}
