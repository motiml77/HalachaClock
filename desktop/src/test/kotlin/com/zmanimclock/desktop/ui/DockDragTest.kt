package com.zmanimclock.desktop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle

/**
 * The law that moves the folded bookmark while it is being dragged.
 *
 * This file exists because of a shipped defect that was invisible on the
 * machine it was written on and catastrophic on the machine it was used on.
 * The old code moved the window by Compose's pointer delta — a quantity
 * measured in the LOCAL SPACE OF THE WINDOW BEING MOVED, in Compose pixels,
 * added to a position expressed in AWT user-space units. Both halves of that
 * are wrong, and together they form a closed loop whose behaviour depends
 * entirely on the display's scale factor:
 *
 *     m(n+1) = -g * m(n) + g * d          characteristic root: -g
 *
 * At g = 1.0 the root is -1: marginally stable, the tab tracks at roughly half
 * speed and merely feels sluggish. THAT IS WHY THIS SHIPPED. At the owner's
 * g = 2.75 the error is multiplied by 2.75 and flips sign on every mouse event;
 * the bookmark crossed a 1047-unit desktop in about five events and was
 * recovered at (-11916, +11915), with dock.properties poisoned to
 * edge=LEFT / along=1.0 — the clamp fingerprint of an alternating divergence.
 *
 * [T2][`the divergence the old law had, and that the new one cannot have`] is
 * therefore the point of the whole file: it simulates the OLD recurrence at
 * three scale factors and shows it leaving the screen at 2.75, then pushes the
 * identical cursor track through [dragTo] and shows it landing correctly at
 * EVERY scale — because the scale factor is not one of dragTo's inputs and
 * cannot be.
 *
 * Nothing here touches MouseInfo, a Window or a display. Every function under
 * test takes its geometry as a parameter for exactly that reason; the parts
 * that genuinely need a live pointer are covered by the manual script in the
 * commit message instead of being faked here.
 */
class DockDragTest {

    /**
     * The owner's actual work area, at 275% scaling. Every number in the bug
     * report came from this screen, and it is the smallest of the fixtures —
     * the one where each clamp comes closest to biting.
     */
    private val owner275 = Rectangle(0, 0, 1047, 607)

    /** An ordinary 1080p desktop with the taskbar along the bottom. */
    private val laptop = Rectangle(0, 0, 1920, 1040)

    /** Taskbar on the LEFT, so the work area does not start at the origin. */
    private val offsetOrigin = Rectangle(80, 0, 1840, 1080)

    private val screens = listOf(owner275, laptop, offsetOrigin)

    /** The bookmark, in the vertical orientation it has on a side edge. */
    private val tab = Dimension(36, 96)

    // ── T1: the drag law tracks the cursor exactly ──────────────────────────

    @Test
    fun `the window tracks the cursor one to one`() {
        // Deliberately started where 200 steps of (+3,-2) stay inside `laptop`:
        // the first draft began at y=300 and ran off the top, and the clamp —
        // correctly — held it at 0 while the assertion kept counting. Tracking
        // and clamping are different promises and get different tests; this one
        // must never touch a boundary or it stops testing tracking.
        val anchorWindow = Point(400, 500)
        val anchorPointer = Point(418, 548)
        var cursor = Point(anchorPointer)

        for (n in 1..200) {
            cursor = Point(cursor.x + 3, cursor.y - 2)
            val placed = dragTo(anchorWindow, anchorPointer, cursor, tab, laptop)
            assertEquals("x drifted at step $n", anchorWindow.x + 3 * n, placed.x)
            assertEquals("y drifted at step $n", anchorWindow.y - 2 * n, placed.y)
            assertTrue("step $n reached the clamp; this test no longer proves tracking", placed.y > laptop.y)
        }
    }

    @Test
    fun `the law is memoryless, so a dropped mouse event costs nothing`() {
        // Called out of order, or repeated, it answers the same thing. An
        // incremental law would have lost the motion in a coalesced event
        // permanently; this one is always right on the very next event.
        val aw = Point(400, 300)
        val ap = Point(418, 348)
        val far = Point(600, 500)

        val direct = dragTo(aw, ap, far, tab, laptop)
        val viaDetour = run {
            dragTo(aw, ap, Point(500, 400), tab, laptop)
            dragTo(aw, ap, Point(300, 200), tab, laptop)
            dragTo(aw, ap, far, tab, laptop)
        }
        assertEquals(direct, viaDetour)
    }

    // ── T2: THE REGRESSION TEST ─────────────────────────────────────────────

    @Test
    fun `the divergence the old law had, and that the new one cannot have`() {
        val wa = owner275
        val step = 2               // AWT units of genuine cursor motion per event
        val events = 20

        // --- The OLD law, simulated at three display scales. -----------------
        // m is the window's position error: the incremental code added a delta
        // measured in the moving window's own local space, scaled by g.
        fun simulateOld(g: Float): Double {
            var m = 0.0            // window offset from where it should be
            var previousLocal = 0.0
            var cursor = 0.0
            repeat(events) {
                cursor += step
                val local = (cursor - m) * g
                m += local - previousLocal
                previousLocal = local
            }
            return m
        }

        val at275 = simulateOld(2.75f)
        assertTrue(
            "at 275% the old law should have thrown the window off a ${wa.width}-unit " +
                "desktop, but it only reached $at275",
            Math.abs(at275) > 1e4,
        )

        val at100 = simulateOld(1.0f)
        assertTrue(
            "at 100% the old law should have merely lagged — this is why it shipped — " +
                "but it reached $at100",
            Math.abs(at100) < wa.width,
        )
        assertNotEquals(
            "the 100% case is not the same as the 275% case; a test that passed on the " +
                "developer's machine proved nothing about the owner's",
            Math.abs(at100) > 1e4,
            Math.abs(at275) > 1e4,
        )

        // --- The NEW law, over the identical cursor track, at every scale. ---
        // g does not appear below, because g is not an input to dragTo. That
        // absence is the fix, and this assertion is what pins it.
        val anchorWindow = Point(100, 200)
        val anchorPointer = Point(118, 248)
        val totalDelta = step * events
        val end = Point(anchorPointer.x + totalDelta, anchorPointer.y)

        val landed = dragTo(anchorWindow, anchorPointer, end, tab, wa)
        assertEquals(
            "the new law must land exactly where the cursor went, at any scale",
            anchorWindow.x + totalDelta,
            landed.x,
        )
        assertEquals(anchorWindow.y, landed.y)
    }

    // ── T3 / T4: the clamp ──────────────────────────────────────────────────

    @Test
    fun `a fling can never put the tab off the screen`() {
        for (wa in screens) {
            for (d in listOf(100_000, -100_000)) {
                for (p in listOf(Point(d, 0), Point(0, d), Point(d, d))) {
                    val anchorWindow = Point(wa.x + 10, wa.y + 10)
                    val anchorPointer = Point(wa.x + 20, wa.y + 20)
                    val cursor = Point(anchorPointer.x + p.x, anchorPointer.y + p.y)
                    val r = dragTo(anchorWindow, anchorPointer, cursor, tab, wa)
                    assertTrue(
                        "flung to $p on $wa the tab left the screen at $r",
                        r.x >= wa.x && r.y >= wa.y &&
                            r.x + tab.width <= wa.x + wa.width &&
                            r.y + tab.height <= wa.y + wa.height,
                    )
                }
            }
        }
    }

    @Test
    fun `a screen smaller than the tab does not throw`() {
        // coerceIn(min, max) throws when min > max. On a degenerate or
        // mid-reconfiguration display that is a crash in the middle of a drag,
        // which is why the maxima are coerceAtLeast'd first.
        val tiny = Rectangle(0, 0, 20, 20)
        val r = dragTo(Point(0, 0), Point(0, 0), Point(9999, 9999), tab, tiny)
        assertEquals(Point(0, 0), r)
    }

    // ── T5: click versus drag ───────────────────────────────────────────────

    @Test
    fun `a click is displacement, not path length`() {
        val anchor = Point(500, 500)

        // A shaky hand that wanders out to 3 units and comes back. Summed as
        // path length this is 12 units and the old code moved and SAVED the
        // bookmark on it; as peak displacement it is 3, below the slop.
        val wobble = listOf(
            Point(502, 500), Point(503, 500), Point(501, 500),
            Point(500, 500), Point(499, 501), Point(500, 500),
        )
        val peak = wobble.maxOf { travelledFrom(anchor, it) }
        assertTrue("a wobble of $peak units must still read as a click", peak < DRAG_SLOP_FOR_TEST)

        // A deliberate 5-unit push in one direction is a drag.
        assertTrue(travelledFrom(anchor, Point(505, 500)) >= DRAG_SLOP_FOR_TEST)
    }

    // ── T6 / T7: the preview arrow ──────────────────────────────────────────

    @Test
    fun `the preview arrow does not strobe on a diagonal`() {
        // On owner275 the LEFT/TOP diagonal passes through points where the two
        // edges are exactly equidistant. One unit of tremor there flips a bare
        // nearestEdge several times a second, which reads as the tab not
        // knowing where it is going.
        val wa = owner275
        var current = DockEdge.LEFT
        val onDiagonal = Rectangle(100 - tab.width / 2, 100 - tab.height / 2, tab.width, tab.height)

        for (n in 0 until 50) {
            val jitter = if (n % 2 == 0) 1 else -1
            val r = Rectangle(onDiagonal.x + jitter, onDiagonal.y - jitter, tab.width, tab.height)
            val next = previewEdgeFor(r, current, wa).first
            assertEquals("the arrow flipped on tremor at step $n", current, next)
            current = next
        }

        // But a decisive move — more than twice the bias — does flip it.
        val committed = Rectangle(onDiagonal.x, onDiagonal.y - 60, tab.width, tab.height)
        assertEquals(DockEdge.TOP, previewEdgeFor(committed, current, wa).first)
    }

    @Test
    fun `the preview and the commit agree away from the boundaries`() {
        // The hysteresis is allowed to disagree with the unbiased commit, but
        // only within PREVIEW_BIAS_UNITS of a diagonal. Well clear of one, the
        // arrow must promise exactly what the release delivers — otherwise the
        // preview is a lie, which is worse than no preview.
        val wa = owner275
        val clearlyLeft = Rectangle(0, 250, tab.width, tab.height)
        val clearlyBottom = Rectangle(500, wa.height - tab.height, tab.width, tab.height)

        for (start in DockEdge.entries) {
            assertEquals(
                "preview disagreed with the commit coming from $start",
                nearestEdge(clearlyLeft, wa).first,
                previewEdgeFor(clearlyLeft, start, wa).first,
            )
            assertEquals(
                nearestEdge(clearlyBottom, wa).first,
                previewEdgeFor(clearlyBottom, start, wa).first,
            )
        }
    }

    // ── T8: the repeat-drop that used to strand the tab ─────────────────────

    @Test
    fun `dropping on the placement it already had is still a fixed point`() {
        // Main.kt holds edge/along in mutableStateOf, which compares
        // structurally, so re-docking to an identical placement records no
        // state change and the repositioning effect never re-runs. The gesture
        // therefore sets the window bounds itself. This pins the arithmetic
        // that makes that unconditional re-snap safe: snapping something that
        // is already snapped must not move it.
        for (wa in screens) {
            for (edge in DockEdge.entries) {
                for (along in listOf(0f, 0.5f, 1f)) {
                    val placed = dockBounds(edge, along, wa)
                    val (again, alongAgain) = nearestEdge(placed, wa)
                    assertEquals("$edge at $along on $wa changed edge", edge, again)
                    assertEquals(
                        "$edge at $along on $wa moved when re-snapped",
                        placed,
                        dockBounds(again, alongAgain, wa),
                    )
                }
            }
        }
    }

    // ── T9: the drop that poisoned the saved file ───────────────────────────

    @Test
    fun `a drop far off screen still produces a legal placement`() {
        // The exact shape of rectangle the old runaway loop handed to save():
        // the recovered window was at (-11916, +11915). The clamp in dragTo
        // makes this unreachable now, but nearestEdge must stay total — a
        // guard against edge=LEFT / along=1.0 ever being written again.
        val wa = owner275
        val flung = Rectangle(-11916, 11915, tab.width, tab.height)
        val (edge, along) = nearestEdge(flung, wa)

        assertTrue("along must stay a legal fraction, was $along", along in 0f..1f)
        val bounds = dockBounds(edge, along, wa)
        assertTrue(
            "even an absurd drop must place the tab on screen, got $bounds",
            bounds.x >= wa.x && bounds.y >= wa.y &&
                bounds.x + bounds.width <= wa.x + wa.width &&
                bounds.y + bounds.height <= wa.y + wa.height,
        )
    }

    /**
     * Mirrors DRAG_SLOP_UNITS, which is private to the production file.
     * Duplicated rather than opened up: the constant's value is a UX decision
     * that belongs beside the gesture, and a test that reached in to read it
     * would assert nothing at all.
     */
    private val DRAG_SLOP_FOR_TEST = 4
}
