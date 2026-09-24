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

/** The two calendar-marker colors — distinguishable from each other and from the lilac accent above. */
val WomensAreaVesetMarker = Color(0xFFC77DA6)
val WomensAreaCleanDayMarker = Color(0xFF8E6FB5)

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
