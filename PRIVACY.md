<div dir="rtl">

# מדיניות פרטיות — Halacha Clock (שעון מעורר - זמנים הלכתיים)

**תאריך תחילת תוקף:** 29 באוגוסט 2026
**חל על:** אפליקציית האנדרואיד **Halacha Clock** (`com.zmanimclock.app`) ותוכנת שולחן העבודה **Halacha Clock** ל-Windows.
**מפתח:** מוטי לוי (Moti Levi) — מפתח עצמאי, לא תאגיד.
**יצירת קשר:** [motiml77@gmail.com](mailto:motiml77@gmail.com)

---

## תמצית

Halacha Clock **לא אוספת, לא שומרת ולא משדרת שום מידע אישי**. האפליקציה לא דורשת הרשמה, לא כוללת פרסומות, לא כוללת שום כלי אנליטיקס או מעקב, ואינה משתפת מידע עם אף גורם שלישי. כל החישוב וההגדרות שלך נשמרים אך ורק על המכשיר שלך.

המשך המסמך מפרט את זה במדויק, כולל **כל הרשאה** שהאפליקציה מבקשת ולמה.

---

## מידע שהאפליקציה לא אוספת

Halacha Clock אינה אוספת ואינה משדרת אף אחד מהבאים: שם, כתובת דוא"ל, מספר טלפון, מיקום GPS מדויק (גם כשמופעל שימוש ב-GPS, המיקום משמש רק לחישוב מקומי על המכשיר ולעולם לא נשלח לשום שרת), אנשי קשר, תמונות, קבצים אחרים במכשיר, מזהי מכשיר, נתוני שימוש, קריסות, או כל מידע אחר שניתן לזהות אותך באמצעותו.

אין באפליקציה חשבון משתמש, התחברות, או שרת אחורי (backend) כלשהו של המפתח.

## מידע שנשמר — רק על המכשיר שלך

כדי שהאפליקציה תעבוד, היא שומרת מקומית בלבד (בתיקיית האחסון הפרטית של האפליקציה, בין אם ב-Room database, DataStore, או SharedPreferences של Android; ובדסקטופ בקובץ טקסט תחת פרופיל המשתמש):

- העיר/מיקום שבחרת לחישוב הזמנים ההלכתיים
- ההתראות והשעונים המעוררים שהגדרת
- הגדרות תצוגה (ערכת נושא, שפה, ווידג'טים)

מידע זה **לעולם לא עוזב את המכשיר שלך**. הסרת ההתקנה של האפליקציה מוחקת אותו לחלוטין.

## הרשאות שהאפליקציה מבקשת, ולמה

| הרשאה | לשם מה |
|---|---|
| **התראות** (Notifications) | הצגת ההתראה על הזמן הבא והשעונים המעוררים שהגדרת |
| **שעונים מדויקים** (Exact/Schedule Alarms) | הליבה של האפליקציה היא שעון מעורר הלכתי — חייבת לצלצל בדיוק בדקה הנכונה |
| **הפעלה מחדש של המכשיר** (Receive Boot Completed) | לחדש את השעונים המעוררים הפעילים אחרי הפעלה מחדש של הטלפון |
| **חלון במסך מלא** (Full-Screen Intent) | להציג את מסך הצלצול גם כשהטלפון נעול |
| **הצגה מעל אפליקציות אחרות** (Display over other apps) | **אופציונלי** — מוצע במסך ההיכרות; מאפשר למסך הצלצול להופיע גם כשהטלפון בשימוש פעיל. האפליקציה עובדת גם בלעדיה |
| **רטט, נעילת CPU, שינוי הגדרות שמע** | כדי שהצלצול באמת יצלצל וירטט בזמן ובעוצמה שהגדרת |
| **התעלמות מאופטימיזציית סוללה** | כדי שהמערכת לא "תרדים" את השעון המעורר ותמנע ממנו לצלצל |
| **אינטרנט וגישה למצב הרשת** | ראה הסעיף הבא — לשליפת טבלת הנץ נראה (ChaiTables) בלבד |

## החיבור היחיד לרשת: ChaiTables

תכונה אחת בלבד באפליקציה יוצרת קשר עם שרת חיצוני: שליפת טבלת "הנץ נראה" הציבורית מהאתר chaitables.com, המשמשת לחישוב אחת מהשיטות לזמן הנץ. הבקשה היא בקשת HTTP רגילה לנתונים ציבוריים (לא מותאמים אישית) — **שום מידע על המשתמש, המיקום, או המכשיר לא נשלח מעבר למה שמובנה בכל בקשת HTTP רגילה** (כתובת ה-IP, שאינה נשמרת או נרשמת בשום מקום על ידינו). אם אין חיבור לאינטרנט, האפליקציה ממשיכה לעבוד באמצעות שיטות חישוב אחרות.

## שיתוף מידע עם צד שלישי

**לא קיים.** האפליקציה אינה כוללת פרסומות, אינה כוללת שום SDK של אנליטיקס, פרסום, או מעקב (לא Google Analytics, לא Firebase Analytics, לא AdMob, ולא כל כלי דומה), ואינה מוכרת, משכירה או משתפת מידע עם אף גורם.

## אבטחת מידע

מכיוון שהמידע לעולם לא עוזב את המכשיר, האבטחה הרלוונטית היחידה היא אבטחת המכשיר עצמו — האפליקציה משתמשת במנגנוני האחסון המוגנים הרגילים של המערכת ההפעלה (אחסון פרטי לאפליקציה, שאינו נגיש לאפליקציות אחרות).

## פרטיות ילדים

האפליקציה אינה מיועדת לילדים מתחת לגיל 13 ואינה אוספת מידע מאף אחד, כולל ילדים.

## הזכויות שלך

מכיוון שלא קיים שום מידע עליך בשום שרת, אין צורך "לבקש מחיקה" — הסרת האפליקציה מהמכשיר (uninstall) מוחקת את כל המידע המקומי שלה באופן מיידי ומוחלט.

## שינויים במדיניות זו

אם מדיניות זו תשתנה, העדכון יפורסם בעמוד זה עם תאריך תחילת תוקף מעודכן.

## יצירת קשר

שאלות בנוגע לפרטיות: **[motiml77@gmail.com](mailto:motiml77@gmail.com)**

</div>

---

<div dir="ltr">

# Privacy Policy — Halacha Clock

**Effective date:** August 29, 2026
**Applies to:** the Android app **Halacha Clock** (`com.zmanimclock.app`) and the **Halacha Clock** Windows desktop application.
**Developer:** Moti Levi — an independent developer, not a company.
**Contact:** [motiml77@gmail.com](mailto:motiml77@gmail.com)

---

## Summary

Halacha Clock **does not collect, store, or transmit any personal information**. The app requires no account, contains no advertising, includes no analytics or tracking tools of any kind, and does not share information with any third party. All calculations and settings are kept exclusively on your own device.

The rest of this document spells that out precisely, including **every permission** the app requests and why.

---

## Information the app does not collect

Halacha Clock does not collect or transmit any of the following: your name, email address, phone number, precise GPS location (even when GPS is used, the location is used only for on-device calculation and is never sent to any server), contacts, photos, other files on your device, device identifiers, usage data, crash reports, or any other information that could identify you.

The app has no user account, no sign-in, and no developer-operated backend server of any kind.

## Information stored — on your device only

For the app to function, it stores the following locally only (in the app's private storage — a Room database, DataStore, or SharedPreferences on Android; a plain text file under your user profile on the desktop):

- The city/location you selected for halachic time calculations
- The alerts and alarms you configured
- Display settings (theme, language, widgets)

This information **never leaves your device**. Uninstalling the app deletes it completely.

## Permissions the app requests, and why

| Permission | Purpose |
|---|---|
| **Notifications** | Showing the next-zman status and the alarms you configured |
| **Exact/Schedule Alarms** | The app's core function is a halachic alarm clock — it must ring at the precise minute |
| **Receive Boot Completed** | Re-arming your active alarms after the phone restarts |
| **Full-Screen Intent** | Showing the ringing screen even while the phone is locked |
| **Display over other apps** | **Optional** — offered during onboarding; lets the ringing screen appear while the phone is in active use. The app works without it |
| **Vibrate, wake lock, modify audio settings** | So the alarm actually rings and vibrates at the time and volume you set |
| **Ignore battery optimizations** | So the system doesn't put the alarm to sleep and prevent it from ringing |
| **Internet and network state access** | See below — used only to fetch the ChaiTables sunrise table |

## The one network connection: ChaiTables

Exactly one feature contacts an external server: fetching the public "visible sunrise" table from chaitables.com, used for one of the sunrise-time calculation methods offered. This is a plain HTTP request for public, non-personalized data — **no information about you, your location, or your device is sent beyond what is inherent to any standard HTTP request** (your IP address, which we do not log or store). If there is no internet connection, the app continues to work using its other calculation methods.

## Sharing information with third parties

**None.** The app contains no advertising and no analytics, advertising, or tracking SDK of any kind (no Google Analytics, no Firebase Analytics, no AdMob, nor anything similar), and does not sell, rent, or share information with anyone.

## Data security

Since information never leaves the device, the relevant security is the device's own — the app uses the operating system's standard private storage, which is not accessible to other apps.

## Children's privacy

The app is not directed at children under 13 and does not collect information from anyone, including children.

## Your rights

Since no information about you exists on any server, there is nothing to request deletion of — uninstalling the app immediately and completely deletes all of its local data.

## Changes to this policy

If this policy changes, the update will be posted on this page with a revised effective date.

## Contact

Questions about privacy: **[motiml77@gmail.com](mailto:motiml77@gmail.com)**

</div>
