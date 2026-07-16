# תוכנית מימוש — תיקוני אמינות + פיצ'רים חדשים + ווידג'ט

> מסמך עבודה לסשן המימוש (Claude Code / Opus). כל סעיף = יחידת עבודה עצמאית עם
> קבצים, שדות ומבחני-קבלה. **סדר הביצוע המומלץ = סדר הסעיפים.**
> הקשר: אפליקציית זמנים+מעורר, ענף `claude/project-cleanup-restart-oy0hsf`,
> עיצוב 1D (README בחבילת קלוד דיזיין), build עם `TEMP=C:\gtmp` (ראה זיכרון פרויקט).

---

## חלק א' — תיקוני אמינות (קריטיים)

### A1. ✅ קריסת ForegroundServiceDidNotStartInTime — **כבר תוקן** (`753d920`)
`AlarmSoundService.start()` מפרסם placeholder מיידית לפני גישת DB. אין עבודה נוספת.

### A2. Full-Screen Intent באנדרואיד 14+ (חסימת מסך הצלצול)
**הבעיה:** מ-API 34 המערכת/המשתמש יכולים לשלול `USE_FULL_SCREEN_INTENT`; בלעדיו מסך הנרות/הצלצול לא ייפתח — רק התראה רגילה.
**הפתרון:**
1. `NotificationHelper` — פונקציה `canUseFullScreenIntent(): Boolean`:
   `Build.VERSION.SDK_INT < 34 || notificationManager.canUseFullScreenIntent()`.
2. **אשף ההרשאות** (`OnboardingScreen`) — כרטיס רביעי "מסך צלצול מלא" (מוצג רק
   כשה-API רלוונטי ולא מאושר): CTA פותח
   `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` עם package URI.
3. **כרטיס בריאות בהגדרות** — כמו כרטיס האזעקות המדויקות הקיים.
4. **Fallback בזמן צלצול:** גם בלי ההרשאה הצליל+רטט עובדים (השירות מצלצל בכל
   מקרה); ההתראה נשארת ongoing עם pri MAX — לוודא `setContentIntent` פותח את
   AlarmActivity בהקשה.
**קבצים:** `NotificationHelper.kt`, `OnboardingScreen.kt`, `SettingsScreen.kt`.
**קבלה:** אמולטור API 35: `adb shell appops set com.zmanimclock.app USE_FULL_SCREEN_INTENT deny` ⟵ הכרטיסים מופיעים; צלצול עדיין משמיע קול והקשה על ההתראה פותחת את המסך.

### A3. Direct Boot — שעון לפני פתיחת נעילה ראשונה אחרי ריסטארט
**הבעיה:** כל האחסון שלנו credential-encrypted; אחרי ריסטארט, עד שהמשתמש פותח
נעילה פעם אחת — AlarmManager לא ישוחזר (BOOT_COMPLETED לא מגיע), וגם אם יגיע
טריגר — Room/DataStore יזרקו. שעון ותיקין של 04:30 לא יצלצל אם הטלפון קובע
עדכון-לילה.
**הפתרון (מדורג):**
1. **Storage:** ליצור `deviceProtectedContext = context.createDeviceProtectedStorageContext()`.
   - `ZmanimDatabase` (שעונים + קאש נצים) עובר ל-DPS: ב-`AppModule`, לבנות את
     Room עם ה-context המוגן + במיגרציה חד-פעמית
     `deviceProtectedContext.moveDatabaseFrom(context, "zmanim.db")`.
   - העדפות קריטיות (עיר/קואורדינטות/אזור-זמן בלבד) — עותק-צל ב-DataStore על
     DPS (`user_prefs_dps`), מסונכרן בכתיבה מ-`UserPreferencesRepository`
     (ההעדפות המלאות נשארות במקום).
2. **Manifest:** `android:directBootAware="true"` על: `BootReceiver`,
   `AlarmTriggerReceiver`, `AlarmSoundService`, `AlarmActivity`,
   `StatusNotificationReceiver`; ל-BootReceiver להוסיף
   `ACTION_LOCKED_BOOT_COMPLETED` ל-intent-filter (ולשמר גם BOOT_COMPLETED —
   מגיע פעמיים, ה-reschedule אידמפוטנטי).
3. **קוד:** ב-`RescheduleWorker`/`AlarmScheduler` — אם `UserManager.isUserUnlocked == false`,
   לקרוא העדפות מעותק ה-DPS; WorkManager לא זמין לפני unlock ⟵ ב-BootReceiver,
   כשנעול, לקרוא ישירות ל-`alarmScheduler.rescheduleAll()` בתוך `goAsync`
   (בלי WorkManager), עם try/catch.
4. **חריג מודע:** ChaiTables fetch רשת לא ירוץ לפני unlock — הקאש ב-DPS מכסה.
**קבצים:** `AppModule.kt`, `ZmanimDatabase.kt`, `UserPreferencesRepository.kt`,
`BootReceiver.kt`, `AndroidManifest.xml`.
**קבלה:** `adb reboot` ⟵ לפני פתיחת נעילה (`adb shell locksettings set-pin 1234` לבדיקה)
שעון שנקבע לדקה הקרובה מצלצל מעל מסך הנעילה.

### A4. רשת בתוך BroadcastReceiver (שורת הסטטוס)
**הבעיה:** `StatusNotificationReceiver` קורא `getDayZmanim` שעלול להפעיל fetch
רשת מלא של ChaiTables — חריגה מתקציב `goAsync` (~10 שנ') ו-ANR פוטנציאלי.
**הפתרון:**
1. `ChaiTablesRepository.getVisibleSunrise(..., allowNetwork: Boolean = true)` —
   כש-false: שלבי הקאש/מטרו בלבד, בלי fetch ובלי sentinel-כתיבה.
2. `ZmanimRepository.getDayZmanim(..., cacheOnly: Boolean = false)` מעביר הלאה.
3. ה-Receiver קורא עם `cacheOnly=true`; אם חסר נץ נראה — המנוע ממילא נופל
   למישור (basedOnVisibleSunrise=false) — מציג נכון.
4. השלמת הקאש נשארת ל-`ChaiTablesRefreshWorker` (כבר קיים, network-gated).
**קבצים:** `ChaiTablesRepository.kt`, `ZmanimRepository.kt`, `StatusNotificationReceiver.kt`.
**קבלה:** מחיקת נתוני האפליקציה + כיבוי רשת + פתיחה ⟵ שורת הסטטוס מופיעה מיד (מישור), בלי חריגות בלוג.

### A5. אייקונים — התראה מונוכרומטית + אייקון אפליקציה
**הפתרון:**
1. `res/drawable/ic_stat_zman.xml` — וקטור מונוכרומטי (פעמון-שעון, path יחיד,
   `android:tint`-able, 24dp) — להחליף את כל ה-`R.drawable.ic_launcher_foreground`
   ב-`NotificationHelper` וב-services. צבע אקסנט: `#123A8B`.
2. אייקון אפליקציה אדפטיבי: foreground = שעון-מעורר בקווי זהב על עיגול,
   background = נייבי `#123A8B` מלא; `mipmap-anydpi-v26` + מונוכרום (API 33 themed icon)
   באותו vector של ic_stat.
**קבצים:** `res/drawable/`, `res/mipmap-anydpi-v26/`, `NotificationHelper.kt`.
**קבלה:** בסטטוס-בר נראה פעמון חד ולא ריבוע; אייקון בהיר/כהה/themed תקין.

### A6. עמידות מול OEM (שיאומי/סמסונג) + בדיקת צלצול
**הפתרון:**
1. כרטיס "בדיקת אמינות" בהגדרות:
   - כפתור **"צלצל בעוד דקה"** — יוצר שעון חד-פעמי now+60s (משתמש בצנרת הקיימת,
     נמחק אוטומטית) — נותן למשתמש ביטחון ומאתר בעיות OEM מיד.
   - אם `Build.MANUFACTURER` ∈ {xiaomi, oppo, vivo, huawei, samsung, oneplus…} —
     שורת הסבר + כפתור שמנסה לפתוח את מסך ה-autostart היצרני
     (רשימת ComponentName ידועים, בתוך try/catch, fallback ל-App Info).
2. טקסט הסבר קצר: "במכשירי שיאומי יש לאשר 'הפעלה אוטומטית'".
**קבצים:** `SettingsScreen.kt`, `SettingsViewModel.kt` (+ helper `OemHelper.kt`).
**קבלה:** הכפתור מצלצל תוך דקה עם המסך כבוי; במכשיר שיאומי אמיתי — הקישור נפתח.

### A7. צליל מותאם שנעלם + רטט-בלבד FGS
**הפתרון:**
1. `startSound`: try על ה-URI המותאם; ב-catch — fallback מפורש ל-
   `RingtoneManager.getDefaultUri(TYPE_ALARM)` (כיום נכשל בשקט ⟵ שעון אילם!).
   בנוסף, בעת בחירת צליל בעורך — `contentResolver.takePersistableUriPermission`
   בתוך runCatching (לא כל ה-URIs תומכים — זה best-effort).
2. רטט-בלבד: להוסיף ב-Manifest `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` +
   `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
   android:value="alarm_vibration"/>`; ב-`goForeground` לבחור type לפי
   `alarm.soundEnabled`.
**קבצים:** `AlarmSoundService.kt`, `AlarmEditScreen.kt`, `AndroidManifest.xml`.
**קבלה:** בחירת צליל, מחיקתו מהמכשיר ⟵ השעון מצלצל בברירת מחדל; שעון רטט-בלבד רץ בלי אזהרת FGS בלוג.

---

## חלק ב' — פיצ'רים חדשים (מהמחקר)

### B1. Wake-Up Check — "אתה באמת ער?"
**מוצר:** per-alarm, כבוי כברירת מחדל. אחרי אישור הצלצול, בעוד X דק' (3/5/10)
מופיעה התראה בולטת "אתה ער? הקש לאישור"; אם אין אישור תוך 2 דק' — **השעון
מצלצל שוב במלואו**.
**מימוש:**
1. `AlarmEntity` + שדה `wakeCheckMinutes: Int = 0` (0=כבוי). Room v4 (destructive).
2. עורך: אחרי סקשן הנודניק — מתג "בדיקת ערות" + צ'יפים 3/5/10.
3. `AlarmSoundService.dismiss()` — אם `wakeCheckMinutes>0`:
   `alarmManager.setExactAndAllowWhileIdle(now+X*60s, PendingIntent(WakeCheckReceiver, alarmId))`
   (request code ייעודי: `alarmId*10+5`).
4. חדש `WakeCheckReceiver` (directBootAware):
   - מפרסם התראת HIGH עם כפתור "אני ער ✓" (PendingIntent לביטול) על ערוץ חדש
     `CHANNEL_WAKE_CHECK` (חשיבות HIGH, עם צליל קצר).
   - מתזמן re-ring: `setExactAndAllowWhileIdle(now+2min, triggerPendingIntent(alarmId))`
     — אותה צנרת של צלצול רגיל.
   - הקשה על "אני ער" ⟵ `WakeCheckConfirmReceiver` מבטל את ה-re-ring ומנקה התראה.
5. כרטיס השעון מציג "· בדיקת ערות" בשורת התיאור.
**קבלה:** שעון עם check=3 ⟵ אישור ⟵ אחרי 3 דק' התראה; בלי מגע ⟵ אחרי 2 דק' צלצול מלא; עם אישור ⟵ שקט.

### B2. דילוג על הפעם הבאה (Skip Next)
**מוצר:** בכרטיס שעון פעיל — אייקון/כפתור "דלג" (או swipe): הצלצול הקרוב מבוטל,
החוזרים שאחריו נשארים. חיווי ברור + ביטול בהקשה חוזרת.
**מימוש:**
1. `AlarmEntity` + `skipUntilEpochMs: Long = 0`.
2. `AlarmsViewModel.skipNext(alarm)`: מחשב `computeNextOccurrence`, כותב
   `skipUntil = fire+1min`, מריץ reschedule. `undoSkip` מאפס.
3. `AlarmScheduler`: אחרי חישוב מועד — אם `fire.toEpochMilli() <= alarm.skipUntilEpochMs`
   ממשיך לחפש את המועד הבא (בלולאת הימים הקיימת; ל-FIXED — להזין
   `nextFixedOccurrence` עם `now = max(now, skipUntil)`).
4. UI: תג זהב "מדלג על מחר" + התווית "בעוד…" מחושבת כבר אחרי הדילוג.
**קבלה:** דילוג ⟵ התווית קופצת ליום שאחרי; ביטול ⟵ חוזרת; ריסטארט לא מאבד את הדילוג.

### B3. הגבלת נודניקים (Anti-Snooze)
**מוצר:** per-alarm: בלי הגבלה / 3 / 1 / ללא נודניק.
**מימוש:**
1. `AlarmEntity` + `maxSnoozes: Int = -1` (-1=∞, 0=אין נודניק).
2. `AlarmEntity` + `snoozeCount: Int = 0` — מתאפס בכל dismiss ובכל scheduleNextOccurrence.
3. `AlarmSoundService.snooze()`: אם `snoozeCount>=maxSnoozes` (כש-maxSnoozes≥0)
   ⟵ מתעלם (לוג); אחרת מגדיל את המונה ב-DB ומתזמן.
4. `AlarmActivity`: מקבל extra `snoozesLeft`; כפתור הנודניק מציג "נודניק (נשארו 2)"
   או מוסתר כשאפס. **חריג מוצרי:** כשיש תרגיל-כיבוי וגם נודניק אסור — הכפתור
   מוצג בכל זאת בעדינות אחרי 60 שנ' צלצול (בטיחות למשתמש מבוהל).
5. עורך: סקשן נודניק — SegmentedButton ∞/3/1/בלי.
**קבלה:** שעון עם מקס 1 ⟵ נודניק ראשון עובד, בצלצול השני הכפתור נעלם.

### B4. ווידג'ט מסך-בית — לפי [WIDGET_PLAN.md](WIDGET_PLAN.md) (כבר מפורט)
סדר פנימי: Provider+layout ⟵ Renderer+hook ב-StatusNotificationReceiver ⟵
Config Activity ⟵ בדיקות. **הערה ל-DPS (A3):** העדפות הווידג'ט גם הן ב-DPS
כדי שהווידג'ט יעבוד אחרי ריסטארט לפני unlock.

---

## סדר ביצוע מומלץ לסשן המימוש

| שלב | סעיפים | הערה |
|---|---|---|
| 1 | A5 (אייקונים) | קטן ומיידי, משפיע על כל ההתראות |
| 2 | A7 + A2 | תיקוני צלצול/הרשאות — לפני פיצ'רים |
| 3 | A4 | ניקוי רשת מה-receiver |
| 4 | B2 + B3 | שדות Entity חדשים במיגרציה אחת (v4) יחד עם B1 |
| 5 | B1 (Wake-Up Check) | תלוי בשדות של שלב 4 |
| 6 | A3 (Direct Boot) | הכי עדין — אחרי שהכל יציב, בדיקת reboot מלאה |
| 7 | A6 (OEM + צלצל-בעוד-דקה) | |
| 8 | B4 (ווידג'ט) | עצמאי, אחרון |

**כללי עבודה:** build+בדיקות אחרי כל שלב · צילום אמולטור לכל UI חדש · commit
per-שלב · לא לשבור את חוזה העיצוב 1D (טוקנים בלבד) · הנודניק לעולם לא נחסם
לחלוטין בזמן תרגיל (עקרון קיים).
