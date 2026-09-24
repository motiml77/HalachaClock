package com.zmanimclock.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * איזור נשי — a מעיין: a drop of water falling into two waves. Drawn here as
 * a vector (24×24, the Material grid) because no stock icon means "mikveh"
 * or "spring"; the lock it replaces said "secret", which is the wrong thing
 * to announce on a bottom bar.
 *
 * Single-colour on purpose, black paths only: Icon() tints the whole vector,
 * so it follows the nav bar's selected/unselected colours like every other
 * tab icon.
 */
val WomensAreaSpringIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "WomensAreaSpring",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // The drop.
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2.5f)
            curveTo(10f, 5.5f, 7.5f, 8f, 7.5f, 11f)
            curveTo(7.5f, 13.5f, 9.5f, 15f, 12f, 15f)
            curveTo(14.5f, 15f, 16.5f, 13.5f, 16.5f, 11f)
            curveTo(16.5f, 8f, 14f, 5.5f, 12f, 2.5f)
            close()
        }
        // Two waves under it.
        listOf(18.3f, 21.6f).forEach { y ->
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(2.5f, y)
                quadToRelative(2.375f, -1.8f, 4.75f, 0f)
                reflectiveQuadToRelative(4.75f, 0f)
                reflectiveQuadToRelative(4.75f, 0f)
                reflectiveQuadToRelative(4.75f, 0f)
            }
        }
    }.build()
}
