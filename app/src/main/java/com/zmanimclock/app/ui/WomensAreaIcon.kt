package com.zmanimclock.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.zmanimclock.app.R
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

/**
 * The Women's Area's own logo, in full colour — a circle with its edge cut
 * clean (transparent outside it, no white corners). Shown wherever the area
 * is entered or introduced; never tinted, so it does not follow the nav bar's
 * colours like a vector icon. (The notification's STATUS-BAR icon is a
 * monochrome drawing of it instead — see ic_stat_womens_area.)
 */
@Composable
fun WomensAreaLogo(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.women_area_logo),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}
