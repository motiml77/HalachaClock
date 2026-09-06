package com.zmanimclock.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.zmanimclock.desktop.widget.WindowPinning
import com.zmanimclock.desktop.Ext
import com.zmanimclock.desktop.ZmanimDesktopTheme
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import kotlin.math.abs

/**
 * The folded state of the app: a bookmark against a screen edge — any edge.
 *
 * The owner's request, verbatim in spirit: minimising should not drop the app
 * to the taskbar — it should tuck it against the side of the screen as a small
 * tab with an arrow, and clicking the tab opens it back up like a side panel.
 * So this window exists exactly while the app is folded, and it does two
 * things: it can be CLICKED to open, and it can be DRAGGED anywhere around the
 * screen's perimeter to re-dock against whichever edge — and at whatever
 * point along that edge — the owner drops it.
 *
 * ONE POINTER GESTURE, DELIBERATELY NOT TWO. A `clickable` alongside a drag
 * detector on the same node race for the same pointer-down event; instead
 * [detectDragGestures] alone decides after the fact, by how far the pointer
 * actually moved, whether this was a click (open) or a drag (redock) — see
 * the gesture handler below.
 *
 * DURING a drag the tab moves freely, following the cursor with no snapping,
 * so the owner gets direct visual feedback of picking it up — snapping only
 * happens once, on release, against whichever of the four edges the drop
 * point is nearest. The window's SIZE (and therefore its shape) does not
 * change until that release either, for the same reason: reflowing the shape
 * mid-drag would fight the thing the owner is actively looking at.
 *
 * Design decisions carried over from the LEFT-only original, now applied per
 * edge — see [dockBounds] and [drawDockBorder] for exactly how:
 *  - Rounded on the side facing the desktop; flat on the side that is the
 *    screen edge, so the shape reads as growing OUT of the edge rather than a
 *    small window parked NEAR it.
 *  - The arrow points the direction the window will actually open.
 *  - `alwaysOnTop`, subject to the owner's setting — a bookmark that other
 *    windows can bury is lost.
 *  - NOT focusable, and closing it opens the app. There is no interaction
 *    with a bookmark except through it; every path leads back to the window.
 */
@Composable
internal fun ApplicationScope.DockTabWindow(
    visible: Boolean,
    alwaysOnTop: Boolean,
    edge: DockEdge,
    along: Float,
    onOpen: () -> Unit,
    onRedock: (DockEdge, Float) -> Unit,
) {
    val bounds = remember(edge, along) { dockBounds(edge, along) }
    val state = rememberWindowState(
        position = WindowPosition(bounds.x.dp, bounds.y.dp),
        size = DpSize(bounds.width.dp, bounds.height.dp),
    )

    Window(
        onCloseRequest = onOpen,
        state = state,
        // ALWAYS composed, shown by flag — see the original design note this
        // preserves: a window that already exists and merely becomes visible
        // appears immediately, where creating one from scratch cost seconds.
        visible = visible,
        title = "שעון מעורר - זמנים הלכתיים",
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = alwaysOnTop,
    ) {
        // Re-apply the computed bounds on every redock — the Window's own
        // rememberWindowState only reads its initial position/size argument
        // once, so a LATER change to `edge`/`along` (from a drag-and-drop, or
        // simply switching MainTab which changes nothing here but keeps this
        // effect's dependency list honest) needs to be pushed onto the AWT
        // window directly, the same fix Main.kt's panel-opening effect
        // needed for the identical reason.
        //
        // KEYED ON `visible` TOO. This window is composed while hidden, and a
        // hidden window has no peer — `isDisplayable` is false and the guard
        // below returns without applying anything. Keyed only on
        // window/edge/along, that early return was FINAL for a placement that
        // never changed again: the effect had already run, so folding the app
        // later re-showed the window without this ever firing, leaving the
        // initial rememberWindowState values as the only thing positioning it.
        // That is exactly the load-a-saved-placement path, and exactly the
        // path autostart-folded now takes on every boot, so it gets a real
        // apply the moment the window actually exists rather than a race it
        // usually wins.
        LaunchedEffect(window, edge, along, visible) {
            if (!window.isDisplayable) return@LaunchedEffect
            window.setBounds(bounds.x, bounds.y, bounds.width, bounds.height)
        }

        // alwaysOnTop puts the window IN the topmost band; it does not keep it
        // at the top OF it. The poll re-asserts, which is what makes "above
        // fullscreen video" actually hold rather than hold until something
        // else floats.
        DisposableEffect(window, alwaysOnTop) {
            WindowPinning.keepOnTop(window, alwaysOnTop)
            onDispose { WindowPinning.stopTopTimer(window) }
        }

        ZmanimDesktopTheme {
            val ext = Ext.colors
            val cs = MaterialTheme.colorScheme
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val shape = dockShape(edge)

            // Accumulated pointer travel since the current press began — the
            // one number that decides click vs. drag on release. A physical
            // mouse click is never perfectly stationary; DRAG_THRESHOLD_PX is
            // comfortably above ordinary hand tremor and comfortably below an
            // intentional drag.
            var dragged by remember { mutableFloatStateOf(0f) }

            Box(
                Modifier.fillMaxSize()
                    // 90% opaque. Enough to read as a solid tab, enough to let
                    // whatever is behind it show through — so it reads as part
                    // of the desktop rather than a window parked on top of it.
                    .alpha(0.9f)
                    .background(Brush.verticalGradient(listOf(ext.heroTop, ext.heroBottom)), shape)
                    // Hover brightens the whole tab, not just the arrow: the
                    // affordance is "this entire thing is a button".
                    .background(Color.White.copy(alpha = if (hovered) 0.10f else 0f), shape)
                    // GOLD ON THREE SIDES — never on the side that is the
                    // screen edge; see [drawDockBorder] for why per edge.
                    // Hand-drawn because Modifier.border has no per-side
                    // option: it strokes the whole outline or nothing.
                    .drawBehind {
                        drawDockBorder(
                            edge = edge,
                            color = if (hovered) ext.accentGold else ext.accentGold.copy(alpha = 0.75f),
                            strokeWidthPx = 1.5.dp.toPx(),
                            radiusPx = 16.dp.toPx(),
                        )
                    }
                    .hoverable(interaction)
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragged = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragged += abs(amount.x) + abs(amount.y)
                                // Free movement, no snapping — the redock only
                                // happens once, in onDragEnd below.
                                val loc = window.location
                                window.setLocation(
                                    (loc.x + amount.x).toInt(),
                                    (loc.y + amount.y).toInt(),
                                )
                            },
                            onDragEnd = {
                                if (dragged < DRAG_THRESHOLD_PX) {
                                    onOpen()
                                } else {
                                    val (newEdge, newAlong) = nearestEdge(window.bounds)
                                    onRedock(newEdge, newAlong)
                                }
                            },
                            // A cancelled gesture (something else claims the
                            // pointer mid-drag — rare with only this one
                            // gesture detector on the node, but not
                            // impossible) still leaves the window wherever
                            // onDrag last moved it. Without this it would sit
                            // there — wrong size, wrong shape, snapped to
                            // nothing — until the user managed a clean drag or
                            // restarted the app. Snapping here costs nothing
                            // when dragged is small: it just redocks to
                            // wherever it already was.
                            onDragCancel = {
                                val (newEdge, newAlong) = nearestEdge(window.bounds)
                                onRedock(newEdge, newAlong)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                // The arrow alone, pointing the direction THIS edge opens —
                // see dockArrow. The logo was tried here once and dropped at
                // the owner's request: at bookmark size it renders as a
                // smudged square, and the tab needs to say exactly one thing.
                Icon(
                    dockArrow(edge),
                    contentDescription = "פתח את שעון מעורר - זמנים הלכתיים",
                    tint = ext.accentGold,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * The tab's visual content alone — background, border and arrow — sized and
 * shaped for one edge, with no window around it.
 *
 * Exists so RenderShotTest can render and actually LOOK AT all four edges'
 * hand-drawn borders: the real [DockTabWindow] needs a live [ApplicationScope]
 * and a real platform [Window] to exist at all (it positions itself via
 * `GraphicsEnvironment` and drags itself via the AWT window), neither of
 * which the offscreen rig can supply. Every visual decision that could be
 * wrong — which corners are rounded, which side is left unstroked, which way
 * the arrow points — lives entirely in this smaller piece, so shooting it is
 * exactly as good a check as shooting the real window would have been.
 */
@Composable
internal fun DockTabPreview(edge: DockEdge) {
    val ext = Ext.colors
    val shape = dockShape(edge)
    val tabSize = if (edge.isVertical) DpSize(TAB_THICKNESS, TAB_LENGTH) else DpSize(TAB_LENGTH, TAB_THICKNESS)

    Box(
        Modifier.size(tabSize.width, tabSize.height)
            .background(Brush.verticalGradient(listOf(ext.heroTop, ext.heroBottom)), shape)
            .drawBehind {
                drawDockBorder(
                    edge = edge,
                    color = ext.accentGold,
                    strokeWidthPx = 1.5.dp.toPx(),
                    radiusPx = 16.dp.toPx(),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            dockArrow(edge),
            contentDescription = null,
            tint = ext.accentGold,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** The bookmark's fixed dimension — its thickness against the edge it grows out of. */
private val TAB_THICKNESS = 36.dp

/** The bookmark's other dimension — how far it runs along the edge. */
private val TAB_LENGTH = 96.dp

/** A stationary press within this many pixels of travel is a click, not a drag. */
private const val DRAG_THRESHOLD_PX = 6f

/**
 * The screen minus the taskbar. The one place the live display is consulted,
 * so that the geometry below can be exercised against a made-up screen.
 */
private fun workArea(): Rectangle =
    GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds

/**
 * Where the bookmark sits for a given edge and position-along-that-edge.
 *
 * [along] is a fraction (0f..1f) of the work area's length along that edge;
 * see [DockPlacement] for why a fraction rather than a pixel offset. The
 * result is clamped so the bookmark's full [TAB_LENGTH] always stays on
 * screen, the same way the widget clamps its own saved position.
 *
 * [wa] IS A PARAMETER, defaulted to the real screen, purely so this is
 * testable. It and [nearestEdge] are the two halves of the "remember where I
 * dropped it" promise — drop point in, fraction out, fraction in, position
 * back out — and that round trip was previously unprovable: both read the
 * live [GraphicsEnvironment] internally, so a test could only ever assert
 * against whatever monitor happened to be attached, which is no assertion at
 * all. Injected, the pair is checked at 1920x1040, 4K and a taskbar-on-the-
 * left offset origin, none of which need a display.
 */
internal fun dockBounds(edge: DockEdge, along: Float, wa: Rectangle = workArea()): Rectangle {
    val thicknessPx = TAB_THICKNESS.value.toInt()
    val lengthPx = TAB_LENGTH.value.toInt()

    // The tab's CENTRE lands at `along` of the way down (or across) the work
    // area, then the whole tab is pulled back inside it. The clamp is why
    // along=0f and along=1f are legal rather than half-off-screen positions.
    fun centred(start: Int, lengthAvailable: Int): Int {
        val target = start + (along * lengthAvailable).toInt() - lengthPx / 2
        val maxStart = start + (lengthAvailable - lengthPx).coerceAtLeast(0)
        return target.coerceIn(start, maxStart)
    }

    return when (edge) {
        DockEdge.LEFT -> Rectangle(
            wa.x, centred(wa.y, wa.height), thicknessPx, lengthPx,
        )
        DockEdge.RIGHT -> Rectangle(
            wa.x + wa.width - thicknessPx, centred(wa.y, wa.height), thicknessPx, lengthPx,
        )
        DockEdge.TOP -> Rectangle(
            centred(wa.x, wa.width), wa.y, lengthPx, thicknessPx,
        )
        DockEdge.BOTTOM -> Rectangle(
            centred(wa.x, wa.width), wa.y + wa.height - thicknessPx, lengthPx, thicknessPx,
        )
    }
}

/**
 * The nearest screen edge to a just-dropped window, and how far along it —
 * as a fraction, ready to hand straight to [DockPlacement.save].
 *
 * Distance is measured from the window's CENTRE to each of the four edges of
 * the work area; whichever is smallest wins. Using the centre rather than a
 * corner means a tab dragged into the exact middle of the screen still picks
 * a definite, unsurprising edge (the nearer pair of edges, tie-broken by
 * whichever axis is closer) instead of a coordinate-order artefact.
 */
internal fun nearestEdge(dropped: Rectangle, wa: Rectangle = workArea()): Pair<DockEdge, Float> {
    val centerX = dropped.x + dropped.width / 2.0
    val centerY = dropped.y + dropped.height / 2.0

    val distances = listOf(
        DockEdge.LEFT to (centerX - wa.x),
        DockEdge.RIGHT to (wa.x + wa.width - centerX),
        DockEdge.TOP to (centerY - wa.y),
        DockEdge.BOTTOM to (wa.y + wa.height - centerY),
    )
    val edge = distances.minBy { it.second }.first

    val along = when {
        edge.isVertical -> ((centerY - wa.y) / wa.height)
        else -> ((centerX - wa.x) / wa.width)
    }
    return edge to along.toFloat().coerceIn(0f, 1f)
}

/** Rounded on the two corners facing the desktop; flat on the screen-edge side. */
private fun dockShape(edge: DockEdge): RoundedCornerShape {
    val r = 16.dp
    // Under the app's forced RTL, topStart/bottomStart ARE the right-hand
    // corners and topEnd/bottomEnd are the left-hand ones — the same rule
    // Main.kt's panel shape follows.
    return when (edge) {
        DockEdge.LEFT -> RoundedCornerShape(topStart = r, bottomStart = r)
        DockEdge.RIGHT -> RoundedCornerShape(topEnd = r, bottomEnd = r)
        DockEdge.TOP -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
        DockEdge.BOTTOM -> RoundedCornerShape(topStart = r, topEnd = r)
    }
}

/** Points the direction this edge actually opens towards. */
private fun dockArrow(edge: DockEdge): ImageVector = when (edge) {
    DockEdge.LEFT -> Icons.Filled.KeyboardArrowRight
    DockEdge.RIGHT -> Icons.Filled.KeyboardArrowLeft
    DockEdge.TOP -> Icons.Filled.KeyboardArrowDown
    DockEdge.BOTTOM -> Icons.Filled.KeyboardArrowUp
}

/**
 * The three-sided gold border, one edge at a time.
 *
 * Each branch traces: start at the screen-edge side, along it to the first
 * rounded corner, around that corner, along the desktop-facing side, around
 * the second corner, back to the screen-edge side — leaving the screen-edge
 * side itself unstroked, which is what lets the shape read as emerging FROM
 * the edge rather than a window sitting NEAR it.
 *
 * Every arc below was derived by the same method, checked against the one
 * case that already shipped and was known correct (LEFT, unchanged from the
 * original single-edge implementation): the tangent point where a straight
 * segment meets a rounded corner sits at the compass angle — 0°=east(right),
 * 90°=south(down), 180°=west(left), 270°=north(up), matching Compose's
 * Path.arcTo — directly opposite the direction the corner is inset from; the
 * sweep is the short way (±90°) between the entry and exit tangent angles,
 * positive when that short way is the increasing direction. Then rendered
 * and read at all four edges via RenderShotTest before being trusted.
 */
private fun DrawScope.drawDockBorder(edge: DockEdge, color: Color, strokeWidthPx: Float, radiusPx: Float) {
    val half = strokeWidthPx / 2f
    val r = radiusPx
    val w = size.width
    val h = size.height

    val path = Path().apply {
        when (edge) {
            DockEdge.LEFT -> {
                val right = w - half
                val top = half
                val bottom = h - half
                moveTo(0f, top)
                lineTo(right - r, top)
                arcTo(Rect(right - 2 * r, top, right, top + 2 * r), 270f, 90f, false)
                lineTo(right, bottom - r)
                arcTo(Rect(right - 2 * r, bottom - 2 * r, right, bottom), 0f, 90f, false)
                lineTo(0f, bottom)
            }
            DockEdge.RIGHT -> {
                val left = half
                val top = half
                val bottom = h - half
                moveTo(w, top)
                lineTo(left + r, top)
                arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 270f, -90f, false)
                lineTo(left, bottom - r)
                arcTo(Rect(left, bottom - 2 * r, left + 2 * r, bottom), 180f, -90f, false)
                lineTo(w, bottom)
            }
            DockEdge.TOP -> {
                val left = half
                val right = w - half
                val bottom = h - half
                moveTo(left, 0f)
                lineTo(left, bottom - r)
                arcTo(Rect(left, bottom - 2 * r, left + 2 * r, bottom), 180f, -90f, false)
                lineTo(right - r, bottom)
                arcTo(Rect(right - 2 * r, bottom - 2 * r, right, bottom), 90f, -90f, false)
                lineTo(right, 0f)
            }
            DockEdge.BOTTOM -> {
                val left = half
                val right = w - half
                val top = half
                moveTo(left, h)
                lineTo(left, top + r)
                arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 180f, 90f, false)
                lineTo(right - r, top)
                arcTo(Rect(right - 2 * r, top, right, top + 2 * r), 270f, 90f, false)
                lineTo(right, h)
            }
        }
    }
    drawPath(path, color, style = Stroke(width = strokeWidthPx))
}
