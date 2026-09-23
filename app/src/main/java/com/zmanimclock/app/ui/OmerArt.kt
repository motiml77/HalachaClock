package com.zmanimclock.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sefirat HaOmer artwork, drawn — no image assets, tinted/animated in code so
 * it stays crisp at any size and matches the app's other hand-drawn scene (the
 * Shabbat candles). The barley/wheat sheaf is the omer's own image: the omer
 * offering was a measure of barley, and the count runs from it to Shavuot.
 *
 * Grain anatomy (a central rachis with pointed leaflets alternating up both
 * sides, tapering to fine awns at the tip) follows real wheat-icon design —
 * e.g. Font Awesome Free's "wheat-awn" glyph — studied for proportion, not
 * copied: every path below is authored from scratch, the same way
 * [com.zmanimclock.app.ui.theme.AppIcons.Candle] is. The first attempt here
 * used plain stacked ovals for grains, which at small sizes read as a lumpy
 * snowman rather than wheat — pointed vesica-shaped leaflets are what
 * actually makes it legible as an ear of grain.
 */

/** Warm wheat gold — the one accent for this feature everywhere it appears. */
val OmerGold = Color(0xFF9C7A24)

/** A single upright wheat ear — the small icon (Settings, list, prompt). */
@Composable
fun WheatEar(modifier: Modifier = Modifier, tint: Color = OmerGold) {
    Canvas(modifier) {
        val th = (size.width * 0.05f).coerceAtLeast(1.2f)
        wheatEar(
            baseX = size.width * 0.5f,
            baseY = size.height * 0.98f,
            len = size.height * 0.94f,
            angle = 0f,
            grain = tint,
            stem = tint,
            th = th,
        )
    }
}

/**
 * The daily omer ring illustration: a bound wheat sheaf standing under a
 * moonlit, star-scattered night sky, with a warm glow at its base. Counterpart
 * to the Candles() scene — a slow star twinkle and a light breeze-sway keep it
 * alive without ever moving in lockstep (which reads as fake).
 */
@Composable
fun OmerNightScene(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "omer-scene")
    @Composable
    fun anim(periodMs: Int, from: Float, to: Float, label: String) = infinite.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = label,
    )

    val twinkleA by anim(1600, 0.30f, 1.0f, "twinkleA")
    val twinkleB by anim(2300, 0.45f, 0.85f, "twinkleB")
    val sway by anim(2600, -1.6f, 1.6f, "sway")

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val baseX = w * 0.5f
        val baseY = h * 0.90f

        // Moon with a soft halo, top corner.
        val moon = Offset(w * 0.80f, h * 0.15f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x59FDFBE6), Color(0x00FDFBE6)),
                center = moon,
                radius = w * 0.34f,
            ),
            radius = w * 0.34f,
            center = moon,
        )
        drawCircle(Color(0xFFF3EFD0), radius = w * 0.072f, center = moon)

        // Stars — fixed positions, alternating twinkle phases.
        val stars = listOf(
            0.12f to 0.10f, 0.25f to 0.20f, 0.40f to 0.07f, 0.55f to 0.16f,
            0.66f to 0.28f, 0.16f to 0.32f, 0.34f to 0.40f, 0.90f to 0.32f,
            0.92f to 0.11f, 0.07f to 0.20f, 0.48f to 0.30f, 0.70f to 0.05f,
        )
        stars.forEachIndexed { i, (fx, fy) ->
            val a = if (i % 2 == 0) twinkleA else twinkleB
            drawCircle(
                color = Color(0xFFFFFDF0).copy(alpha = a * 0.9f),
                radius = w * (if (i % 3 == 0) 0.013f else 0.008f),
                center = Offset(w * fx, h * fy),
            )
        }

        // Warm bloom behind the sheaf, grounding it in light.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x40E0C64F), Color(0x00E0C64F)),
                center = Offset(baseX, h * 0.60f),
                radius = w * 0.70f,
            ),
            radius = w * 0.70f,
            center = Offset(baseX, h * 0.60f),
        )

        // Contact shadow.
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x55000000), Color(0x00000000)),
                center = Offset(baseX, baseY),
                radius = w * 0.42f,
            ),
            topLeft = Offset(w * 0.12f, baseY - h * 0.03f),
            size = Size(w * 0.76f, h * 0.06f),
        )

        // The sheaf: a five-ear fan, each swaying a touch differently.
        val grainLight = Color(0xFFE9D170)
        val grainDeep = Color(0xFFD7B646)
        val stem = Color(0xFFB2913A)
        val th = w * 0.02f
        val fan = listOf(-30f, -15f, 0f, 15f, 30f)
        val lengths = listOf(0.80f, 0.92f, 1.0f, 0.92f, 0.80f)
        fan.forEachIndexed { i, ang ->
            val swayFactor = when {
                i < 2 -> -1f
                i > 2 -> 1f
                else -> 0.3f
            }
            wheatEar(
                baseX = baseX,
                baseY = baseY,
                len = h * 0.70f * lengths[i],
                angle = ang + sway * swayFactor,
                grain = if (i % 2 == 0) grainLight else grainDeep,
                stem = stem,
                th = th,
            )
        }

        // The tie binding the sheaf.
        drawRoundRect(
            color = Color(0xFF8F6E2C),
            topLeft = Offset(baseX - w * 0.10f, baseY - h * 0.17f),
            size = Size(w * 0.20f, h * 0.055f),
            cornerRadius = CornerRadius(w * 0.02f),
        )
        drawRoundRect(
            color = Color(0xFFB79445),
            topLeft = Offset(baseX - w * 0.10f, baseY - h * 0.155f),
            size = Size(w * 0.20f, h * 0.016f),
            cornerRadius = CornerRadius(w * 0.01f),
        )
    }
}

/**
 * One wheat ear rising from ([baseX], [baseY]), rotated [angle]° about its
 * base: a bare lower stalk, a thin central rachis, pointed leaflet-grains
 * alternating up both sides (tighter and more upright near the tip, like a
 * real spike), a crown grain, and fine awns off the very top.
 */
private fun DrawScope.wheatEar(
    baseX: Float,
    baseY: Float,
    len: Float,
    angle: Float,
    grain: Color,
    stem: Color,
    th: Float,
) {
    rotate(angle, pivot = Offset(baseX, baseY)) {
        val earStartY = baseY - len * 0.34f
        val tipY = baseY - len
        drawLine(
            color = stem,
            start = Offset(baseX, baseY),
            end = Offset(baseX, earStartY),
            strokeWidth = th,
            cap = StrokeCap.Round,
        )
        // Thin rachis running through the ear so the leaflets read as
        // attached to a spine rather than floating.
        drawLine(
            color = stem,
            start = Offset(baseX, earStartY + len * 0.02f),
            end = Offset(baseX, tipY + len * 0.04f),
            strokeWidth = th * 0.5f,
            cap = StrokeCap.Round,
        )

        val rows = 4
        val grainLen = len * 0.30f
        val grainWidth = len * 0.10f
        for (i in 0 until rows) {
            val t = i / (rows - 1f) // 0 = bottom of ear, 1 = near the tip
            val y = earStartY - (earStartY - tipY) * 0.80f * t
            val outAngle = 34f - t * 10f // more upright near the tip
            val shrink = 1f - t * 0.22f
            leaflet(Offset(baseX, y), -outAngle, grainLen * shrink, grainWidth * shrink, grain)
            leaflet(Offset(baseX, y), outAngle, grainLen * shrink, grainWidth * shrink, grain)
        }
        // Crown grain, straight up off the tip.
        leaflet(Offset(baseX, tipY + len * 0.05f), 0f, grainLen * 0.85f, grainWidth * 0.85f, grain)

        // Fine awns fanning off the very top.
        val awn = len * 0.15f
        val awnFrom = Offset(baseX, tipY - len * 0.02f)
        listOf(-13f, 0f, 13f).forEach { a ->
            val r = Math.toRadians(a.toDouble())
            drawLine(
                color = grain,
                start = awnFrom,
                end = Offset(
                    baseX + (awn * sin(r)).toFloat(),
                    awnFrom.y - (awn * cos(r)).toFloat(),
                ),
                strokeWidth = th * 0.4f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * One grain leaflet: a pointed vesica (two arcs meeting at sharp tips), the
 * shape that actually reads as "wheat" rather than a berry — attached at
 * [attach] and swept out at [angle]° from straight up (0°), [len] long and
 * [width] wide at its middle.
 */
private fun DrawScope.leaflet(attach: Offset, angle: Float, len: Float, width: Float, color: Color) {
    rotate(angle, pivot = attach) {
        translate(left = attach.x, top = attach.y) {
            drawPath(vesica(len, width), color = color)
        }
    }
}

/** A pointed leaf outline: tip at the origin, other tip [len] above it. */
private fun vesica(len: Float, width: Float): Path = Path().apply {
    moveTo(0f, 0f)
    quadraticTo(width, -len * 0.42f, 0f, -len)
    quadraticTo(-width, -len * 0.42f, 0f, 0f)
    close()
}
