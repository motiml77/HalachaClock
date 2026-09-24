package com.zmanimclock.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.cos
import kotlin.math.sin

/**
 * The hero card's picture: a sun on the horizon while the sun is up, a
 * crescent moon with stars once it has set.
 *
 * Drawn, not bundled as an image: it stays sharp at any window scaling and
 * needs no asset pipeline. The Android app draws the same picture from a copy
 * of this file (ui/components/SkyGlyph.kt) — keep the two in step.
 *
 * Every measure is a fraction of the canvas width, so the glyph scales as one
 * piece; the caller sets the size.
 */
@Composable
fun SkyGlyph(sunUp: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = if (sunUp) "יום" else "לילה" }) {
        if (sunUp) drawSun() else drawMoonAndStars()
    }
}

/**
 * The sun's gold. Also the hero's zman-name colour, so the name under the
 * time reads as belonging to the picture beside it.
 */
val SkyGold = Color(0xFFF6B73C)

private val SunLight = Color(0xFFFFD75E)
private val SunDeep = Color(0xFFF4A62A)
private val SunLine = SkyGold
private val MoonLight = Color(0xFFFFF1C4)
private val MoonDeep = Color(0xFFF2C75C)
private val StarColor = Color(0xFFFFE9A6)

/** Half a sun on the horizon, rays fanned above it, its reflection below. */
private fun DrawScope.drawSun() {
    val w = size.width
    val cx = w * 0.5f
    val horizon = size.height * 0.60f
    val r = w * 0.20f
    val stroke = w * 0.062f

    // A soft warmth behind the disc, so it reads as light and not a sticker.
    drawCircle(
        Brush.radialGradient(
            listOf(SunDeep.copy(alpha = 0.30f), Color.Transparent),
            center = Offset(cx, horizon),
            radius = w * 0.46f,
        ),
        radius = w * 0.46f,
        center = Offset(cx, horizon),
    )

    // Rays: seven, fanned over the top half and kept clear of the horizon.
    for (deg in floatArrayOf(15f, 40f, 65f, 90f, 115f, 140f, 165f)) {
        val a = Math.toRadians(deg.toDouble())
        val dx = cos(a).toFloat()
        val dy = -sin(a).toFloat()
        drawLine(
            SunLine,
            start = Offset(cx + dx * w * 0.29f, horizon + dy * w * 0.29f),
            end = Offset(cx + dx * w * 0.395f, horizon + dy * w * 0.395f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }

    // The disc — only its upper half; the horizon line covers the seam.
    drawArc(
        Brush.verticalGradient(listOf(SunLight, SunDeep), startY = horizon - r, endY = horizon),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(cx - r, horizon - r),
        size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
    )

    // Horizon, then two shortening strokes of reflection on the water.
    fun line(y: Float, half: Float) = drawLine(
        SunLine,
        start = Offset(cx - w * half, y),
        end = Offset(cx + w * half, y),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    line(horizon, 0.42f)
    line(horizon + w * 0.13f, 0.22f)
    line(horizon + w * 0.25f, 0.11f)
}

/** A crescent opening to the upper left, with stars in the gap. */
private fun DrawScope.drawMoonAndStars() {
    val w = size.width
    val h = size.height

    val moonCenter = Offset(w * 0.56f, h * 0.56f)
    val moonR = w * 0.31f

    drawCircle(
        Brush.radialGradient(
            listOf(MoonDeep.copy(alpha = 0.20f), Color.Transparent),
            center = moonCenter,
            radius = w * 0.48f,
        ),
        radius = w * 0.48f,
        center = moonCenter,
    )

    val disc = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(moonCenter, moonR))
    }
    val bite = Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                Offset(moonCenter.x - moonR * 0.52f, moonCenter.y - moonR * 0.40f),
                moonR * 0.86f,
            ),
        )
    }
    val crescent = Path().apply { op(disc, bite, PathOperation.Difference) }
    drawPath(
        crescent,
        Brush.linearGradient(
            listOf(MoonLight, MoonDeep),
            start = Offset(moonCenter.x + moonR, moonCenter.y - moonR),
            end = Offset(moonCenter.x - moonR, moonCenter.y + moonR),
        ),
    )

    sparkle(Offset(w * 0.22f, h * 0.22f), w * 0.085f)
    sparkle(Offset(w * 0.42f, h * 0.12f), w * 0.05f)
    sparkle(Offset(w * 0.12f, h * 0.50f), w * 0.045f)
    drawCircle(StarColor, radius = w * 0.018f, center = Offset(w * 0.36f, h * 0.36f))
    drawCircle(StarColor.copy(alpha = 0.7f), radius = w * 0.014f, center = Offset(w * 0.86f, h * 0.16f))
}

/** A four-pointed star: two thin diamonds crossed, pinched at the waist. */
private fun DrawScope.sparkle(c: Offset, r: Float) {
    val waist = r * 0.26f
    val star = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x + waist * 0.35f, c.y - waist * 0.35f, c.x + r, c.y)
        quadraticTo(c.x + waist * 0.35f, c.y + waist * 0.35f, c.x, c.y + r)
        quadraticTo(c.x - waist * 0.35f, c.y + waist * 0.35f, c.x - r, c.y)
        quadraticTo(c.x - waist * 0.35f, c.y - waist * 0.35f, c.x, c.y - r)
        close()
    }
    drawPath(star, StarColor)
}
