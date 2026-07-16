package com.zmanimclock.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens — style 1D "קריאוּת מרבית" (Claude Design handoff README §3).
 * Navy #123A8B + amber #F5C518, high contrast, elderly-readable.
 * Source of truth: the design README delivered 2026-07-16.
 */

// ===== Light =====
val PrimaryLight = Color(0xFF123A8B)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFDBE6FB)
val OnPrimaryContainerLight = Color(0xFF0A2255)
val SecondaryLight = Color(0xFF4B5C82)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFDCE4F5)
val OnSecondaryContainerLight = Color(0xFF2A3550)
val TertiaryLight = Color(0xFF7A5E00)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFF3C4)
val OnTertiaryContainerLight = Color(0xFF4A3B00)
val BackgroundLight = Color(0xFFF5F7FC)
val OnBackgroundLight = Color(0xFF12203A)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF12203A)
val SurfaceVariantLight = Color(0xFFE7ECF5)
val OnSurfaceVariantLight = Color(0xFF4A5468)
val SurfaceContainerLight = Color(0xFFF0F3F9)
val OutlineLight = Color(0xFFB9C2D4)
val OutlineVariantLight = Color(0xFFE4E9F2)
val ErrorLight = Color(0xFFB3261E)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFF9DEDC)
val OnErrorContainerLight = Color(0xFF410E0B)

// ===== Dark =====
val PrimaryDark = Color(0xFFA9C2F5)
val OnPrimaryDark = Color(0xFF0B2352)
val PrimaryContainerDark = Color(0xFF1C3B7A)
val OnPrimaryContainerDark = Color(0xFFD6E2FF)
val SecondaryDark = Color(0xFFB6C4E4)
val OnSecondaryDark = Color(0xFF212E4A)
val SecondaryContainerDark = Color(0xFF37456A)
val OnSecondaryContainerDark = Color(0xFFD6E1FA)
val TertiaryDark = Color(0xFFF5C518)
val OnTertiaryDark = Color(0xFF3A2E00)
val TertiaryContainerDark = Color(0xFF4A3B00)
val OnTertiaryContainerDark = Color(0xFFFFE08A)
val BackgroundDark = Color(0xFF0B1220)
val OnBackgroundDark = Color(0xFFE6EAF4)
val SurfaceDark = Color(0xFF121B2E)
val OnSurfaceDark = Color(0xFFE6EAF4)
val SurfaceVariantDark = Color(0xFF1C2740)
val OnSurfaceVariantDark = Color(0xFFA6B2CC)
val SurfaceContainerDark = Color(0xFF16203A)
val OutlineDark = Color(0xFF34405C)
val OutlineVariantDark = Color(0xFF232E48)
val ErrorDark = Color(0xFFF2B8B5)
val OnErrorDark = Color(0xFF601410)
val ErrorContainerDark = Color(0xFF8C1D18)
val OnErrorContainerDark = Color(0xFFF9DEDC)

// ===== Extended (custom roles, README §3 "Extended") =====
/** Custom semantic colors beyond the M3 scheme — accessed via [LocalExtendedColors]. */
data class ExtendedColors(
    /** Gold accent: "הנץ הנראה" tag, countdown highlight, active-alert bell. */
    val accentGold: Color,
    val onAccentGold: Color,
    /** Approaching deadline (סוזק"ש, סו"ז תפילה) — "עד". */
    val deadline: Color,
    val deadlineContainer: Color,
    val onDeadlineContainer: Color,
    /** Window opening (מנחה גדולה, פלג) — "מ-". */
    val windowOpen: Color,
    /** Background of the next-zman row. */
    val nextRow: Color,
    /** Background of a row with an active alert. */
    val reminderTint: Color,
    /** Hero header: solid navy (light) / gradient endpoints (dark). */
    val heroTop: Color,
    val heroBottom: Color,
    /** Inner container inside the hero. */
    val heroInner: Color,
    val heroInnerBorder: Color,
    val heroLabel: Color,
)

val ExtendedLight = ExtendedColors(
    accentGold = Color(0xFFF5C518),
    onAccentGold = Color(0xFF12203A),
    deadline = Color(0xFFC24A00),
    deadlineContainer = Color(0xFFFFE1CC),
    onDeadlineContainer = Color(0xFF5A2100),
    windowOpen = Color(0xFF1E7A46),
    nextRow = Color(0xFFE8F0FF),
    reminderTint = Color(0xFFFFF8E1),
    heroTop = Color(0xFF123A8B),
    heroBottom = Color(0xFF123A8B),
    heroInner = Color(0xFF0E2E6E),
    heroInnerBorder = Color(0xFF0E2E6E),
    heroLabel = Color(0xFFB9CCF2),
)

val ExtendedDark = ExtendedColors(
    accentGold = Color(0xFFF5C518),
    onAccentGold = Color(0xFF12203A),
    deadline = Color(0xFFFF9A62),
    deadlineContainer = Color(0xFF4A2100),
    onDeadlineContainer = Color(0xFFFFE1CC),
    windowOpen = Color(0xFF6FD79B),
    nextRow = Color(0xFF16305C),
    reminderTint = Color(0xFF241F0E),
    heroTop = Color(0xFF12224C),
    heroBottom = Color(0xFF0E1B3C),
    heroInner = Color(0xFF0A1530),
    heroInnerBorder = Color(0xFF23345E),
    heroLabel = Color(0xFF8FA6D8),
)
