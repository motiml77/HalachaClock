package com.zmanimclock.app.feature.zmanim.model

import com.zmanimclock.app.feature.zmanim.engine.DayZmanim
import java.time.Instant

/**
 * Stable identifiers for alert-able zmanim. Alert rows store [name], so these
 * names must never be renamed once released.
 *
 * [hebrewName] is used in notifications; screens localize via resources.
 */
enum class ZmanKind(val hebrewName: String) {
    CHATZOT_LAYLA("חצות לילה"),
    ALOT_HASHACHAR("עלות השחר"),
    MISHEYAKIR("משיכיר"),
    HANETZ("הנץ החמה"),
    SOF_ZMAN_SHMA_MGA("סוף זמן ק\"ש מג\"א"),
    SOF_ZMAN_SHMA_GRA("סוף זמן ק\"ש גר\"א"),
    SOF_ZMAN_TFILA_MGA("סוף זמן תפילה מג\"א"),
    SOF_ZMAN_TFILA_GRA("סוף זמן תפילה גר\"א"),
    CHATZOT("חצות היום"),
    MINCHA_GEDOLA("מנחה גדולה"),
    MINCHA_KETANA("מנחה קטנה"),
    PLAG_HAMINCHA("פלג המנחה"),
    SHKIA("שקיעה"),
    TZEIT_HAKOCHAVIM("צאת הכוכבים"),
    TZEIT_SHABBAT("צאת שבת"),
    TZEIT_RABBEINU_TAM("רבנו תם"),
    CANDLE_LIGHTING("הדלקת נרות");

    companion object {
        fun fromNameOrNull(name: String): ZmanKind? = entries.firstOrNull { it.name == name }
    }
}

/** The concrete time of [kind] on this day (visible-netz-based when available). */
fun DayZmanim.instantOf(kind: ZmanKind): Instant? = when (kind) {
    ZmanKind.CHATZOT_LAYLA -> chatzotLayla
    ZmanKind.ALOT_HASHACHAR -> alotHashachar
    ZmanKind.MISHEYAKIR -> misheyakir66
    ZmanKind.HANETZ -> hanetzVisible ?: hanetzMishor
    ZmanKind.SOF_ZMAN_SHMA_MGA -> sofZmanShmaMga
    ZmanKind.SOF_ZMAN_SHMA_GRA -> sofZmanShmaGra
    ZmanKind.SOF_ZMAN_TFILA_MGA -> sofZmanTfilaMga
    ZmanKind.SOF_ZMAN_TFILA_GRA -> sofZmanTfilaGra
    ZmanKind.CHATZOT -> chatzot
    ZmanKind.MINCHA_GEDOLA -> minchaGedola
    ZmanKind.MINCHA_KETANA -> minchaKetana
    ZmanKind.PLAG_HAMINCHA -> plagHaminchaYalkutYosef
    ZmanKind.SHKIA -> shkia
    ZmanKind.TZEIT_HAKOCHAVIM -> tzeitHakochavim
    ZmanKind.TZEIT_SHABBAT -> tzeitShabbat
    ZmanKind.TZEIT_RABBEINU_TAM -> tzeitRabbeinuTam
    ZmanKind.CANDLE_LIGHTING -> candleLighting
}
