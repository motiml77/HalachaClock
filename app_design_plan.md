# Halachic Alarm Clock - App Design Plan

## Table of Contents
1. [Overview & Vision](#overview--vision)
2. [Navigation Architecture](#navigation-architecture)
3. [Color Scheme](#color-scheme)
4. [Typography](#typography)
5. [Screen 1: Main Screen (Home)](#screen-1-main-screen-home)
6. [Screen 2: Alarm/Alert Settings](#screen-2-alarmalert-settings)
7. [Screen 3: Settings](#screen-3-settings)
8. [Screen 4: Weekly/Monthly View](#screen-4-weeklymonthly-view)
9. [Screen 5: Widget](#screen-5-widget)
10. [Alert/Notification UX Flow](#alertnotification-ux-flow)
11. [Component Hierarchy](#component-hierarchy)
12. [Edge Cases](#edge-cases)
13. [Complete Zmanim List](#complete-zmanim-list)
14. [Technical Notes](#technical-notes)

---

## Overview & Vision

A focused, clean Android app that serves one purpose excellently: showing halachic times and alerting users before they arrive. The design philosophy follows Material Design 3 guidelines with full RTL (right-to-left) Hebrew support as the primary layout direction.

**Core identity:** A clock app, not a calendar app. The primary interaction is glancing at the screen to see "what is the next zman and when does it arrive."

**Anti-goals:** No stopwatch, no weather, no premium features, no ads, no social features, no siddur text.

---

## Navigation Architecture

```
Bottom Navigation Bar (3 tabs)
|
+-- [1] Home (default)          --> Today's zmanim list + current time
|       |
|       +-- Tap any zman row    --> Quick-add alert bottom sheet
|
+-- [2] Calendar                --> Weekly/Monthly table view
|       |
|       +-- Tap any day         --> That day's full zmanim list
|
+-- [3] Alerts                  --> List of all active alerts
        |
        +-- Tap alert           --> Edit alert bottom sheet
        +-- FAB (+)             --> New alert creation

Top App Bar (contextual)
|
+-- Settings gear icon          --> Full-screen Settings activity
+-- Location name (tappable)    --> Quick location change bottom sheet
```

**Navigation rules:**
- Bottom nav uses 3 items (Material 3 recommended range: 3-5)
- Settings is a full-screen activity, not a tab (it is rarely accessed)
- Bottom sheets for quick actions; full screens for complex flows
- Back button always returns to Home tab

---

## Color Scheme

### Design Rationale
The color palette draws from traditional Jewish aesthetics -- deep blues (techelet), gold/amber (reminiscent of Jerusalem stone and Torah ornaments), and clean whites. The scheme must provide excellent contrast for outdoor readability (users checking zmanim while walking to shul).

### Light Theme

| Role | Color | Hex | Usage |
|------|-------|-----|-------|
| Primary | Deep Techelet Blue | `#1A5276` | App bar, active states, primary buttons |
| On Primary | White | `#FFFFFF` | Text/icons on primary color |
| Primary Container | Light Blue | `#D4E6F1` | Selected zman row, active alert badges |
| On Primary Container | Dark Blue | `#0E2F44` | Text on primary container |
| Secondary | Jerusalem Gold | `#B7950B` | Accent highlights, Shabbat indicator |
| On Secondary | White | `#FFFFFF` | Text on secondary |
| Secondary Container | Light Gold | `#FEF9E7` | Shabbat/Holiday row background |
| Tertiary | Warm Red | `#C0392B` | Urgent alerts, sof zman warnings |
| On Tertiary | White | `#FFFFFF` | Text on tertiary |
| Surface | Off-White | `#FAFAFA` | Main background |
| Surface Variant | Light Gray | `#F0F0F0` | Card backgrounds, dividers |
| On Surface | Near Black | `#1C1C1E` | Primary text |
| On Surface Variant | Dark Gray | `#5D6D7E` | Secondary text, time values |
| Outline | Medium Gray | `#BDC3C7` | Borders, dividers |
| Error | Red | `#E74C3C` | Error states |

### Dark Theme

| Role | Color | Hex | Usage |
|------|-------|-----|-------|
| Primary | Light Techelet | `#5DADE2` | Active states, buttons |
| On Primary | Very Dark Blue | `#0A1929` | Text on primary |
| Primary Container | Muted Blue | `#1A3A5C` | Selected row |
| Surface | Dark Gray | `#121212` | Main background |
| Surface Variant | Charcoal | `#1E1E1E` | Cards |
| On Surface | Off-White | `#E8E8E8` | Primary text |
| On Surface Variant | Light Gray | `#A0A0A0` | Secondary text |
| Secondary | Muted Gold | `#D4AC0D` | Accent |

### Semantic Colors (both themes)

| Meaning | Light | Dark |
|---------|-------|------|
| Day period (morning) | `#F9E79F` (warm yellow tint) | `#3E3A1F` |
| Day period (afternoon) | `#FADBD8` (warm pink tint) | `#3A2020` |
| Night period | `#D5D8DC` (cool gray tint) | `#1A1A2E` |
| Shabbat/Yom Tov active | `#FEF9E7` (golden glow) | `#2C2A1A` |
| Upcoming zman (next) | `#EBF5FB` (highlight blue) | `#1A2A3A` |
| Passed zman | 60% opacity | 40% opacity |

---

## Typography

### Font Selection

| Priority | Font | Fallback | Usage |
|----------|------|----------|-------|
| Hebrew Primary | **Heebo** (Google Fonts) | Rubik, system Hebrew | Body text, zman names |
| Hebrew Display | **Frank Ruhl Libre** | David Libre, Heebo | Hebrew date, headers |
| Numbers/Time | **Rubik** | Heebo, system sans | Time values (05:32, etc.) |
| English | **Roboto** | system sans-serif | English UI option |

### Type Scale (Material 3)

| Style | Size | Weight | Line Height | Usage |
|-------|------|--------|-------------|-------|
| Display Large | 40sp | 700 | 48sp | Current time on home |
| Display Medium | 34sp | 600 | 40sp | Hebrew date |
| Headline Large | 28sp | 600 | 34sp | Section headers |
| Headline Medium | 24sp | 500 | 30sp | Screen titles |
| Title Large | 20sp | 500 | 26sp | Zman name in row |
| Title Medium | 16sp | 500 | 22sp | Alert title |
| Body Large | 16sp | 400 | 24sp | Description text |
| Body Medium | 14sp | 400 | 20sp | Secondary info |
| Body Small | 12sp | 400 | 16sp | Caption, zman opinion label |
| Label Large | 14sp | 500 | 20sp | Button text |
| Label Small | 11sp | 500 | 16sp | Chip labels, badges |

### Number Display
- All time values displayed in **tabular (monospaced) numerals** to ensure alignment in lists
- Use Rubik's tabular figure feature or a monospace fallback for time columns
- Time format: 24-hour default for Hebrew UI, option for 12-hour (AM/PM) in English UI

---

## Screen 1: Main Screen (Home)

### Layout Overview

The home screen is a **scrollable vertical list** with a sticky header. This was chosen over a circular/timeline view because:
- Users need to scan 15-30 zmanim quickly
- List format is familiar and scannable
- A timeline/arc would waste space and reduce readability
- RTL list layout is straightforward

### Wireframe Description

```
+--------------------------------------------------+
| [Top App Bar - Collapsible]                       |
| [Settings gear]   [App Name: "zmanim"]   [Share] |
+--------------------------------------------------+
| [Location Bar - tappable]                         |
| pin icon  "Yerushalayim, Israel"        [change]  |
+--------------------------------------------------+
|                                                    |
| [Current Time Block]                               |
|        1 8 : 4 2                                   |
|   (large, centered, live-updating)                 |
|                                                    |
|   Yom Revi'i  |  15 Nisan 5786                     |
|   [Shabbat/Holiday chip if applicable]             |
|                                                    |
|   [Day Progress Bar]                               |
|   sunrise ====|=========== sunset                  |
|   "sha'ah zmanit: 57 min"                          |
|                                                    |
+--------------------------------------------------+
|                                                    |
| [Zmanim List - scrollable]                         |
|                                                    |
| --- MORNING (subheader with sun-up icon) ---       |
|                                                    |
| +----------------------------------------------+  |
| | Alot HaShachar              04:52    [bell]  |  |
| +----------------------------------------------+  |
| | Misheyakir                  05:12    [bell]  |  |
| +----------------------------------------------+  |
| | HaNetz (Sunrise)            05:38    [bell]  |  |
| +----------------------------------------------+  |
| | >> Sof Zman K"Sh (MA)       08:42    [bell]  |  |  <-- NEXT ZMAN
| |    in 23 min                                 |  |      highlighted
| +----------------------------------------------+  |
| | Sof Zman K"Sh (GRA)         09:18    [bell]  |  |
| +----------------------------------------------+  |
| | Sof Zman Tefillah (MA)      09:48             |  |
| +----------------------------------------------+  |
| | Sof Zman Tefillah (GRA)     10:18             |  |
| +----------------------------------------------+  |
|                                                    |
| --- MIDDAY (subheader with sun icon) ---           |
|                                                    |
| | Chatzot HaYom               12:30             |  |
| | Mincha Gedolah               12:54             |  |
| | Mincha Ketanah               15:42             |  |
| | Plag HaMincha                16:54             |  |
|                                                    |
| --- EVENING (subheader with moon icon) ---         |
|                                                    |
| | Shekia (Sunset)              19:22    [bell]  |  |
| | Tzeit HaKochavim             19:52    [bell]  |  |
| | Tzeit R"T                    20:34    [bell]  |  |
| | Chatzot HaLaila              00:30             |  |
|                                                    |
+--------------------------------------------------+
| [Bottom Navigation Bar]                            |
|   [Home*]      [Calendar]      [Alerts]            |
+--------------------------------------------------+
```

### Component Details

#### Top App Bar
- Material 3 `MediumTopAppBar` with scroll behavior: collapses to small on scroll
- Title: App name in Hebrew
- Leading icon: Settings gear (navigates to Settings screen)
- Trailing icon: Share (shares today's zmanim as text)

#### Location Bar
- Horizontal card beneath app bar
- Shows pin icon + city name in Hebrew
- Tapping opens a bottom sheet for location selection
- If GPS active, shows small GPS icon badge

#### Current Time Block
- Time displayed in Display Large (40sp), bold, centered
- Updates every second (colon blinks every second)
- Hebrew date below in Display Medium
- Secular date in Body Small beneath Hebrew date
- If Shabbat or Yom Tov: a gold chip/badge shows "Shabbat" or the holiday name
- If Omer counting period: small label "Day X of the Omer" below date

#### Day Progress Bar
- Thin horizontal bar showing position between sunrise and sunset
- Left edge = sunrise, right edge = sunset (mirrored for RTL: right = sunrise)
- Current position marked with a dot/indicator
- Below the bar: "Sha'ah Zmanit: XX min" (halachic hour length)
- Background color subtly shifts: warm tones for day, cool for night

#### Zmanim List
- Grouped by period: Morning / Midday / Evening / Night
- Each group has a subheader with an icon and label
- Each zman row contains:
  - **Zman name** (Title Large, right-aligned in RTL) - e.g., "סוף זמן קריאת שמע (מג"א)"
  - **Time value** (Title Large, tabular numerals, left-aligned in RTL) - e.g., "08:42"
  - **Bell icon** (if alert is set for this zman) - filled bell = active alert
  - **Opinion label** (Body Small, muted) - shown inline or below if needed (e.g., "לפי הגר"א")

#### Next Upcoming Zman
- The row for the next upcoming zman is **visually highlighted**:
  - Background: Primary Container color
  - Left border (right border in RTL): 4dp thick Primary color bar
  - Additional line below zman name: "**in XX min**" or "**in X hr XX min**" in Body Medium, Primary color
  - Row slightly taller than others (additional 8dp padding)

#### Passed Zmanim
- Zmanim that have already passed today:
  - Text opacity reduced to 60%
  - Time value gets a subtle strikethrough or dimming
  - No bell icon shown (cannot set alert for passed time)
  - User can optionally hide passed zmanim (toggle in settings)

#### Interaction: Tap on Zman Row
- Opens a **bottom sheet** for quick alert setup (see Screen 2 details)
- Long-press: shows a tooltip with the full calculation method name

---

## Screen 2: Alarm/Alert Settings

### Two Entry Points

**A) Quick Add (Bottom Sheet)** - triggered by tapping a zman row on Home screen
**B) Full Alert Editor** - triggered from Alerts tab or editing existing alert

### A) Quick Add Bottom Sheet

```
+--------------------------------------------------+
|                  [drag handle]                     |
|                                                    |
|  Set Alert for:                                    |
|  "Sof Zman Kriat Shema (Magen Avraham)"           |
|  Today: 08:42                                      |
|                                                    |
|  [Offset Selector]                                 |
|  (-)  [  10  ]  (+)    minutes before              |
|                                                    |
|  Alert at: 08:32                                   |
|                                                    |
|  Alert type:                                       |
|  [Notification]  [Sound]  [Vibration]              |
|   (segmented button - multi-select)                |
|                                                    |
|  [x] Repeat daily                                  |
|                                                    |
|  [Cancel]              [Save Alert]                |
|                                                    |
+--------------------------------------------------+
```

#### Component Details

**Offset Selector:**
- Numeric stepper: minus button, number field, plus button
- Default value: 10 minutes
- Range: 0-120 minutes
- Step: 1 minute (tap), 5 minutes (long-press/hold)
- Toggle: "before" / "after" (segmented button below)
- Calculated alert time shown in real-time below: "Alert at: HH:MM"

**Alert Type (Segmented Button):**
- Three segments, multi-selectable:
  - Notification (default ON) - silent push notification
  - Sound (default OFF) - alarm sound plays
  - Vibration (default OFF) - phone vibrates
- At least one must be selected
- If "Sound" selected, a secondary row appears for sound selection

**Repeat Toggle:**
- Checkbox: "Repeat daily" (default ON for most zmanim)
- If ON: alert fires every day at the calculated offset from that zman
- Note: the actual time shifts daily as zmanim change

**Buttons:**
- Cancel (text button, left in RTL)
- Save Alert (filled button, right in RTL, Primary color)

### B) Full Alert Editor Screen

```
+--------------------------------------------------+
| [Back arrow]    Edit Alert    [Delete - trash]     |
+--------------------------------------------------+
|                                                    |
| Zman:                                              |
| [Dropdown: Select Zman]                            |
| "Sof Zman Kriat Shema"                             |
|                                                    |
| Opinion:                                           |
| [Dropdown: Magen Avraham / GRA / Both]             |
|                                                    |
| Tomorrow's time: 08:40                             |
|                                                    |
+--------------------------------------------------+
|                                                    |
| Offset:                                            |
| (-)  [  10  ]  (+)    [before / after]             |
|                                                    |
| Alert fires at: 08:30                              |
|                                                    |
+--------------------------------------------------+
|                                                    |
| Alert Type:                                        |
| [Notification]  [Sound]  [Vibration]               |
|                                                    |
| Sound:  [Default Alarm]  [>]                       |
| (only shown if Sound is selected)                  |
|                                                    |
+--------------------------------------------------+
|                                                    |
| Schedule:                                          |
|                                                    |
| (*) Every day                                      |
| ( ) Weekdays only (Sun-Thu)                        |
| ( ) Specific days:                                 |
|     [S] [M] [T] [W] [T] [F] [Sh]                  |
|     (day chips - toggleable)                       |
| ( ) One time only                                  |
|                                                    |
+--------------------------------------------------+
|                                                    |
| Shabbat & Yom Tov:                                 |
|                                                    |
| [x] Skip on Shabbat                                |
| [x] Skip on Yom Tov                                |
| [ ] Different alert on Erev Shabbat                |
|     (if checked, expands to show separate offset)  |
|                                                    |
+--------------------------------------------------+
|                                                    |
| Snooze:                                            |
| [x] Allow snooze                                   |
| Snooze interval: [  5  ] minutes                   |
| Max snoozes: [  3  ]                                |
|                                                    |
+--------------------------------------------------+
|                                                    |
| Label (optional):                                  |
| [  "Time to daven Shacharit!"  ]                   |
|                                                    |
+--------------------------------------------------+
|                                                    |
|              [Save Changes]                        |
|                                                    |
+--------------------------------------------------+
```

### Alerts Tab (List View)

```
+--------------------------------------------------+
| [Top App Bar]   My Alerts                          |
+--------------------------------------------------+
|                                                    |
| [Active Alerts]                                    |
|                                                    |
| +----------------------------------------------+  |
| | bell  Sof Zman K"Sh (MA)     10 min before  |  |
| |       Every day    08:32 tomorrow    [toggle] |  |
| +----------------------------------------------+  |
| | bell  Shekia                 18 min before   |  |
| |       Weekdays     19:04 tomorrow    [toggle] |  |
| +----------------------------------------------+  |
| | bell  Hadlakat Nerot         20 min before   |  |
| |       Erev Shabbat 19:02 Fri         [toggle] |  |
| +----------------------------------------------+  |
|                                                    |
| [Inactive Alerts]                                  |
| +----------------------------------------------+  |
| | bell  Chatzot (dimmed)       at time         |  |
| |       Paused                         [toggle] |  |
| +----------------------------------------------+  |
|                                                    |
|                                          [FAB +]   |
+--------------------------------------------------+
```

**Alert List Row:**
- Bell icon (filled = active, outlined = paused)
- Zman name (Title Medium)
- Offset description (Body Medium)
- Schedule type label (Body Small, muted)
- Next fire time (Body Small, muted)
- Toggle switch (on/off, right edge in RTL layout = left edge visually)
- Tap row: opens Full Alert Editor
- Swipe to delete (with undo snackbar)

---

## Screen 3: Settings

### Layout Structure

Settings uses a **single scrollable screen** with grouped sections, following Material 3 preference patterns.

```
+--------------------------------------------------+
| [Back arrow]           Settings                    |
+--------------------------------------------------+
|                                                    |
| == Location ==                                     |
|                                                    |
| Location method                                    |
| [GPS Auto-detect]  /  [Manual Selection]           |
| (segmented button)                                 |
|                                                    |
| Current: Yerushalayim, Israel                      |
| Coordinates: 31.7683 N, 35.2137 E                  |
| Elevation: 754m                                    |
|                                                    |
| [Change City]  (opens city picker)                 |
| [Enter Coordinates]  (opens manual entry)          |
|                                                    |
+--------------------------------------------------+
|                                                    |
| == Halachic Opinions ==                            |
|                                                    |
| Nusach / Custom:                                   |
| ( ) Ashkenazi                                      |
| (*) Sephardi (R' Ovadia Yosef)                     |
| ( ) Yemenite (Baladi)                               |
| ( ) Custom                                          |
|                                                    |
| Primary Shita:                                     |
| [Dropdown: GRA / Magen Avraham / Both]             |
|                                                    |
| Calculation method:                                |
| Degrees below horizon for Alot: [  16.1  ]         |
| Degrees below horizon for Tzeit: [  8.5  ]         |
| Rabbeinu Tam method: [72 min / degrees]             |
|                                                    |
| (Advanced users only - collapsed by default         |
|  under "Advanced calculation settings" expander)    |
|                                                    |
+--------------------------------------------------+
|                                                    |
| == Display ==                                      |
|                                                    |
| Zmanim to show:                                    |
| [Manage Zmanim List]  (opens checklist screen)     |
| Currently showing: 18 of 32                        |
|                                                    |
| Theme:                                             |
| [Light]  [Dark]  [System]                           |
| (segmented button)                                 |
|                                                    |
| Time format:                                       |
| [24-hour]  [12-hour AM/PM]                          |
|                                                    |
| Language:                                          |
| [Hebrew]  [English]                                 |
|                                                    |
| Hide passed zmanim: [toggle]                       |
|                                                    |
+--------------------------------------------------+
|                                                    |
| == Notifications ==                                |
|                                                    |
| Default alert sound:                               |
| [Gentle Chime]  [>]                                |
|                                                    |
| Default vibration pattern:                         |
| [Short pulse]  [>]                                 |
|                                                    |
| Do Not Disturb override: [toggle]                  |
| (Allow alarms during DND)                          |
|                                                    |
| Shabbat mode:                                      |
| [x] Silence all alerts during Shabbat              |
| [ ] Silence all alerts during Yom Tov              |
| Shabbat auto-silence: 20 min before candle lighting|
| Resume alerts: at Tzeit HaKochavim Motzei Shabbat  |
|                                                    |
+--------------------------------------------------+
|                                                    |
| == Data ==                                         |
|                                                    |
| Offline data:                                      |
| Downloaded: Jan 2026 - Dec 2026                    |
| [Download Next Year]                               |
| [Clear Cache]                                      |
|                                                    |
| Last GPS update: today 14:22                       |
|                                                    |
+--------------------------------------------------+
|                                                    |
| == About ==                                        |
|                                                    |
| Version: 1.0.0                                     |
| Halachic advisor: [Rabbi Name]                     |
| Calculation library: KosherJava                    |
| [Licenses]                                         |
| [Contact / Feedback]                               |
|                                                    |
+--------------------------------------------------+
```

### Zmanim Checklist Screen (sub-screen)

```
+--------------------------------------------------+
| [Back]     Select Zmanim to Display                |
+--------------------------------------------------+
| [Select All]                    [Reset to Default] |
+--------------------------------------------------+
|                                                    |
| == Morning Times ==                                |
| [x] Alot HaShachar (Dawn)                         |
| [x] Misheyakir (Earliest Tallit/Tefillin)          |
| [x] HaNetz HaChama (Sunrise)                       |
| [x] Sof Zman Kriat Shema (MA)                      |
| [x] Sof Zman Kriat Shema (GRA)                     |
| [x] Sof Zman Tefillah (MA)                         |
| [x] Sof Zman Tefillah (GRA)                        |
| [ ] Sof Zman Tefillah (R' Ovadia)                  |
| ...                                                |
|                                                    |
| == Midday Times ==                                 |
| [x] Chatzot HaYom (Midday)                        |
| [x] Mincha Gedolah                                 |
| [x] Mincha Ketanah                                 |
| [x] Plag HaMincha                                  |
| ...                                                |
|                                                    |
| == Evening Times ==                                |
| [x] Shekia (Sunset)                                |
| [x] Tzeit HaKochavim                               |
| [x] Tzeit HaKochavim (R' Tam)                      |
| [x] Chatzot HaLaila (Midnight)                     |
| ...                                                |
|                                                    |
| == Shabbat & Holiday ==                            |
| [x] Hadlakat Nerot (Candle Lighting)               |
| [x] Motzei Shabbat                                 |
| [ ] Motzei Shabbat (R' Tam)                        |
| ...                                                |
|                                                    |
+--------------------------------------------------+
```

### City Picker (Bottom Sheet or Full Screen)

```
+--------------------------------------------------+
| [Search field: "Search city..."]                   |
+--------------------------------------------------+
| == Recent ==                                       |
| Yerushalayim, Israel                               |
| Tel Aviv, Israel                                   |
+--------------------------------------------------+
| == Popular ==                                      |
| Yerushalayim | Bnei Brak | Tel Aviv               |
| New York     | Los Angeles | London                |
| Paris        | Montreal   | Melbourne              |
+--------------------------------------------------+
| == All Countries ==                                |
| Israel (expandable)                                |
|   > Yerushalayim                                   |
|   > Tel Aviv                                       |
|   > Haifa                                          |
|   > ...                                            |
| United States (expandable)                         |
|   > New York                                       |
|   > Los Angeles                                    |
|   > ...                                            |
+--------------------------------------------------+
```

---

## Screen 4: Weekly/Monthly View

### Weekly View (Default)

```
+--------------------------------------------------+
| [Back arrow]    Weekly View    [<  >] week nav     |
+--------------------------------------------------+
| [Week] [Month]  (segmented button)                 |
+--------------------------------------------------+
| 10-16 Nisan 5786  |  Apr 6-12, 2026               |
+--------------------------------------------------+
|                                                    |
| Horizontally scrollable table:                     |
|                                                    |
| Zman        | Sun  | Mon  | Tue  | Wed  | ...     |
|             | 10   | 11   | 12   | 13   |          |
|-------------|------|------|------|------|          |
| Alot        |04:52 |04:51 |04:50 |04:49 |          |
| HaNetz      |05:38 |05:37 |05:36 |05:35 |          |
| Sof K"Sh MA |08:42 |08:42 |08:41 |08:41 |          |
| Sof K"Sh GRA|09:18 |09:18 |09:17 |09:17 |          |
| Chatzot     |12:30 |12:30 |12:30 |12:30 |          |
| Mincha G.   |12:54 |12:54 |12:54 |12:53 |          |
| Plag        |16:54 |16:55 |16:56 |16:57 |          |
| Shekia      |19:22 |19:23 |19:24 |19:25 |          |
| Tzeit       |19:52 |19:53 |19:54 |19:55 |          |
| Tzeit R"T   |20:34 |20:35 |20:36 |20:37 |          |
|                                                    |
| *Shabbat column highlighted in gold*               |
| *Today's column has blue header highlight*         |
|                                                    |
+--------------------------------------------------+
```

### Monthly View

```
+--------------------------------------------------+
| [< Nisan 5786 >]                                   |
+--------------------------------------------------+
| Compact calendar grid showing:                     |
| - Each day cell shows Hebrew date number           |
| - Shabbat cells highlighted gold                   |
| - Yom Tov cells highlighted gold with label        |
| - Tap any day: bottom sheet slides up with         |
|   that day's full zmanim list                      |
| - Today circled in Primary color                   |
|                                                    |
| Below calendar: key times for selected day         |
|                                                    |
| Selected: Wednesday, 15 Nisan                      |
| Sunrise: 05:35  |  Sunset: 19:25                   |
| Candle Lighting: 19:05                             |
+--------------------------------------------------+
```

### Design Notes
- Table cells use **tabular numerals** for perfect column alignment
- The first column (zman names) is frozen/sticky during horizontal scroll
- Today's column has a subtle blue background tint
- Shabbat/Yom Tov columns have a gold background tint
- Long-press on any cell: shows the full zman name and calculation method
- Font size in table: Body Small (12sp) to fit data compactly

---

## Screen 5: Widget

### Widget Variants

Three widget sizes are supported:

#### A) Small Widget (2x1 cells)

```
+------------------------+
| Next: Sof K"Sh (MA)   |
| 08:42    in 23 min     |
+------------------------+
```
- Shows only the next upcoming zman
- Background adapts to light/dark theme
- Tapping opens the app

#### B) Medium Widget (4x2 cells)

```
+--------------------------------------------------+
|  15 Nisan 5786     Yerushalayim          18:42    |
|  ------------------------------------------------ |
|  > Sof K"Sh (MA)      08:42    in 23 min          |
|  Sof K"Sh (GRA)       09:18                       |
|  Shekia               19:22                       |
|  Tzeit                 19:52                       |
+--------------------------------------------------+
```
- Hebrew date, location, current time in header
- Next zman highlighted with ">" marker and countdown
- Shows 3-4 upcoming key zmanim
- Tapping any row opens the app to that zman

#### C) Large Widget (4x4 cells)

```
+--------------------------------------------------+
|  15 Nisan 5786     Yerushalayim          18:42    |
|  Wednesday, April 8                                |
|  ------------------------------------------------ |
|  Alot HaShachar        04:52    (passed)           |
|  Misheyakir            05:12    (passed)           |
|  HaNetz                05:38    (passed)           |
|  > Sof K"Sh (MA)       08:42    in 23 min          |
|  Sof K"Sh (GRA)        09:18                       |
|  Chatzot               12:30                       |
|  Shekia                19:22                       |
|  Tzeit                 19:52                       |
+--------------------------------------------------+
```
- Full day's key zmanim visible
- Passed zmanim shown dimmed
- Same highlight treatment for next zman

### Widget Design Details
- Background: Surface color with rounded corners (28dp radius per M3)
- Material 3 widget guidelines: use `@android:style/Widget.Material3`
- Update frequency: every minute
- Tap target: entire widget opens app; individual rows open to that zman
- Dynamic colors: support Material You / dynamic color theming on Android 12+

---

## Alert/Notification UX Flow

### Flow 1: Setting an Alert

```
User taps zman row on Home
        |
        v
Bottom sheet appears with Quick Add
        |
        v
User sets offset (default: 10 min before)
        |
        v
User selects alert type (notification / sound / vibration)
        |
        v
User taps "Save Alert"
        |
        v
Confirmation snackbar: "Alert set: K"Sh (MA) - 10 min before"
Bell icon appears on that zman row
        |
        v
Alert scheduled in Android AlarmManager (exact alarm)
```

### Flow 2: Alert Fires

```
Scheduled time arrives
        |
        +-- If "Notification" selected:
        |     Android notification appears
        |     Title: "Sof Zman Kriat Shema (MA)"
        |     Body: "In 10 minutes (08:42)"
        |     Actions: [Dismiss] [Snooze 5 min]
        |     Channel: "Zmanim Alerts" (user-configurable priority)
        |
        +-- If "Sound" selected:
        |     Full-screen alarm activity launches
        |     (like a standard alarm clock)
        |     Large time display
        |     Zman name prominently shown
        |     [Dismiss] button (large, easy to tap)
        |     [Snooze] button (if enabled)
        |     Auto-dismiss after 5 minutes
        |
        +-- If "Vibration" selected:
              Phone vibrates in pattern
              Notification also appears
```

### Flow 3: Full-Screen Alarm

```
+--------------------------------------------------+
|                                                    |
|                                                    |
|              [Bell animation / icon]               |
|                                                    |
|              08:32                                  |
|                                                    |
|         Sof Zman Kriat Shema                       |
|         (Magen Avraham)                             |
|                                                    |
|         Zman is at 08:42                            |
|         10 minutes remaining                       |
|                                                    |
|                                                    |
|         [====  DISMISS  ====]                      |
|                                                    |
|         [    Snooze 5 min    ]                     |
|                                                    |
|                                                    |
+--------------------------------------------------+
```

- Background: Dark overlay (works in both themes)
- Dismiss button: Large filled button, Primary color, full width
- Snooze button: Outlined button below dismiss
- Alarm sound loops until dismissed or auto-timeout (5 min)
- Wake screen and show over lock screen (if permitted)
- Respect Shabbat mode: if Shabbat mode active, silently skip

### Notification Design

```
+--------------------------------------------------+
| [App icon]  Zmanim                           now  |
|                                                    |
| Sof Zman Kriat Shema (Magen Avraham)               |
| In 10 minutes -- 08:42                             |
|                                                    |
| [Dismiss]                      [Snooze 5 min]      |
+--------------------------------------------------+
```

- Use a dedicated notification channel: "Zmanim Alerts"
- Priority: HIGH for sound alerts, DEFAULT for silent notifications
- Notification group: group multiple zman alerts if they fire close together
- Rich notification with actions (Dismiss, Snooze)
- On Android 12+: use large icon with zman-appropriate icon (sun/moon)

---

## Component Hierarchy

### Design System Components

```
Foundation Layer
|
+-- Colors (MaterialTheme.colorScheme)
|   +-- Light scheme
|   +-- Dark scheme
|   +-- Dynamic colors (Android 12+)
|
+-- Typography (MaterialTheme.typography)
|   +-- Hebrew type scale (Heebo/Frank Ruhl Libre)
|   +-- English type scale (Roboto)
|   +-- Tabular numerals for times
|
+-- Shapes (MaterialTheme.shapes)
|   +-- Small: 8dp rounded
|   +-- Medium: 12dp rounded
|   +-- Large: 16dp rounded
|   +-- Extra Large: 28dp rounded (cards, bottom sheets)

Atomic Components
|
+-- ZmanTimeText          -- Formatted time display (HH:MM)
+-- ZmanNameText          -- Zman name with opinion label
+-- CountdownText         -- "in XX min" live countdown
+-- AlertBellIcon         -- Bell icon (filled/outlined/off)
+-- DayPeriodIcon         -- Sun/moon/sunrise/sunset icons
+-- HebrewDateText        -- Formatted Hebrew date
+-- OffsetStepper         -- +/- control for minute offset
+-- AlertTypeSelector     -- Segmented button (notif/sound/vibrate)

Molecule Components
|
+-- ZmanRow               -- Single zman row in list
|   +-- ZmanNameText
|   +-- ZmanTimeText
|   +-- AlertBellIcon
|   +-- CountdownText (if next zman)
|
+-- ZmanGroupHeader       -- Section header (Morning/Midday/Evening)
|   +-- DayPeriodIcon
|   +-- Label
|
+-- CurrentTimeDisplay    -- Large clock + Hebrew date
|   +-- Time digits
|   +-- HebrewDateText
|   +-- Secular date
|   +-- Holiday chip
|
+-- DayProgressBar        -- Sunrise-to-sunset progress
|   +-- Progress indicator
|   +-- Sha'ah Zmanit label
|
+-- LocationBar           -- City name + GPS indicator
|
+-- AlertCard             -- Alert row in Alerts tab
|   +-- AlertBellIcon
|   +-- ZmanNameText
|   +-- Offset label
|   +-- Schedule label
|   +-- Next fire time
|   +-- Toggle switch
|
+-- QuickAddSheet         -- Bottom sheet for quick alert
|   +-- ZmanNameText
|   +-- OffsetStepper
|   +-- AlertTypeSelector
|   +-- Repeat toggle
|   +-- Save/Cancel buttons

Organism Components
|
+-- ZmanList              -- Full scrollable list of zman rows
|   +-- ZmanGroupHeader (x3-4)
|   +-- ZmanRow (x15-30)
|
+-- AlertList             -- List of all alerts
|   +-- AlertCard (xN)
|
+-- WeeklyTable           -- 7-column zmanim table
|
+-- MonthlyCalendar       -- Calendar grid with zmanim summary

Screen Components
|
+-- HomeScreen
|   +-- TopAppBar
|   +-- LocationBar
|   +-- CurrentTimeDisplay
|   +-- DayProgressBar
|   +-- ZmanList
|
+-- CalendarScreen
|   +-- TopAppBar
|   +-- WeeklyTable / MonthlyCalendar (toggle)
|
+-- AlertsScreen
|   +-- TopAppBar
|   +-- AlertList
|   +-- FAB
|
+-- SettingsScreen
|   +-- Preference groups
|
+-- AlarmScreen (full-screen overlay)
|   +-- Alarm display
|   +-- Dismiss/Snooze buttons
```

---

## Edge Cases

### 1. Shabbat Mode

**Problem:** The phone should not make sounds on Shabbat. Some users want complete silence; others want pre-Shabbat alerts but not during Shabbat.

**Solution:**
- Setting: "Silence alerts during Shabbat" (default ON)
- Auto-silence begins X minutes before candle lighting (configurable, default 20 min)
- Auto-resume at Motzei Shabbat (Tzeit HaKochavim)
- During Shabbat mode:
  - All sound/vibration alerts suppressed
  - Silent notifications still delivered (visible when user checks phone after Shabbat)
  - Widget still updates (display only, no interaction needed)
  - Home screen still shows zmanim (for viewing before Shabbat)
- Visual indicator on Home screen: gold banner "Shabbat Mode Active"
- Same logic applies to Yom Tov (separate toggle)

### 2. No GPS / Location Unavailable

**Problem:** User denies GPS permission or is in an area with no signal.

**Solution:**
- First launch: request GPS permission with clear explanation
- If denied: prompt manual city selection immediately
- If GPS fails after initial setup: use last known location with warning banner
- Warning banner: "Using last known location (Yerushalayim). Tap to update."
- Manual location always available as fallback
- Store coordinates locally so app works fully offline

### 3. First-Time Setup (Onboarding)

**Flow:**
```
Welcome screen
    "Halachic Alarm Clock"
    [Get Started]
        |
        v
Step 1: Location
    "Where are you located?"
    [Use GPS]  or  [Choose City]
        |
        v
Step 2: Custom/Nusach
    "Select your minhag"
    [Ashkenazi]  [Sephardi]  [Yemenite]  [Custom]
        |
        v
Step 3: Primary Opinion
    "Which shita for zmanim?"
    [GRA]  [Magen Avraham]  [Show Both]
    (brief explanation of each)
        |
        v
Step 4: Quick Alert Setup
    "Set your first alert?"
    Show top 5 most common zmanim with suggested alerts:
    [x] Sof Zman K"Sh - 10 min before
    [x] Shekia - 18 min before
    [x] Hadlakat Nerot - 20 min before (Fridays)
    [ ] Alot HaShachar
    [ ] Chatzot
        |
        v
Done! -> Home screen
```

- Onboarding is 4 steps max
- Each step fits on one screen (no scrolling)
- User can skip to defaults at any point
- Settings are changeable later
- Onboarding only appears on first launch

### 4. Extreme Latitudes

**Problem:** In northern locations (e.g., Scandinavia, northern Canada), some zmanim may not exist (no true sunset in summer, no true sunrise in winter).

**Solution:**
- Show "N/A" for zmanim that cannot be calculated
- Display informational banner: "Some zmanim cannot be calculated at this latitude"
- Provide link to halachic guidance for extreme locations
- Do not allow setting alerts for N/A zmanim
- For Shabbat times in extreme latitudes: note that a competent halachic authority should be consulted

### 5. Crossing Date Boundary

**Problem:** Jewish day begins at sunset, but the calendar shows from midnight to midnight. Chatzot HaLaila (halachic midnight) belongs to the next Jewish day.

**Solution:**
- Display all zmanim from Alot of today through Alot of tomorrow
- Group clearly: Morning > Midday > Evening > Night
- After sunset, show next day's Hebrew date but keep today's zmanim visible
- Add subtle divider at sunset with label: "Night of [next Hebrew date]"
- Chatzot HaLaila labeled clearly as belonging to the night period

### 6. Timezone Changes / Travel

**Problem:** User travels to a different timezone; zmanim must update.

**Solution:**
- If GPS is active: automatic update when location changes significantly (>10 km)
- Notification: "Your location has changed. Zmanim updated for [new city]."
- All alerts automatically recalculate for new location
- If manual location: prompt user to update when timezone change detected
- Show timezone in settings for clarity

### 7. Battery Optimization

**Problem:** Android aggressively kills background services; alerts might not fire.

**Solution:**
- On first alert setup, check if app is battery-optimized
- If yes: show dialog explaining the need to disable battery optimization for reliable alerts
- Provide direct intent to battery optimization settings
- Use `AlarmManager.setExactAndAllowWhileIdle()` for critical alerts
- Use foreground service with persistent notification for sound alarms
- Test and document behavior on major OEMs (Samsung, Xiaomi, Huawei) which have aggressive app killing

### 8. Multiple Opinions Conflicting

**Problem:** User wants alerts for the same zman according to different opinions (e.g., Sof Zman K"Sh according to both MA and GRA).

**Solution:**
- Allow multiple alerts for the same zman with different opinions
- Each alert is independent with its own offset and type
- In the alert list, clearly label which opinion each alert follows
- On Home screen, show both times when "Show Both" is selected in settings

### 9. App Opened on Shabbat (by non-observant family member or accident)

**Solution:**
- App functions normally (it is a display app; no prohibition on viewing)
- All sound alerts already silenced by Shabbat mode
- No special lockout (this would be a UX problem, not a halachic one)
- The app does not encourage or discourage Shabbat phone use

### 10. Offline Mode

**Problem:** User has no internet connection.

**Solution:**
- Core zmanim calculation is done locally (KosherJava library) -- needs only coordinates + date
- No internet required for basic functionality
- City database bundled with app (offline)
- Hebrew calendar data calculated locally
- Only GPS lookup may need network (fallback to last known coordinates)
- Holiday calendar pre-calculated for multiple years ahead

---

## Complete Zmanim List

The app should support all of the following zmanim. Users select which to display from Settings.

### Morning (Boker)
1. Alot HaShachar (Dawn) - 72 min before sunrise / 16.1 degrees
2. Alot HaShachar (90 min)
3. Misheyakir (Earliest Tallit & Tefillin) - 10.2 degrees
4. Misheyakir (11.5 degrees)
5. HaNetz HaChama (Sunrise) - sea level
6. HaNetz HaChama (Sunrise) - elevation adjusted
7. Sof Zman Kriat Shema (Magen Avraham)
8. Sof Zman Kriat Shema (GRA/Baal HaTanya)
9. Sof Zman Kriat Shema (Fixed / R' Ovadia)
10. Sof Zman Tefillah (Magen Avraham)
11. Sof Zman Tefillah (GRA)

### Midday (Chatzot)
12. Chatzot HaYom (Solar Midday)
13. Mincha Gedolah (Earliest Mincha)
14. Mincha Gedolah (30 min fixed after Chatzot)
15. Mincha Ketanah
16. Plag HaMincha (GRA)
17. Plag HaMincha (Magen Avraham)

### Evening (Erev)
18. Shekia (Sunset) - sea level
19. Shekia (Sunset) - elevation adjusted
20. Bein HaShmashot (Twilight start)
21. Tzeit HaKochavim (Nightfall) - 3 small stars - 8.5 degrees
22. Tzeit HaKochavim (Nightfall) - 13.5 min fixed
23. Tzeit HaKochavim (R' Tam) - 72 min
24. Tzeit HaKochavim (R' Tam) - degrees (16.1)

### Night (Laila)
25. Chatzot HaLaila (Halachic Midnight)

### Shabbat & Holidays
26. Hadlakat Nerot (Candle Lighting) - configurable minutes before sunset
27. Motzei Shabbat (End of Shabbat) - standard
28. Motzei Shabbat (R' Tam)
29. Motzei Yom Tov

### Seasonal / Conditional
30. Sof Zman Kiddush Levana (Latest Kiddush Levana)
31. Earliest Kiddush Levana
32. Fast begins (for fast days)
33. Fast ends (for fast days)
34. Sefirat HaOmer reminder (during Omer period)
35. Earliest time for Shabbat (Plag HaMincha on Friday)

---

## Technical Notes

### Recommended Libraries
- **KosherJava** (or its Kotlin port): Industry-standard halachic time calculation library
- **Hebcal**: For Hebrew calendar dates and holiday awareness
- **Jetpack Compose**: For modern declarative UI with native RTL support
- **Material 3 Components**: For consistent design language
- **WorkManager + AlarmManager**: For reliable alert scheduling
- **Room Database**: For storing alert configurations
- **DataStore**: For user preferences
- **Hilt**: For dependency injection

### RTL Implementation Notes
- Set `android:supportsRtl="true"` in manifest
- Use `start`/`end` instead of `left`/`right` in all layouts
- Use `LayoutDirection.Rtl` as default in Compose
- Test with force-RTL developer option enabled
- Bidirectional text handling for mixed Hebrew/English content
- Numbers always display left-to-right even in RTL layout

### Performance Targets
- App cold start: under 1.5 seconds
- Zmanim calculation for one day: under 50ms
- Widget update: under 200ms
- Alert scheduling: exact timing (AlarmManager exact alarms)
- Memory footprint: under 60MB

### Accessibility
- All interactive elements have content descriptions in Hebrew
- Minimum touch target: 48dp x 48dp (Material 3 standard)
- Color contrast: minimum 4.5:1 for body text, 3:1 for large text
- Screen reader support: meaningful reading order for zmanim list
- Dynamic font scaling support (sp units throughout)
- No information conveyed by color alone (always pair with icon or text)

---

## Summary

This design plan provides a complete blueprint for building a professional, focused Halachic Alarm Clock app. The key design decisions are:

1. **List over timeline**: Zmanim displayed as a scannable vertical list grouped by day period, not a circular clock face
2. **Bottom sheets for quick actions**: Tapping a zman row immediately offers alert setup without leaving the context
3. **Three-tab navigation**: Home / Calendar / Alerts -- simple, focused, no feature creep
4. **Shabbat awareness built in**: Automatic silencing, holiday detection, and respectful defaults
5. **Opinion flexibility**: Support for multiple halachic opinions with clear labeling, not hidden in menus
6. **Offline-first**: All core calculations run locally; no server dependency
7. **Material 3 compliance**: Dynamic colors, proper component usage, accessible design
8. **RTL-native**: Hebrew as the primary language with full right-to-left layout support
