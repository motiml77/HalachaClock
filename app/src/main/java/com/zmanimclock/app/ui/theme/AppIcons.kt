package com.zmanimclock.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom single-tone vector icons (Material-style, 24dp grid) for concepts
 * Material Symbols doesn't cover. Tinted by the caller like any Icons.*.
 */
object AppIcons {

    /** Shabbat candle: teardrop flame over a rounded candle body. */
    val Candle: ImageVector by lazy {
        ImageVector.Builder(
            name = "Candle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Flame
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 1.5f)
                curveTo(10.8f, 3.1f, 9.9f, 4.4f, 9.9f, 5.6f)
                curveTo(9.9f, 6.8f, 10.85f, 7.75f, 12f, 7.75f)
                curveTo(13.15f, 7.75f, 14.1f, 6.8f, 14.1f, 5.6f)
                curveTo(14.1f, 4.4f, 13.2f, 3.1f, 12f, 1.5f)
                close()
            }
            // Body
            path(fill = SolidColor(Color.Black)) {
                moveTo(10.2f, 9.25f)
                lineTo(13.8f, 9.25f)
                curveTo(14.35f, 9.25f, 14.8f, 9.7f, 14.8f, 10.25f)
                lineTo(14.8f, 21.5f)
                curveTo(14.8f, 22.05f, 14.35f, 22.5f, 13.8f, 22.5f)
                lineTo(10.2f, 22.5f)
                curveTo(9.65f, 22.5f, 9.2f, 22.05f, 9.2f, 21.5f)
                lineTo(9.2f, 10.25f)
                curveTo(9.2f, 9.7f, 9.65f, 9.25f, 10.2f, 9.25f)
                close()
            }
        }.build()
    }
}
