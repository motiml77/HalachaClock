package com.zmanimclock.app.feature.zmanim.data.model

import androidx.annotation.StringRes
import com.zmanimclock.app.R

enum class ZmanCategory {
    NIGHT, MORNING, MIDDAY, AFTERNOON, EVENING, SHABBAT_HOLIDAY, SEASONAL
}

enum class ZmanSource {
    KOSHERJAVA, ZEMANEH_YOSEF, HEBCAL, CALCULATED
}

enum class ZmanOpinion {
    GRA, MAGEN_AVRAHAM, RABBEINU_TAM, RAV_OVADIA, YEREIM, GEONIM, GENERAL
}

enum class ZmanId(
    @StringRes val hebrewNameRes: Int,
    val category: ZmanCategory,
    val source: ZmanSource,
    val opinion: ZmanOpinion,
    val defaultEnabled: Boolean,
    val infoText: String,
) {
    CHATZOT_LAYLA(
        R.string.zman_chatzot_layla, ZmanCategory.NIGHT, ZmanSource.KOSHERJAVA, ZmanOpinion.GENERAL,
        defaultEnabled = false,
        infoText = "חצות הלילה האסטרונומית - נקודת האמצע בין חצות היום של היום הנוכחי לחצות היום של למחרת. משמש לתיקון חצות, זמן אחרון לקידוש לבנה ועוד."
    ),
    ALOS_90(
        R.string.zman_alos_90, ZmanCategory.MORNING, ZmanSource.KOSHERJAVA, ZmanOpinion.GENERAL,
        defaultEnabled = false,
        infoText = "שיטת הראשונים שמהלך מיל = 22.5 דקות, 4 מילין = 90 דקות לפני הנץ. חישוב בדקות זמניות - משתנה לפי אורך היום."
    ),
    ALOS_72(
        R.string.zman_alos_72, ZmanCategory.MORNING, ZmanSource.KOSHERJAVA, ZmanOpinion.MAGEN_AVRAHAM,
        defaultEnabled = true,
        infoText = "שיטת הרמב\"ם והמגן אברהם: מהלך מיל = 18 דקות, 4 מילין = 72 דקות לפני הנץ. הזמן המקובל בלוח \"אור החיים\" של הרב עובדיה יוסף זצ\"ל."
    ),
    MISHEYAKIR_66(
        R.string.zman_misheyakir_66, ZmanCategory.MORNING, ZmanSource.ZEMANEH_YOSEF, ZmanOpinion.RAV_OVADIA,
        defaultEnabled = false,
        infoText = "זמן מוקדם לטלית ותפילין בשעת הדחק לפי לוח \"אור החיים\", ע\"פ הפרי חדש. 66 דקות זמניות לפני הנץ."
    ),
    MISHEYAKIR_60(
        R.string.zman_misheyakir_60, ZmanCategory.MORNING, ZmanSource.ZEMANEH_YOSEF, ZmanOpinion.RAV_OVADIA,
        defaultEnabled = true,
        infoText = "הזמן המוקדם ביותר להנחת טלית ותפילין לכתחילה לפי לוח \"אור החיים\". 60 דקות זמניות = שעה זמנית אחת לפני הנץ."
    ),
    HANETZ_SEA(
        R.string.zman_hanetz_sea, ZmanCategory.MORNING, ZmanSource.KOSHERJAVA, ZmanOpinion.GENERAL,
        defaultEnabled = true,
        infoText = "זריחת השמש בגובה פני הים. חישוב אסטרונומי סטנדרטי ללא התחשבות בגובה הגיאוגרפי."
    ),
    HANETZ_ELEVATED(
        R.string.zman_hanetz_elevated, ZmanCategory.MORNING, ZmanSource.KOSHERJAVA, ZmanOpinion.GENERAL,
        defaultEnabled = false,
        infoText = "זריחה מותאמת לגובה הגיאוגרפי. ככל שהמקום גבוה יותר - הזריחה מוקדמת יותר."
    ),
    HANETZ_VISIBLE(
        R.string.zman_hanetz_visible, ZmanCategory.MORNING, ZmanSource.ZEMANEH_YOSEF, ZmanOpinion.RAV_OVADIA,
        defaultEnabled = false,
        infoText = "הרגע שבו דיסקת השמש נראית מעל האופק בפועל, בהתחשבות בטופוגרפיה. הרב עובדיה יוסף זצ\"ל פסק שהנץ ההלכתי הוא הנראה ולא האסטרונומי."
    ),
    SOF_ZMAN_SHMA_GRA(
        R.string.zman_shma_gra, ZmanCategory.MORNING, ZmanSource.KOSHERJAVA, ZmanOpinion.GRA,
        defaultEnabled = true,
        infoText = "סוף זמן ק\"ש לפי הגר\"א: 3 שעות זמניות מהנץ. היום = הנץ עד שקיעה. שו\"ע או\"ח נח:א."
    ),
    SOF_ZMAN_SHMA_MGA(
        R.string.zman_shma_mga, ZmanCategory.MORNING, ZmanSource.KOSHERJAVA, ZmanOpinion.MAGEN_AVRAHAM,
        defaultEnabled = true,
        infoText = "סוף זמן ק\"ש לפי מג\"א: 3 שעות זמניות מעלוה\"ש. היום = עלוה\"ש 72 זמניות עד צה\"כ 72 זמניות."
    ),
    SOF_ZMAN_TFILA_GRA(
        R.string.zman_tfila_gra, ZmanCategory.MORNING, ZmanSource.KOSHERJAVA, ZmanOpinion.GRA,
        defaultEnabled = true,
        infoText = "סוף זמן שחרית לפי הגר\"א: 4 שעות זמניות מהנץ. שו\"ע או\"ח פט:א."
    ),
    SOF_ZMAN_TFILA_MGA(
        R.string.zman_tfila_mga, ZmanCategory.MORNING, ZmanSource.KOSHERJAVA, ZmanOpinion.MAGEN_AVRAHAM,
        defaultEnabled = true,
        infoText = "סוף זמן שחרית לפי מג\"א: 4 שעות זמניות מעלוה\"ש."
    ),
    MINCHA_GEDOLA(
        R.string.zman_mincha_gedola, ZmanCategory.MIDDAY, ZmanSource.KOSHERJAVA, ZmanOpinion.GENERAL,
        defaultEnabled = true,
        infoText = "הזמן המוקדם למנחה: חצי שעה זמנית אחרי חצות, אך לא פחות מ-30 דקות קבועות. שו\"ע או\"ח רלג:א."
    ),
    MINCHA_KETANA_161(
        R.string.zman_mincha_ketana_161, ZmanCategory.AFTERNOON, ZmanSource.KOSHERJAVA, ZmanOpinion.MAGEN_AVRAHAM,
        defaultEnabled = false,
        infoText = "מנחה קטנה לפי מג\"א (16.1 מעלות). 9.5 שעות זמניות מתחילת היום."
    ),
    MINCHA_KETANA_72(
        R.string.zman_mincha_ketana_72, ZmanCategory.AFTERNOON, ZmanSource.KOSHERJAVA, ZmanOpinion.MAGEN_AVRAHAM,
        defaultEnabled = false,
        infoText = "מנחה קטנה לפי מג\"א (72 דקות קבועות). 9.5 שעות זמניות מעלוה\"ש 72 דקות."
    ),
    PLAG_YALKUT_YOSEF(
        R.string.zman_plag_yy, ZmanCategory.AFTERNOON, ZmanSource.ZEMANEH_YOSEF, ZmanOpinion.RAV_OVADIA,
        defaultEnabled = true,
        infoText = "פלג המנחה לפי הילקוט יוסף: שעה ורבע זמנית לפני צאת הכוכבים (לא מהשקיעה). לוח \"אור החיים\"."
    ),
    SHKIA_SEA(
        R.string.zman_shkia_sea, ZmanCategory.EVENING, ZmanSource.KOSHERJAVA, ZmanOpinion.GENERAL,
        defaultEnabled = true,
        infoText = "שקיעת החמה בגובה פני הים - \"שקיעה מישורית\". חישוב אסטרונומי ללא התחשבות בגובה."
    ),
    SHKIA_ELEVATED(
        R.string.zman_shkia_elevated, ZmanCategory.EVENING, ZmanSource.KOSHERJAVA, ZmanOpinion.GENERAL,
        defaultEnabled = false,
        infoText = "שקיעה מותאמת לגובה הגיאוגרפי. ככל שהמקום גבוה יותר, השקיעה מאוחרת יותר."
    ),
    SHKIA_GENERAL(
        R.string.zman_shkia_general, ZmanCategory.EVENING, ZmanSource.KOSHERJAVA, ZmanOpinion.GENERAL,
        defaultEnabled = true,
        infoText = "שקיעת החמה לפי ההגדרה הנבחרת (מישורית או מותאמת גובה)."
    ),
    BEIN_HASHMASHOT_YEREIM(
        R.string.zman_bein_hashmashot, ZmanCategory.EVENING, ZmanSource.KOSHERJAVA, ZmanOpinion.YEREIM,
        defaultEnabled = false,
        infoText = "בין השמשות לפי היראים (רבי אליעזר ממיץ): 13.5 דקות לפני השקיעה. 3/4 מיל לפי 18 דק' למיל."
    ),
    TZAIS_3_8(
        R.string.zman_tzais_3_8, ZmanCategory.EVENING, ZmanSource.KOSHERJAVA, ZmanOpinion.GEONIM,
        defaultEnabled = false,
        infoText = "צה\"כ לפי הגאונים: 3.8° מתחת לאופק. מקביל ל-13.5 דקות בירושלים בתקופת ניסן."
    ),
    TZAIS_4_61(
        R.string.zman_tzais_4_61, ZmanCategory.EVENING, ZmanSource.KOSHERJAVA, ZmanOpinion.GEONIM,
        defaultEnabled = false,
        infoText = "צה\"כ לפי הגאונים: 4.61° (18 דקות, 3/4 מיל לפי 24 דק' למיל)."
    ),
    TZAIS_4_8(
        R.string.zman_tzais_4_8, ZmanCategory.EVENING, ZmanSource.KOSHERJAVA, ZmanOpinion.GEONIM,
        defaultEnabled = false,
        infoText = "צה\"כ לפי הגאונים: 4.8° מתחת לאופק."
    ),
    TZAIS_5_95(
        R.string.zman_tzais_5_95, ZmanCategory.EVENING, ZmanSource.KOSHERJAVA, ZmanOpinion.GEONIM,
        defaultEnabled = false,
        infoText = "צה\"כ לפי הגאונים: 5.95° (24 דקות בירושלים, מהלך מיל שלם)."
    ),
    TZAIS_7_67(
        R.string.zman_tzais_7_67, ZmanCategory.EVENING, ZmanSource.KOSHERJAVA, ZmanOpinion.GEONIM,
        defaultEnabled = false,
        infoText = "צה\"כ: 7.67° - הרב משה פיינשטיין (אגרות משה אהע\"ז ד:ד) והרב שמואל קמנצקי."
    ),
    TZAIS_8_5(
        R.string.zman_tzais_8_5, ZmanCategory.EVENING, ZmanSource.KOSHERJAVA, ZmanOpinion.GEONIM,
        defaultEnabled = true,
        infoText = "צה\"כ ברירת מחדל: 8.5° - הרב מאיר פוזן (\"אור מאיר\"). 3 כוכבים קטנים נראים."
    ),
    TZAIS_9_75(
        R.string.zman_tzais_9_75, ZmanCategory.EVENING, ZmanSource.KOSHERJAVA, ZmanOpinion.GEONIM,
        defaultEnabled = false,
        infoText = "צה\"כ: 9.75° (60 דקות בתקופת ניסן). שיטת הרב אליהו הנקין."
    ),
    TZAIS_13_5_ZMANIYOT(
        R.string.zman_tzais_13_5, ZmanCategory.EVENING, ZmanSource.ZEMANEH_YOSEF, ZmanOpinion.RAV_OVADIA,
        defaultEnabled = true,
        infoText = "צה\"כ לפי לוח \"אור החיים\": 13.5 דקות זמניות אחרי השקיעה. בקיץ מאוחר יותר, בחורף מוקדם."
    ),
    TZAIS_LECHUMRA(
        R.string.zman_tzais_lechumra, ZmanCategory.EVENING, ZmanSource.ZEMANEH_YOSEF, ZmanOpinion.RAV_OVADIA,
        defaultEnabled = false,
        infoText = "צה\"כ מחמיר: 20 דקות זמניות. משמש לסוף תעניות, הדלקת נרות מיו\"ט ליו\"ט."
    ),
    TZAIS_SHABBAT_8_5(
        R.string.zman_tzais_shabbat_8_5, ZmanCategory.SHABBAT_HOLIDAY, ZmanSource.KOSHERJAVA, ZmanOpinion.GENERAL,
        defaultEnabled = true,
        infoText = "צאת שבת לפי 8.5 מעלות מתחת לאופק. זמן שבו נראים 3 כוכבים קטנים. שיטה מקובלת ומחמירה."
    ),
    TZAIS_SHABBAT_YY(
        R.string.zman_tzais_shabbat_yy, ZmanCategory.SHABBAT_HOLIDAY, ZmanSource.ZEMANEH_YOSEF, ZmanOpinion.RAV_OVADIA,
        defaultEnabled = true,
        infoText = "צאת שבת לפי דעת הילקוט יוסף (הרב יצחק יוסף שליט\"א) - הדעה המקילה ביותר בלוח. מבוסס על חישוב מעלות שמבטיח תמיד 30+ דקות אחרי השקיעה בארץ ישראל, עם מינימום של 20 דקות."
    ),
    TZAIS_SHABBAT_AH(
        R.string.zman_tzais_shabbat_ah, ZmanCategory.SHABBAT_HOLIDAY, ZmanSource.ZEMANEH_YOSEF, ZmanOpinion.RAV_OVADIA,
        defaultEnabled = false,
        infoText = "צאת שבת לפי לוח \"עמודי הוראה\" (הרב דהן): 7.165° מתחת לאופק - מבטיח תמיד 30+ דקות אחרי השקיעה בנקודה הצפונית ביותר בארץ. מינימום 20 דקות."
    ),
    TZAIS_SHABBAT_AH_40(
        R.string.zman_tzais_shabbat_ah_40, ZmanCategory.SHABBAT_HOLIDAY, ZmanSource.ZEMANEH_YOSEF, ZmanOpinion.RAV_OVADIA,
        defaultEnabled = false,
        infoText = "כמו צאת שבת עמודי הוראה, עם תקרה של 40 דקות. לפי הרב מאיר גבריאל אלבז."
    ),
    TZAIS_RT_AH(
        R.string.zman_tzais_rt, ZmanCategory.EVENING, ZmanSource.ZEMANEH_YOSEF, ZmanOpinion.RABBEINU_TAM,
        defaultEnabled = false,
        infoText = "צאת ר\"ת עמודי הוראה: המוקדם מבין 72 דקות קבועות ו-72 זמניות. הרב עובדיה החמיר כזמניות, הרב דהן מיקל."
    ),
    SHAAH_ZMANIT_GRA(
        R.string.zman_shaah_gra, ZmanCategory.SEASONAL, ZmanSource.KOSHERJAVA, ZmanOpinion.GRA,
        defaultEnabled = true,
        infoText = "שעה זמנית גר\"א: (שקיעה - הנץ) / 12. בקיץ ~75 דקות, בחורף ~50 דקות."
    ),
    SHAAH_ZMANIT_MGA(
        R.string.zman_shaah_mga, ZmanCategory.SEASONAL, ZmanSource.KOSHERJAVA, ZmanOpinion.MAGEN_AVRAHAM,
        defaultEnabled = false,
        infoText = "שעה זמנית מג\"א: (צה\"כ 72 זמניות - עלוה\"ש 72 זמניות) / 12. תמיד ארוכה יותר מגר\"א."
    ),
    KIDDUSH_LEVANA_3(
        R.string.zman_kiddush_levana_3, ZmanCategory.SEASONAL, ZmanSource.KOSHERJAVA, ZmanOpinion.GENERAL,
        defaultEnabled = false,
        infoText = "תחילת קידוש לבנה לפי רבינו יונה: 3 ימים (72 שעות) אחרי המולד."
    ),
    KIDDUSH_LEVANA_7(
        R.string.zman_kiddush_levana_7, ZmanCategory.SEASONAL, ZmanSource.KOSHERJAVA, ZmanOpinion.GENERAL,
        defaultEnabled = true,
        infoText = "תחילת קידוש לבנה לפי רוב הפוסקים (שו\"ע, רמ\"א): 7 ימים אחרי המולד."
    ),
    KIDDUSH_LEVANA_15(
        R.string.zman_kiddush_levana_15, ZmanCategory.SEASONAL, ZmanSource.KOSHERJAVA, ZmanOpinion.GENERAL,
        defaultEnabled = true,
        infoText = "סוף זמן קידוש לבנה: 15 ימים מהמולד (ירח מלא)."
    ),
    CANDLE_LIGHTING(
        R.string.zman_candle_lighting, ZmanCategory.SHABBAT_HOLIDAY, ZmanSource.KOSHERJAVA, ZmanOpinion.GENERAL,
        defaultEnabled = true,
        infoText = "הדלקת נרות שבת/חג. אשכנז: 18 דקות לפני השקיעה. ירושלים: 40 דקות. שו\"ע או\"ח רסג:ד."
    );
}

data class ZmanTime(
    val id: ZmanId,
    val time: java.util.Date?,
    val isPassed: Boolean = false,
    val isNext: Boolean = false,
    val hasAlert: Boolean = false,
    val displayValue: String? = null,
)

data class DayZmanim(
    val date: java.util.Date,
    val hebrewDate: String,
    val locationName: String,
    val zmanim: List<ZmanTime>,
    val shaahZmanisGra: Long = 0,
    val shaahZmanisMga: Long = 0,
)
