package com.zmanimclock.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zmanimclock.app.R

/**
 * Typography — style 1D (Claude Design README §4).
 * Digits/display: Rubik (variable). Hebrew text: Heebo (variable).
 * Both bundled in res/font (no network). Clock digits always tabular
 * ([TNUM]) so times align in lists.
 */

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun rubik(weight: FontWeight) = Font(
    R.font.rubik_wght,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun heebo(weight: FontWeight) = Font(
    R.font.heebo_wght,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Rubik = FontFamily(
    rubik(FontWeight.Normal),
    rubik(FontWeight.Medium),
    rubik(FontWeight.SemiBold),
    rubik(FontWeight.Bold),
)

val Heebo = FontFamily(
    heebo(FontWeight.Normal),
    heebo(FontWeight.Medium),
    heebo(FontWeight.SemiBold),
    heebo(FontWeight.Bold),
    heebo(FontWeight.ExtraBold),
)

/** Tabular numerals for every clock digit. */
const val TNUM = "tnum"

val Typography = Typography(
    // === Rubik displays (clock digits) ===
    displayLarge = TextStyle(
        fontFamily = Rubik, fontSize = 60.sp, fontWeight = FontWeight.Bold,
        lineHeight = 64.sp, fontFeatureSettings = TNUM,
    ),
    displayMedium = TextStyle(
        fontFamily = Rubik, fontSize = 45.sp, fontWeight = FontWeight.Bold,
        lineHeight = 52.sp, fontFeatureSettings = TNUM,
    ),
    displaySmall = TextStyle(
        fontFamily = Rubik, fontSize = 36.sp, fontWeight = FontWeight.Bold,
        lineHeight = 44.sp, fontFeatureSettings = TNUM,
    ),
    // === Heebo headlines ===
    headlineLarge = TextStyle(
        fontFamily = Heebo, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Heebo, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Heebo, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Heebo, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Heebo, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp, letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Heebo, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp, letterSpacing = 0.10.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Heebo, fontSize = 18.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Heebo, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Heebo, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Heebo, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Heebo, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Heebo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
)

/** Zman time in the daily list: 26sp Rubik bold tabular (README §4). */
val ZmanListTimeStyle = TextStyle(
    fontFamily = Rubik, fontSize = 26.sp, fontWeight = FontWeight.Bold,
    lineHeight = 32.sp, fontFeatureSettings = TNUM,
)
