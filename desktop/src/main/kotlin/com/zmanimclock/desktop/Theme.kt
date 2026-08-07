package com.zmanimclock.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

/**
 * The same "1D" palette as the Android app, so the two read as one product.
 * Values copied deliberately rather than shared: the Android colours live in a
 * resource module that a JVM app cannot depend on.
 */
private val Navy = Color(0xFF123A8B)
private val Gold = Color(0xFFF5C518)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE6FB),
    onPrimaryContainer = Color(0xFF0A2255),
    secondary = Color(0xFF4B5C82),
    tertiary = Color(0xFF7A5E00),
    tertiaryContainer = Color(0xFFFFF3C4),
    onTertiaryContainer = Color(0xFF4A3B00),
    background = Color(0xFFF5F7FC),
    onBackground = Color(0xFF12203A),
    surface = Color.White,
    onSurface = Color(0xFF12203A),
    surfaceVariant = Color(0xFFE7ECF5),
    onSurfaceVariant = Color(0xFF4A5468),
    outline = Color(0xFFB9C2D4),
    outlineVariant = Color(0xFFE4E9F2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C2F5),
    onPrimary = Color(0xFF0B2352),
    primaryContainer = Color(0xFF1C3B7A),
    onPrimaryContainer = Color(0xFFD6E2FF),
    secondary = Color(0xFFB6C4E4),
    tertiary = Gold,
    tertiaryContainer = Color(0xFF4A3B00),
    onTertiaryContainer = Color(0xFFFFE08A),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6EAF4),
    surface = Color(0xFF121B2E),
    onSurface = Color(0xFFE6EAF4),
    surfaceVariant = Color(0xFF1C2740),
    onSurfaceVariant = Color(0xFFA6B2CC),
    outline = Color(0xFF34405C),
    outlineVariant = Color(0xFF243154),
)

/** Extended roles the M3 scheme has no slot for — mirrors the Android app. */
data class DesktopExtras(
    val accentGold: Color,
    val onAccentGold: Color,
    val deadline: Color,
    val deadlineContainer: Color,
    val heroTop: Color,
    val heroBottom: Color,
    val heroInner: Color,
    val heroLabel: Color,
    val nextRow: Color,
)

private val ExtrasLight = DesktopExtras(
    accentGold = Gold,
    onAccentGold = Color(0xFF12203A),
    deadline = Color(0xFFC24A00),
    deadlineContainer = Color(0xFFFFE1CC),
    heroTop = Navy,
    heroBottom = Navy,
    heroInner = Color(0xFF0E2E6E),
    heroLabel = Color(0xFFB9CCF2),
    nextRow = Color(0xFFE8F0FF),
)

private val ExtrasDark = DesktopExtras(
    accentGold = Gold,
    onAccentGold = Color(0xFF12203A),
    deadline = Color(0xFFFF9A62),
    deadlineContainer = Color(0xFF4A2100),
    heroTop = Color(0xFF12224C),
    heroBottom = Color(0xFF0E1B3C),
    heroInner = Color(0xFF0A1530),
    heroLabel = Color(0xFF8FA6D8),
    nextRow = Color(0xFF16305C),
)

val LocalExtras = staticCompositionLocalOf { ExtrasLight }

object Ext {
    val colors: DesktopExtras
        @Composable get() = LocalExtras.current
}

/**
 * Rubik and Heebo are bundled rather than left to the system.
 *
 * Windows does ship Hebrew-capable fonts, so text would render either way —
 * but it would render DIFFERENTLY from the phone, and on a different machine
 * differently again. Bundling makes it deterministic and matches Android.
 */
private fun loadFont(path: String, weight: FontWeight): Font? = runCatching {
    val bytes = object {}.javaClass.getResourceAsStream(path)!!.readBytes()
    androidx.compose.ui.text.platform.Font(identity = path + weight.weight, data = bytes, weight = weight)
}.getOrNull()

private val AppFontFamily: FontFamily by lazy {
    val faces = listOfNotNull(
        loadFont("/font/heebo_wght.ttf", FontWeight.Normal),
        loadFont("/font/heebo_wght.ttf", FontWeight.Medium),
        loadFont("/font/heebo_wght.ttf", FontWeight.Bold),
    )
    if (faces.isEmpty()) FontFamily.Default else FontFamily(faces)
}

/** Times are tabular and large — the number is the point of every row. */
private val NumberFontFamily: FontFamily by lazy {
    val faces = listOfNotNull(
        loadFont("/font/rubik_wght.ttf", FontWeight.Normal),
        loadFont("/font/rubik_wght.ttf", FontWeight.Bold),
    )
    if (faces.isEmpty()) FontFamily.Default else FontFamily(faces)
}

val ZmanNumberFamily: FontFamily get() = NumberFontFamily

private fun typographyWith(family: FontFamily) = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family),
    )
}

/**
 * RTL is FORCED, never inherited.
 *
 * Compose derives layout direction from the system locale, and a large share
 * of Hebrew speakers run Windows in English — on those machines an all-Hebrew
 * app would lay out left-to-right. JetBrains' own RTL tracking issue for
 * desktop (CMP-5362) is also still open, and CMP-2446 means per-string
 * automatic direction detection cannot be relied on. Forcing it at the root is
 * the only reliable path.
 */
@Composable
fun ZmanimDesktopTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalExtras provides if (dark) ExtrasDark else ExtrasLight,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = typographyWith(AppFontFamily),
            content = content,
        )
    }
}
