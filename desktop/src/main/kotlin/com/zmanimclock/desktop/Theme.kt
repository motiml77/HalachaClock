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

/**
 * Lifted deliberately off near-black.
 *
 * The first pass ran the surfaces down at #0B1220/#121B2E, which reads as a
 * black window with faint blue in it rather than as the navy the brand is. On
 * a desktop monitor — bigger, brighter and usually further from the eye than a
 * phone — that came out heavy and muddy, and the dividers at #243154 all but
 * vanished. Everything here is raised by roughly one step while the ROLES stay
 * ordered: background darkest, then surface, then surfaceVariant, so elevation
 * still reads. Text sits lighter to hold contrast against the raised ground.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFBFD2FA),
    onPrimary = Color(0xFF0B2352),
    primaryContainer = Color(0xFF2A4C93),
    onPrimaryContainer = Color(0xFFE3ECFF),
    secondary = Color(0xFFC6D2EE),
    tertiary = Gold,
    tertiaryContainer = Color(0xFF5C4A00),
    onTertiaryContainer = Color(0xFFFFE9A6),
    background = Color(0xFF16203A),
    onBackground = Color(0xFFF0F4FC),
    surface = Color(0xFF1E2A48),
    onSurface = Color(0xFFF0F4FC),
    surfaceVariant = Color(0xFF2A3859),
    onSurfaceVariant = Color(0xFFBCC7E0),
    outline = Color(0xFF4A5980),
    outlineVariant = Color(0xFF35446B),
)

/** Extended roles the M3 scheme has no slot for — mirrors the Android app. */
data class DesktopExtras(
    val accentGold: Color,
    /**
     * Text ON the hero band. NOT colorScheme.onPrimary.
     *
     * onPrimary is defined to sit on `primary`, and in a dark scheme that
     * makes it DARK — #0B2352 here. The hero is not painted with `primary`,
     * it is painted with heroTop/heroBottom, so using onPrimary there put a
     * dark navy Hebrew date and a dark navy headline time on a navy band.
     * The date was the app's largest text and it was the hardest to read.
     * The hero owns its own foreground, in both themes.
     */
    val heroText: Color,
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
    heroText = Color.White,
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
    heroText = Color(0xFFF2F6FF),
    onAccentGold = Color(0xFF12203A),
    deadline = Color(0xFFFFAE7E),
    deadlineContainer = Color(0xFF5E2C00),
    // The hero is the one block that must stay DARKER than the list under it,
    // or the page loses its top and the eye has nothing to start from. Raised
    // with the rest, but the gradient and the inner card keep their order.
    heroTop = Color(0xFF23417F),
    heroBottom = Color(0xFF1A3164),
    heroInner = Color(0xFF14274F),
    heroLabel = Color(0xFFAFC4EC),
    nextRow = Color(0xFF2A4E8C),
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
