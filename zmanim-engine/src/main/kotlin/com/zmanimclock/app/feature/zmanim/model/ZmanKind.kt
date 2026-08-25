package com.zmanimclock.app.feature.zmanim.model

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.zmanimclock.app.feature.zmanim.engine.DayZmanim
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.GregorianCalendar

/**
 * Identifiers for alert-able zmanim.
 *
 * [name] is persisted VERBATIM in three places — an alarm stores it as
 * `alarm.zmanId` in Room, the next-zman filter as a DataStore string, each
 * widget's selection in SharedPreferences — so a constant may only be
 * renamed together with an entry in legacyName, which keeps a string the
 * user already saved resolving to the same zman. Every read of a stored
 * selection goes through [canonicalNames] for the same reason.
 *
 * [hebrewName] is the full list label, carrying the shita qualifier, and is
 * what notifications fall back to; screens localize via resources.
 * [shortName] is the compact display used in notifications — no degree or
 * minute suffixes, per the user's preference.
 */
enum class ZmanKind(val hebrewName: String, val shortName: String = hebrewName) {
    CHATZOT_LAYLA("חצות לילה"),
    ALOT_HASHACHAR("עלות השחר"),
    MISHEYAKIR("משיכיר"),
    HANETZ("הנץ החמה"),
    // Display only. HANETZ already falls back to the mishor time when no
    // ChaiTables data exists, so this row exists purely so that a user WITH
    // terrain data can also see the plain astronomical sunrise. It feeds
    // nothing: the seasonal-hour grid was always built on the mishor day
    // regardless (see MaranZmanimEngine), so adding this row changes no
    // computed value anywhere.
    HANETZ_MISHOR("הנץ מישור (אסטרונומי)", "הנץ מישור"),
    // TWO MGA readings of each morning deadline, and the suffix says which:
    // _72_ZMANIYOT is the luach's own shita — the day stretched by 72
    // ZMANIYOT minutes at each end — while _16_1_DEG is the fixed 16.1° solar
    // depression. They are ~9 minutes apart in Jerusalem, so a name that does
    // not say which one it is, is a trap: this pair used to be called _MGA and
    // _MGA_72, where the constant whose name ended in 72 was in fact the 16.1°
    // one. That already produced a wrong row in docs/VERIFICATION_TABLES.md.
    //
    // The retired names are still resolvable — see legacyName() below. They
    // were written verbatim into saved alarms, widget selections and the
    // next-zman filter, so they have to keep meaning what they meant.
    SOF_ZMAN_SHMA_MGA_72_ZMANIYOT("ק\"ש מג\"א 72 ד\"ז", "ק\"ש מג\"א"),
    SOF_ZMAN_SHMA_MGA_16_1_DEG("ק\"ש מג\"א 16.1°", "ק\"ש מג\"א"),
    SOF_ZMAN_SHMA_GRA("סוף זמן ק\"ש גר\"א", "ק\"ש גר\"א"),
    SOF_ZMAN_TFILA_MGA_72_ZMANIYOT("תפילה מג\"א 72 ד\"ז", "תפילה מג\"א"),
    SOF_ZMAN_TFILA_MGA_16_1_DEG("תפילה מג\"א 16.1°", "תפילה מג\"א"),
    SOF_ZMAN_TFILA_GRA("סוף זמן תפילה גר\"א", "תפילה גר\"א"),
    CHATZOT("חצות היום"),
    MINCHA_GEDOLA("מנחה גדולה"),
    MINCHA_KETANA("מנחה קטנה"),
    // BOTH plag rows are named for WHAT THEY MEASURE BACK FROM, and for
    // nothing else. Wording set by the app's owner.
    //
    // The two rows differ by exactly the 13.5 zmaniyot minutes between shkia
    // and tzeit — a machloket, not a defect, each verified against its own
    // authority (see PlagHaminchaTest). An unqualified "פלג המנחה" would leave
    // a reader no way to tell which of the two he is looking at.
    //
    // The earlier label credited the second row to the גר"א. Dropped: the
    // printed לוח אור החיים carries no such row and so gives it no name, and
    // for a Sephardi reader an Ashkenazi attribution invites him to discount a
    // time that R' David Yosef in fact holds. The measurement is the honest
    // description and needs no posek's name attached.
    //
    // NOTE the constant names stay as they are. They are persisted verbatim in
    // the widget's saved selection (WidgetPrefs / DesktopPrefs.widgetZmanim
    // store ZmanKind.name), so renaming one silently drops that row from every
    // widget already configured.
    PLAG_HAMINCHA("פלג המנחה (מצאה\"כ)", "פלג מצאה\"כ"),
    PLAG_HAMINCHA_GRA("פלג המנחה (מהשקיעה)", "פלג מהשקיעה"),
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
        fun fromNameOrNull(name: String): ZmanKind? =
            entries.firstOrNull { it.name == name } ?: legacyName(name)

        /**
         * Persisted [name] strings mapped onto the names those zmanim carry
         * TODAY, dropping any string that no longer names a zman at all.
         *
         * Call this wherever a stored selection is READ — the next-zman
         * filter, a widget's zman list, the set of kinds that have an alarm —
         * so that everything downstream compares current names only.
         */
        fun canonicalNames(names: Collection<String>): List<String> =
            names.mapNotNull { fromNameOrNull(it)?.name }

        /**
         * Names a constant used to carry, and the constant carrying that
         * meaning now.
         *
         * [name] is written verbatim into `alarm.zmanId` (Room), the
         * next-zman filter (DataStore) and each widget's selection
         * (SharedPreferences), so an entry here is the ONLY surviving record
         * of what an already-saved string meant. Never delete one, and never
         * hand a retired name to a different zman — an alarm set for one zman
         * would start ringing for another.
         */
        private fun legacyName(name: String): ZmanKind? = when (name) {
            "SOF_ZMAN_SHMA_MGA" -> SOF_ZMAN_SHMA_MGA_72_ZMANIYOT
            "SOF_ZMAN_SHMA_MGA_72" -> SOF_ZMAN_SHMA_MGA_16_1_DEG
            "SOF_ZMAN_TFILA_MGA" -> SOF_ZMAN_TFILA_MGA_72_ZMANIYOT
            "SOF_ZMAN_TFILA_MGA_72" -> SOF_ZMAN_TFILA_MGA_16_1_DEG
            else -> null
        }
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
    return ZmanKind.entries
        .filter { kind -> isZmanRelevantOn(kind, date, zone) }
        // הנץ מישור earns a row only when the user would actually READ a
        // different number. "A visible netz exists" is not that test: in
        // Jerusalem the terrain time lands in the same minute as the mishor
        // one, so gating on existence printed 05:58 twice. Gate on the
        // displayed minute instead — the same unit the row is rendered in.
        .filter { kind -> kind != ZmanKind.HANETZ_MISHOR || showsDistinctMishorNetz(zone) }
        .mapNotNull { kind -> instantOf(kind)?.let { kind to it } }
        .sortedBy { (_, instant) -> instant }
}

/**
 * Whether [kind] exists at all on [date] — independent of any particular
 * location's zmanim. Only two kinds are day-specific:
 *  - הדלקת נרות only on erev Shabbat (Friday) / erev Yom Tov.
 *  - צאת שבת only on Shabbat itself / a Yom Tov that is assur bemelacha.
 * Everything else is relevant every day. Callers that need a fire INSTANT
 * (the alarm scheduler) must still test the resulting instant's own local
 * date, since a zman can straddle midnight relative to the date it was
 * derived from (חצות לילה is the standing example) — this function alone
 * only answers "is this kind meaningful on this calendar day at all".
 */
fun isZmanRelevantOn(kind: ZmanKind, date: LocalDate, zone: ZoneId): Boolean {
    if (kind != ZmanKind.CANDLE_LIGHTING && kind != ZmanKind.TZEIT_SHABBAT) return true
    val jc = JewishCalendar(GregorianCalendar.from(date.atStartOfDay(zone))).apply { inIsrael = true }
    return when (kind) {
        ZmanKind.CANDLE_LIGHTING ->
            date.dayOfWeek == DayOfWeek.FRIDAY || jc.isErevYomTov || jc.isErevYomTovSheni
        ZmanKind.TZEIT_SHABBAT ->
            date.dayOfWeek == DayOfWeek.SATURDAY || jc.isYomTovAssurBemelacha
        else -> true
    }
}

/**
 * The single "next zman" ranked across [today]'s list AND [yesterday]'s
 * חצות לילה, if it has not passed yet.
 *
 * חצות לילה for a date D is D's chatzot + 12h — solar midnight — which lands
 * on the CALENDAR DAY AFTER D whenever chatzot itself is after 12:00 wall
 * clock (roughly half the year, whenever DST is in effect). So the חצות
 * לילה a user actually needs in the first ~40 minutes after midnight lives
 * in YESTERDAY's [DayZmanim], not today's: today's own row for this kind is
 * ~24 hours out. Every "next zman" consumer used to fetch only today (and,
 * for the widget/notification, tomorrow as a forward fallback) — never
 * yesterday — so during that window the headline pointed at the day's first
 * MORNING zman while חצות לילה, minutes away, sat unranked at the bottom of
 * today's own list showing a time that is in fact ~24h out.
 */
fun nextRelevantZman(
    today: DayZmanim,
    date: LocalDate,
    now: Instant,
    yesterday: DayZmanim?,
    /**
     * Which kinds may be the headline. EMPTY means "no preference" — every
     * zman is eligible, which is the original behaviour and stays the
     * default.
     *
     * A user who only cares about, say, שקיעה and ק"ש does not want the
     * headline walking through עלות, משיכיר and הנץ on the way there: with
     * those two selected, 06:30 already reads "הזמן הבא: ק״ש" even though
     * other zmanim fall in between. Filtering here rather than at each call
     * site keeps the home screen, the status notification and the widget
     * showing the same answer.
     *
     * Names are [ZmanKind.name] strings, matching how the preference is
     * persisted; an unknown name is simply ignored, so a selection saved by a
     * newer build cannot break an older one.
     */
    eligible: Set<String> = emptySet(),
): Pair<ZmanKind, Instant>? {
    // Resolved through fromNameOrNull rather than compared as raw strings, so
    // a filter saved under a name its constant no longer carries still picks
    // out the zman the user actually chose.
    val eligibleKinds = eligible.mapNotNullTo(mutableSetOf()) { ZmanKind.fromNameOrNull(it) }
    fun allowed(kind: ZmanKind) = eligible.isEmpty() || kind in eligibleKinds

    val candidates = today.relevantTimedZmanim(date)
        .filter { (kind, instant) -> instant.isAfter(now) && allowed(kind) }
    val fromYesterday = yesterday?.chatzotLayla
        ?.takeIf { it.isAfter(now) && allowed(ZmanKind.CHATZOT_LAYLA) }
        ?.let { ZmanKind.CHATZOT_LAYLA to it }
    return (if (fromYesterday != null) candidates + fromYesterday else candidates)
        .minByOrNull { (_, instant) -> instant }
}

/**
 * Whether הנץ מישור would render as a different clock time from הנץ.
 *
 * Compared at MINUTE resolution in the location's own zone, because that is
 * what the list shows: two instants seconds apart are the same row to a
 * reader, and printing them twice reads as a bug rather than as information.
 */
private fun DayZmanim.showsDistinctMishorNetz(zone: ZoneId): Boolean {
    val visible = hanetzVisible ?: return false
    val mishor = hanetzMishor ?: return false
    return visible.atZone(zone).truncatedTo(ChronoUnit.MINUTES) !=
        mishor.atZone(zone).truncatedTo(ChronoUnit.MINUTES)
}

/** The concrete time of [kind] on this day (visible-netz-based when available). */
fun DayZmanim.instantOf(kind: ZmanKind): Instant? = when (kind) {
    ZmanKind.CHATZOT_LAYLA -> chatzotLayla
    ZmanKind.ALOT_HASHACHAR -> alotHashachar
    ZmanKind.MISHEYAKIR -> misheyakir60 // luach standard: one shaah zmanit before the netz
    ZmanKind.HANETZ -> hanetzVisible ?: hanetzMishor
    ZmanKind.HANETZ_MISHOR -> hanetzMishor
    ZmanKind.SOF_ZMAN_SHMA_MGA_72_ZMANIYOT -> sofZmanShmaMga
    ZmanKind.SOF_ZMAN_SHMA_MGA_16_1_DEG -> sofZmanShmaMga16
    ZmanKind.SOF_ZMAN_SHMA_GRA -> sofZmanShmaGra
    ZmanKind.SOF_ZMAN_TFILA_MGA_72_ZMANIYOT -> sofZmanTfilaMga
    ZmanKind.SOF_ZMAN_TFILA_MGA_16_1_DEG -> sofZmanTfilaMga16
    ZmanKind.SOF_ZMAN_TFILA_GRA -> sofZmanTfilaGra
    ZmanKind.CHATZOT -> chatzot
    ZmanKind.MINCHA_GEDOLA -> minchaGedola
    ZmanKind.MINCHA_KETANA -> minchaKetana
    ZmanKind.PLAG_HAMINCHA -> plagHaminchaYalkutYosef
    ZmanKind.PLAG_HAMINCHA_GRA -> plagHaminchaGra
    ZmanKind.SHKIA -> shkia
    ZmanKind.TZEIT_HAKOCHAVIM -> tzeitHakochavim
    ZmanKind.TZEIT_LECHUMRA -> tzeitLechumra
    ZmanKind.TZEIT_SHABBAT -> tzeitShabbat
    ZmanKind.TZEIT_RABBEINU_TAM -> tzeitRabbeinuTam
    ZmanKind.CANDLE_LIGHTING -> candleLighting
}
