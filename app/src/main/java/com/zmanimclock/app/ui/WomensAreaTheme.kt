package com.zmanimclock.app.ui

import androidx.compose.ui.graphics.Color

/**
 * איזור נשי — ורוד לילך. A dedicated small token file, following the Omer
 * feature's own precedent (see OmerArt.kt's OmerGold) rather than growing
 * ExtendedColors: this feature has exactly one consumer set (its own
 * Settings card, its own nav tab, its own screens), the same shape that
 * precedent was chosen for.
 */
val WomensAreaLilac = Color(0xFF9C7AB8)
val WomensAreaLilacContainer = Color(0xFFF3E5F9)
val OnWomensAreaLilacContainer = Color(0xFF3A2145)

/** The veset day's own fill on the calendar — distinguishable from the lilac accent above. */
val WomensAreaVesetMarker = Color(0xFFC77DA6)

/**
 * The separation days' frame. A true red, not a lilac shade: these are the
 * days the whole screen exists to point out, and they must not read as one
 * more pastel marker.
 */
val WomensAreaPrishaRed = Color(0xFFD32F2F)

/**
 * The day-count chip (1, 2, 3 … 30). Solid, with white digits, so it reads the
 * same in light and dark theme, and a blue far from both the lilac clean-day
 * chip and the red frame, so the three never blur together.
 */
val WomensAreaCountBlue = Color(0xFF2962C8)

/** שבעה נקיים — a green frame and a green day chip (1…7), as the owner specified. */
val WomensAreaCleanGreen = Color(0xFF2E7D32)

/** ליל הטבילה — water blue, the מעיין icon's own colour on the calendar. */
val WomensAreaTevilaBlue = Color(0xFF0277BD)

/** יום נוכחי — a thick yellow frame, on the calendar, in the legend and in the date picker. */
val WomensAreaTodayYellow = Color(0xFFF9B800)
