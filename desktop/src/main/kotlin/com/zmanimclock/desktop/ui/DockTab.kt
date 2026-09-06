package com.zmanimclock.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

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
 * detector on the same node race for the same pointer-down event, so a single
 * raw gesture decides after the fact — by how far the pointer actually moved —
 * whether this was a click (open) or a drag (redock).
 *
 * That gesture is [awaitEachGesture] and NOT `detectDragGestures`, which this
 * used to be and which cannot express it: a press released before the drag
 * slop invokes NO callback at all. Read from the compiled foundation
 * (`DragGestureDetectorKt$detectDragGestures$13`, `2284: ifnull 3013`) the
 * null-slop branch jumps past onDragStart, onDrag, onDragCancel AND onDragEnd
 * straight to the return — so on a control whose entire job is being clicked,
 * a perfectly still click reached nothing. Owning the pointer-up ourselves is
 * the only way to hear it.
 *
 * THE DRAG LAW IS ANCHORED, NEVER INCREMENTAL — see [dragTo], and read its
 * signature as the proof: it takes where the window was and where the pointer
 * was WHEN THE PRESS BEGAN, plus where the pointer is now. It does NOT take
 * the window's current position, so it cannot feed its own output back in.
 * This is not stylistic. The previous version added Compose's pointer delta
 * to `window.location` on every event, and that delta is measured in the local
 * space of the very window being moved, in Compose pixels, while setLocation
 * speaks AWT user-space units. Writing g for the display scale, the closed
 * loop is m(n+1) = -g*m(n) + g*d, whose characteristic root is -g:
 *   g = 1.00 (a 100% display) — root -1, marginally stable. The tab tracked at
 *       roughly half speed and merely felt sluggish, which is how this shipped.
 *   g = 2.75 (the owner's display, measured: GraphicsConfiguration
 *       .defaultTransform = 2.75) — the error is multiplied by 2.75 AND flips
 *       sign on every mouse event, crossing a 1047-unit desktop in about five
 *       events. The bookmark was found at (-11916, +11915) with dock.properties
 *       reading edge=LEFT / along=1.0 — the clamp fingerprint of exactly this.
 * The owner's report was "כאילו נעלמת" — it disappears. It was not hiding; it
 * had been flung twelve thousand pixels off the desktop.
 *
 * DURING a drag the tab moves freely, following the cursor with no snapping,
 * so the owner gets direct visual feedback of picking it up — snapping only
 * happens once, on release, against whichever of the four edges the drop
 * point is nearest. The window's SIZE (and therefore its shape) does not
 * change until that release either, for the same reason: reflowing the shape
 * mid-drag would fight the thing the owner is actively looking at. What DOES
 * change mid-drag is the arrow, which turns to face the edge the tab would
 * land on if released now — see [previewEdgeFor]. That is the answer to "ואיפה
 * עוצרים": the tab tells you where it is going before you let go.
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

            // The edge the tab would land on if the button were released right
            // now, or null when nothing is being dragged. Only the arrow reads
            // it, and only while a drag is in flight — which is why it is
            // separate from `edge`: `edge` is the committed placement and must
            // not flicker just because a gesture is passing over a diagonal.
            var previewEdge by remember { mutableStateOf<DockEdge?>(null) }

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
                    .pointerInput(alwaysOnTop) {
                        awaitEachGesture {
                            // requireUnconsumed = false: nothing else on this
                            // node competes for the press, and refusing an
                            // already-consumed down would silently make the
                            // bookmark dead to a click.
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()

                            // EVERYTHING THE GESTURE NEEDS, READ ONCE, HERE.
                            // The work area is frozen for the whole gesture so
                            // that the edge the arrow promises and the edge the
                            // release commits are computed against the same
                            // screen — a resolution change mid-drag would
                            // otherwise make the preview a lie.
                            val grabPointer = pointerOnScreen()
                            val grabWindow = window.location
                            val work = workArea()
                            val reachable = reachableBounds()
                            val tab = Dimension(window.width, window.height)

                            // Peak DISPLACEMENT from the press point, not the
                            // length of the path walked: a slow wobble that
                            // ends where it started is a click with a shaky
                            // hand, and summing |dx|+|dy| per event called it a
                            // drag and moved the bookmark.
                            var travel = 0
                            var released = false
                            var preview = edge

                            // A bookmark that slides behind a browser window
                            // half way through the drag is the same complaint —
                            // "it disappeared" — reached by a different route.
                            // Only needed when the owner has NOT asked for
                            // always-on-top, which is the default.
                            if (!alwaysOnTop) WindowPinning.raiseToTop(window)

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (!change.pressed) {
                                        released = true
                                        break
                                    }
                                    // The cursor's ABSOLUTE position, in the
                                    // same AWT user-space units setLocation
                                    // speaks (measured on the owner's 275%
                                    // display: MouseInfo reported (439,536) on
                                    // a 1047x655 screen, so these are logical
                                    // units, not physical pixels). Absolute is
                                    // what breaks the feedback loop; same-units
                                    // is what removes the scale factor. Compose
                                    // deltas give neither.
                                    val now = pointerOnScreen() ?: continue
                                    if (grabPointer == null) continue

                                    travel = maxOf(travel, travelledFrom(grabPointer, now))
                                    window.setLocation(dragTo(grabWindow, grabPointer, now, tab, reachable))

                                    preview = previewEdgeFor(window.bounds, preview, work).first
                                    if (previewEdge != preview) previewEdge = preview
                                }
                            } finally {
                                previewEdge = null
                                if (!alwaysOnTop) WindowPinning.clearTopmost(window)

                                if (released && travel < DRAG_SLOP_UNITS) {
                                    onOpen()
                                } else {
                                    // Also the cancellation path — something
                                    // else claiming the pointer mid-drag must
                                    // never be read as a click, but must still
                                    // leave the tab flush against a real edge
                                    // rather than stranded wherever the last
                                    // move put it.
                                    val (newEdge, newAlong) = nearestEdge(window.bounds, work)
                                    // THE WINDOW IS MOVED HERE, NOT BY THE
                                    // EFFECT ABOVE. Routing the snap through
                                    // onRedock alone was a real defect: Main.kt
                                    // holds edge/along in mutableStateOf, which
                                    // compares structurally, so re-docking to
                                    // the placement it already had recorded no
                                    // change — remember(edge, along) did not
                                    // recompute and LaunchedEffect did not
                                    // re-run, leaving the tab exactly where the
                                    // drag dropped it with no path back. The
                                    // clamp in nearestEdge makes repeat values
                                    // likely, not rare. onRedock is now
                                    // bookkeeping and persistence only.
                                    window.bounds = dockBounds(newEdge, newAlong, work)
                                    onRedock(newEdge, newAlong)
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                // The arrow alone, pointing the direction THIS edge opens —
                // see dockArrow. The logo was tried here once and dropped at
                // the owner's request: at bookmark size it renders as a
                // smudged square, and the tab needs to say exactly one thing.
                //
                // Mid-drag it says a second thing, which is the same thing:
                // it points at the edge the tab is about to land on. Nothing
                // else about the tab changes while it is in the air, so this
                // arrow is the whole of "where does it stop" — and it is
                // hysteretic (see previewEdgeFor) so that it states an answer
                // rather than strobing between two of them near a diagonal.
                Icon(
                    dockArrow(previewEdge ?: edge),
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

/**
 * A press that never gets this far from where it started is a click, not a drag.
 *
 * AWT USER-SPACE UNITS — the space [MouseInfo] and `Window.getLocation` both
 * speak, and therefore the same on every display. The value it replaces was 6
 * Compose pixels, which is a DIFFERENT GESTURE ON EVERY MACHINE: 6 units at
 * 100% scaling, 2.18 at the owner's 275%. Four units is comfortably above
 * ordinary hand tremor while releasing a button and far below any intentional
 * drag. (It is not SM_CXDRAG, which an earlier draft of this claimed — this
 * machine reports `DnD.gestureMotionThreshold = 2`.)
 */
private const val DRAG_SLOP_UNITS = 4

/**
 * How much nearer a new edge must be than the current one before the preview
 * arrow switches to it, in AWT user-space units.
 *
 * Without it the arrow strobes: along the diagonals two edges are exactly
 * equidistant, and one unit of hand tremor flips the answer several times a
 * second, which reads as the tab not knowing where it is going. With it the
 * arrow states one answer and holds it until the pointer has clearly committed.
 *
 * Deliberately NOT applied to the drop itself — [nearestEdge] commits
 * unbiased. The two can therefore disagree, but only within 24 units of a
 * diagonal, where the two edges are equidistant and neither answer is wrong.
 * Biasing the commit as well would mean the tab's resting place depended on
 * where it had been before, which is harder to explain than a 24-unit band.
 */
private const val PREVIEW_BIAS_UNITS = 24

/**
 * The screen minus the taskbar. The one place the live display is consulted,
 * so that the geometry below can be exercised against a made-up screen.
 *
 * KNOWN LIMIT, deliberately not fixed here: this is
 * `SunGraphicsEnvironment.getUsableBounds(defaultScreenDevice)` — the PRIMARY
 * monitor's work area, never the union of all of them (Win32GraphicsEnvironment
 * does not override it; the javadoc's "entire display area" wording describes
 * the Xinerama case). So a tab dropped on a secondary monitor snaps back to a
 * primary edge. Main.kt's panel has the identical limit, so at least the two
 * agree with each other, and the owner has one display. [reachableBounds] is
 * the union, which is what keeps a second screen reachable during the drag.
 */
private fun workArea(): Rectangle =
    GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds

/**
 * Every pixel the tab may be dragged over: the union of all monitors' FULL
 * bounds.
 *
 * Full bounds, not work areas, and the distinction is visible: the taskbar
 * strip is 48 units tall on this machine and is somewhere a pointer can go, so
 * clamping to the work area would make the tab stick and judder along an
 * invisible line above the taskbar for the rest of the drag.
 *
 * Folded with `reduceOrNull` rather than `fold(Rectangle())` — an empty seed
 * is a 0x0 rectangle AT THE ORIGIN, and `union` with it drags the result back
 * to (0,0) on any layout whose primary screen does not start there.
 */
private fun reachableBounds(): Rectangle =
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        .map { it.defaultConfiguration.bounds }
        .reduceOrNull { a, b -> a.union(b) }
        ?: workArea()

/** Warned about at most once per run; a per-event message would be a torrent. */
private val pointerInfoWarned = AtomicBoolean(false)

/**
 * The cursor's absolute position in AWT user-space units, or null.
 *
 * Null is possible in principle (a headless or locked session), and both call
 * sites skip the event rather than guess. That combination is silent by
 * construction — the tab would simply not move and the release would open the
 * panel, i.e. "dragging just opens it" — so it says so once on stderr. Silence
 * is precisely what let the original defect survive review.
 */
private fun pointerOnScreen(): Point? {
    val p = MouseInfo.getPointerInfo()?.location
    if (p == null && pointerInfoWarned.compareAndSet(false, true)) {
        System.err.println("DockTab: MouseInfo.getPointerInfo() returned null — the bookmark cannot be dragged")
    }
    return p
}

/**
 * Where the tab window belongs right now, mid-drag.
 *
 * ANCHORED, NOT INCREMENTAL, and the signature is the guarantee: the window's
 * CURRENT position is not a parameter, so no output of this function can ever
 * become one of its inputs. That is the whole fix — see the class comment for
 * the recurrence the incremental version produced and where it put the
 * bookmark. Any future change that adds a current-position parameter here is
 * reintroducing the bug and should be rejected on sight.
 *
 * It is also memoryless: call it with the same three points in any order, any
 * number of times, and it answers the same thing. A dropped or coalesced mouse
 * event therefore costs nothing — the next one is still exactly right, where
 * an incremental law would have lost that motion permanently.
 *
 * Clamped so the whole tab stays somewhere a pointer can reach. The old code
 * clamped nothing at all, which is what turned a runaway into an unrecoverable
 * one; [com.zmanimclock.desktop.widget.WidgetPlacement] has always clamped its
 * own restore for the same reason. `coerceAtLeast` on the maxima keeps
 * `coerceIn` from throwing on a screen smaller than the tab.
 */
internal fun dragTo(
    anchorWindow: Point,
    anchorPointer: Point,
    pointerNow: Point,
    tab: Dimension,
    reachable: Rectangle,
): Point {
    val x = anchorWindow.x + (pointerNow.x - anchorPointer.x)
    val y = anchorWindow.y + (pointerNow.y - anchorPointer.y)
    val maxX = (reachable.x + reachable.width - tab.width).coerceAtLeast(reachable.x)
    val maxY = (reachable.y + reachable.height - tab.height).coerceAtLeast(reachable.y)
    return Point(x.coerceIn(reachable.x, maxX), y.coerceIn(reachable.y, maxY))
}

/**
 * How far the pointer has strayed from where the press began — Manhattan
 * DISPLACEMENT, deliberately not the length of the path walked.
 *
 * The caller keeps the running maximum of this. Summing per-event distance
 * instead (which is what the old code did) counts a shaky hand that returns to
 * its starting point as a long drag, and then moves and saves the bookmark on
 * what the owner performed as a click.
 */
internal fun travelledFrom(anchor: Point, now: Point): Int =
    abs(now.x - anchor.x) + abs(now.y - anchor.y)

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
    // roundToInt, matching Compose's own Windows_desktopKt.setSizeImpl, which
    // is what turns the identical Dp values into this window's actual size via
    // rememberWindowState. Behaviour-identical today because 36 and 96 are
    // whole numbers; the moment either constant gains a fraction, truncating
    // here and rounding there leaves a one-unit gap between the tab and the
    // screen edge on RIGHT and BOTTOM — a bookmark that is visibly not flush.
    val thicknessPx = TAB_THICKNESS.value.roundToInt()
    val lengthPx = TAB_LENGTH.value.roundToInt()

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
    val edge = edgeDistances(dropped, wa).minBy { it.second }.first
    return edge to alongFor(edge, dropped, wa)
}

/**
 * Distance from the rectangle's centre to each of the four work-area edges.
 *
 * THESE ARE SIGNED, AND THAT IS CORRECT — recorded here because it has now
 * been proposed as a bug twice, by two independent reviews, and re-derived as
 * correct both times. Take the cited case: a centre at (10, 620) on a 1047x607
 * work area, i.e. dropped into the taskbar strip below the desktop. Signed
 * distance picks BOTTOM. Clamping the centre into the work area first picks
 * BOTTOM. True distance to each edge SEGMENT — the geometrically unimpeachable
 * answer — also picks BOTTOM, 13 against 16.4 to LEFT. Wrapping these in
 * `abs()` returns LEFT, which is simply wrong, and a proposed "fix" of exactly
 * that shape failed its own author's regression test.
 *
 * In any case the clamp in [dragTo] means the tab's centre can no longer leave
 * the reachable area at all, so a negative distance is now unreachable rather
 * than merely rare. Please do not "fix" this a third time.
 */
private fun edgeDistances(rect: Rectangle, wa: Rectangle): List<Pair<DockEdge, Double>> {
    val centerX = rect.x + rect.width / 2.0
    val centerY = rect.y + rect.height / 2.0
    return listOf(
        DockEdge.LEFT to (centerX - wa.x),
        DockEdge.RIGHT to (wa.x + wa.width - centerX),
        DockEdge.TOP to (centerY - wa.y),
        DockEdge.BOTTOM to (wa.y + wa.height - centerY),
    )
}

/** How far along [edge] the rectangle's centre sits, as the stored 0f..1f fraction. */
private fun alongFor(edge: DockEdge, rect: Rectangle, wa: Rectangle): Float {
    val centerX = rect.x + rect.width / 2.0
    val centerY = rect.y + rect.height / 2.0
    val along = when {
        edge.isVertical -> (centerY - wa.y) / wa.height
        else -> (centerX - wa.x) / wa.width
    }
    return along.toFloat().coerceIn(0f, 1f)
}

/**
 * The same question as [nearestEdge], asked mid-drag and answered stickily:
 * which edge would this land on if the button were released right now?
 *
 * [current] is the edge the preview is already showing. A different edge has
 * to be nearer by more than [biasUnits] before it takes over — see
 * [PREVIEW_BIAS_UNITS] for why a bare `nearestEdge` here would strobe.
 *
 * Feeding the result back in as [current] on the next event is what makes the
 * hysteresis a ratchet rather than a one-shot comparison; the caller does
 * exactly that.
 */
internal fun previewEdgeFor(
    dragged: Rectangle,
    current: DockEdge,
    wa: Rectangle = workArea(),
    biasUnits: Int = PREVIEW_BIAS_UNITS,
): Pair<DockEdge, Float> {
    val distances = edgeDistances(dragged, wa)
    val nearest = distances.minBy { it.second }
    val currentDistance = distances.first { it.first == current }.second
    val edge = if (nearest.first == current || nearest.second < currentDistance - biasUnits) {
        nearest.first
    } else {
        current
    }
    return edge to alongFor(edge, dragged, wa)
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

/**
 * Points away from the edge the tab is stuck to — "pull me out of here".
 *
 * It used to mean something narrower: the direction the panel would open. The
 * panel now always opens as a narrow column flush LEFT whatever edge the
 * bookmark sits on (see Main.kt's snap-to-edge effect for the screenful of
 * reasons), so this is the surviving, more general reading, and it is the one
 * that was always doing the work visually: the arrow leads out of the screen
 * edge and into the desktop, which is where the app appears.
 *
 * Mid-drag it answers the more urgent question instead — which edge the tab is
 * about to land on. Same glyph, and no contradiction: both readings point out
 * of whichever edge the tab is against.
 */
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
