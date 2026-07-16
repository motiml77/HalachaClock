# תוכנית בנייה-מחדש — שעון זמנים הלכתי + שעון מעורר (Handoff)

> מסמך זה נכתב בסשן ענן (ללא אפשרות build) כדי להעביר את העבודה לסביבה מקומית עם Android SDK.
> אחרי שהבנייה-מחדש רצה — אפשר למחוק את הקובץ הזה.

## מטרה
אתחול מחדש של אפליקציית זמנים הלכתיים לאנדרואיד, **בעיצוב אחר**, כשהדגש כרגע על **הבקאנד/לוגיקה**.
העיצוב יגיע בהמשך מ**קלוד דיזיין** (ווב) ויוטמע ב-Compose דרך תפר design-tokens.

## החלטות שננעלו מול המשתמש
| נושא | החלטה |
|------|-------|
| פלטפורמה | נייטיב **Android Kotlin + Jetpack Compose** (נדרש: אזעקות אמינות, עבודת רקע, boot, התראות) |
| סדר עדיפויות | **בקאנד קודם**; UI דק וזמני |
| הטמעת עיצוב | design-tokens + composables נטולי-state → הטמעה מדויקת של עיצוב קלוד דיזיין |
| סמכות הלכתית | לוח **אור החיים** / **חזון יוסף – מורשת מרן** (הרב יצחק יוסף). אין API רשמי → משחזרים ומאמתים מול הלוח |
| מנוע חישוב (מצע) | **KosherJava** (`com.kosherjava:zmanim:2.5.0`, Maven Central) — מצע אסטרונומי בלבד, **לא פוסק הלכה** |
| הנץ הנראה | שכבת **ChaiTables** — הבסיס לכל זמני היום בשיטת מרן |
| בסיס מוסמך-מאושר | **Zemaneh-Yosef / RabbiOvadiahYosefCalendarAndroidApp** (נושא הסכמת הרב יצחק יוסף). הקוד הישן כאן כבר פורט שלו |
| מנוע אזעקות | Kotlin נקי, דפוסי אמינות מ-`yuriykulikov/AlarmClock` (Apache-2.0). לא Fossify (GPL) |
| רישוי | permissive-safe (להימנע מ-GPL) |

## מקור שחזור
כל קוד האפליקציה המקורי שמור ב-commit **`ccdad36`**: `git show ccdad36:<path>`.
כולל `ZmanimCalculator.kt` (מיישם כבר את שיטת מרן), תת-מערכת `feature/chaitables/**`, `scheduling/**`, `location/**`, קונפיג build.

## סטטוס
- ✅ commit `d95f6d0`: מחיקת כל הקבצים הלא-שייכים (הגהת ספר, spec, CSV, סקריפטים).
- ⏸️ הכתיבה-מחדש עצמה — לביצוע כאן, מקומית, עם build בכל שלב.

## צעדי פתיחה בסביבה המקומית
1. לוודא JDK 17 + Android SDK (platform 35, build-tools 35) + `ANDROID_HOME`.
2. `./gradlew :app:assembleDebug` על המצב הקיים (checkout זמני של `ccdad36` לקבצים) — לוודא שהבסיס בונה.
3. לבצע שלבים 0–6 למטה, כשכל שלב מתקמפל ומאומת.

## שלבי ביצוע (build בכל שלב)
- **שלב 0 — ריקון:** להסיר את קוד ה-UI/features הישן; להשאיר git+`.claude`; לעדכן `.gitignore` לאנדרואיד.
- **שלב 1 — שלד נקי:** `libs.versions.toml`, `build.gradle.kts`, `AndroidManifest` (הרשאות: `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `WAKE_LOCK`, מיקום), Application+Hilt, MainActivity+Compose RTL. **להסיר Retrofit/Hebcal (לא בשימוש); לשמור moshi-kotlin (cities.json).**
- **שלב 2 — מנוע זמנים לפי שיטת מרן:** מעל KosherJava כמצע. **הבסיס = הנץ הנראה**. היסמכויות (לאמת מול הלוח):
  - עלות = 72 ד"ז לפני הנץ · משיכיר · סוזק"ש GRA = 3 ש"ז מהנץ · חצות = 6 ש"ז · מנחה גדולה = 30 דק' אחרי חצות (מחמיר) · **פלג = שעה ו-15 ד"ז לפני צאת (ילקוט יוסף)** · צאת חול = **13.5 ד"ז אחרי שקיעה** · צאת שבת = 40 דק' · רבנו תם = 72 ד"ז.
- **שלב 3 — ChaiTables:** URL builder, fetcher, Jsoup parser, repository עם קאשינג לפי יום-בשנה, Room. הזרקת הנץ הנראה למנוע; fallback לזריחת מישור.
- **שלב 4 — מנוע אזעקות:** `AlarmScheduler` (exact alarms, Android 12+), `BootReceiver`, `AlarmTriggerReceiver`+`AlarmActivity` מסך-מלא, `AlarmSoundService` (עוצמה עולה, רטט), `ForegroundService`+`NotificationHelper`, `DailyRescheduleWorker`, Room `Alert*`, DataStore prefs.
- **שלב 5 — UI דק + תפר עיצוב:** תמה מבוססת-טוקנים; **לבטל dynamic color** (ב-`Theme.kt` להעביר `dynamicColor=false`, לשמור חתימת `ZmanimTheme`); composables נטולי-state; מסכי placeholder; README+CLAUDE.md.
- **שלב 6 — אימות:** `assembleDebug` עובר; בדיקות יחידה למנוע הזמנים מול **moreshet-maran.com** ומצב **"חזון יוסף" ב-royzmanim.com** (התאמה ללוח, לא רק סטייה אסטרונומית); commit+push.

## פרמטרים לאימות (התגלו בקריאת הקוד הישן — לא "לתקן" בניחוש)
- עלות/משיכיר במימוש הישן מבוססים על זריחת KosherJava (מישור/אסטרונומי), בעוד ששיטת מרן מבססת על **הנץ הנראה** — לסנכרן מול upstream של Zemaneh-Yosef ולאמת מול הלוח.
- משיכיר: מימוש ישן = 66/60 ד"ז לפני הנץ; לוודא מול הלוח.
- בין השמשות + עיגולים עירוניים — לאמת מול הלוח המודפס לפני "הלכה למעשה".

## סיכונים
- אמינות אזעקות תלוית-יצרן (Doze/OEM) — exact alarms + foreground service + reschedule ב-boot.
- ChaiTables גורד HTML חיצוני — לשמר קאשינג קבוע + fallback.
- דיוק הלכתי — לאמת מול luchot מוכרים.
