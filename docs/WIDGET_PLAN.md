# תוכנית ווידג'ט מסך-בית — "שעון זמנים"

> ווידג'ט אנדרואיד שמציג זמנים נבחרים + הזמן הבא + כמה זמן נשאר לו.
> סטטוס: מתוכנן (טרם ממומש). מבוסס על התשתית הקיימת (StatusNotificationReceiver, ZmanimRepository).

## 1. החלטת טכנולוגיה: RemoteViews קלאסי (לא Glance)

הדרישה הקריטית היא **"כמה זמן נשאר" שמתעדכן חי**. עדכון ווידג'ט כל דקה מהאפליקציה = הרג סוללה ו-throttling של המערכת (עדכוני AppWidget מוגבלים). הפתרון המקצועי:

- **`Chronometer` עם `setChronometerCountDown(true)`** בתוך RemoteViews — המערכת עצמה מציירת ספירה לאחור חיה (כמו בטיימר של Google Clock), **בלי אף עדכון מהאפליקציה**. Glance לא תומך ב-Chronometer — לכן RemoteViews קלאסי.
- עדכון תוכן (הזמנים עצמם) נדרש רק ב**מעברי זמן** — בדיוק הטריגר שכבר קיים ל-StatusNotificationReceiver (AlarmManager על הזמן הבא + boot + שינוי שעה/עיר). מוסיפים לאותו receiver שורת `appWidgetManager.updateAppWidget(...)`.

## 2. מבנה הווידג'ט (עיצוב 1D)

```
┌──────────────────────────────────┐
│  🔆 הזמן הבא: מנחה קטנה           │  ← titleMedium, זהב #F5C518
│  16:53   ⏳ 1:06:32 (חי)          │  ← Rubik ענק + Chronometer
│──────────────────────────────────│
│  הנץ החמה              05:52     │  ← שורות הזמנים שנבחרו
│  סוזק"ש גר"א           09:21     │     (עד ~5, לפי גובה)
│  שקיעה                 19:45     │
└──────────────────────────────────┘
```

- רקע: נייבי `#123A8B` (בהיר) / `#0B1220` (כהה) — `values-night` נפרד; פינות 20dp.
- גדלים: 4×2 ברירת מחדל; resize אנכי מוסיף/גורע שורות (`OPTION_APPWIDGET_MIN_HEIGHT`).
- הקשה על הווידג'ט ⟶ פותחת את האפליקציה.

## 3. בחירת הזמנים ע"י המשתמש

- **Activity קונפיגורציה** (`android.appwidget.action.APPWIDGET_CONFIGURE`) בעיצוב 1D: רשימת 17 הזמנים עם checkboxes + תצוגה מקדימה; ברירת מחדל: הנץ, סוזק"ש גר"א, שקיעה, צאת.
- שמירה: DataStore key לכל widgetId (`widget_{id}_zmanim` = Set<String>) — תומך בכמה ווידג'טים עם בחירות שונות.
- עריכה חוזרת: הקשה ארוכה ⟶ "הגדרות ווידג'ט" (reconfigurable ב-widget_info).

## 4. צנרת עדכון (ממחזרת את הקיים)

```
ZmanWidgetProvider (AppWidgetProvider)
 ├─ onUpdate/onAppWidgetOptionsChanged → WidgetRenderer.render(widgetId)
 └─ ACTION_WIDGET_REFRESH ← נשלח מ-StatusNotificationReceiver (מעבר זמן),
                             RescheduleWorker, BootReceiver, בחירת עיר
WidgetRenderer
 ├─ קורא בחירת זמנים מ-DataStore
 ├─ ZmanimRepository.getDayZmanim(היום; מחר אם נגמר היום)
 ├─ ממלא RemoteViews: זמנים + הזמן הבא + Chronometer base =
 │    SystemClock.elapsedRealtime() + (fireTime - now)
 └─ fallback חסר-נתונים: "פתח את האפליקציה לבחירת עיר"
```

- **אמינות**: גם `updatePeriodMillis` = 30 דק' כרשת ביטחון (מינימום מערכת) — מיישר סטייה אם pinג פוספס.
- **חצות**: ה-ping הקיים כבר מתוזמן לזמן האחרון של היום; אחריו הרינדור עובר אוטומטית ליום המחרת.

## 5. שלבי מימוש (כ-3-4 שעות עבודה)

| # | שלב | קבצים |
|---|---|---|
| 1 | widget_info.xml + Provider + Manifest | `res/xml/`, `ZmanWidgetProvider.kt` |
| 2 | layout-ים RemoteViews (בהיר/כהה, 2 גבהים) | `res/layout/widget_*.xml` |
| 3 | WidgetRenderer + hook ב-StatusNotificationReceiver | `WidgetRenderer.kt` |
| 4 | Activity קונפיגורציה + DataStore per-widget | `WidgetConfigActivity.kt` |
| 5 | בדיקות אמולטור: מעבר זמן, החלפת עיר, ריסטארט, resize | — |

## 6. סיכונים ידועים
- OEM-ים (שיאומי) הורגים עדכוני ווידג'ט ברקע — ה-Chronometer החי ממתן כי אין תלות בעדכונים תכופים.
- RTL ב-RemoteViews: לוודא `android:layoutDirection="rtl"` על השורש.
- פונט מותאם (Rubik) לא נתמך ב-RemoteViews לפני API 31 — ליפול ל-sans-serif-condensed טאבולרי.
