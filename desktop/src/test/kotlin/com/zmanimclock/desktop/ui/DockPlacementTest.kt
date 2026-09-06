package com.zmanimclock.desktop.ui

import com.zmanimclock.desktop.data.DesktopPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.awt.Rectangle
import java.io.File

/**
 * "Put the bookmark back where I left it."
 *
 * The promise has three links and this exercises all three, because a break
 * in any one of them looks identical to the owner — the tab reappears in the
 * middle of the left edge as if nothing had been remembered:
 *
 *   1. DROP → PLACEMENT.  [nearestEdge] turns the rectangle the tab was
 *      released at into an edge plus a fraction along it.
 *   2. PLACEMENT → DISK.  [DockPlacement] writes and reads dock.properties.
 *   3. PLACEMENT → PIXELS.  [dockBounds] turns the fraction back into the
 *      rectangle the window is given on the next launch.
 *
 * Links 1 and 3 are each other's inverse, which is the property worth
 * asserting: drop the tab somewhere, quit, launch again, and the tab's CENTRE
 * must be back at the same point along the same edge. That is testable only
 * because both take the work area as a parameter — see [dockBounds].
 *
 * Every case runs on all four edges. Three of them were written in one go
 * when the feature stopped being LEFT-only, and an error in the rotated
 * arithmetic for, say, BOTTOM alone would otherwise ship unseen: nobody
 * docks to all four edges by hand before a release.
 */
class DockPlacementTest {

    /**
     * A perfectly ordinary 1080p screen with the taskbar along the bottom —
     * 1040 tall, not 1080, because [dockBounds] is given the WORK AREA.
     */
    private val laptop = Rectangle(0, 0, 1920, 1040)

    /**
     * The same screen with the taskbar moved to the LEFT, so the work area no
     * longer starts at the origin. Anything that quietly assumes x=0/y=0
     * passes on [laptop] and fails here, which is the only reason this second
     * screen exists.
     */
    private val taskbarOnLeft = Rectangle(80, 0, 1840, 1080)

    /** 4K. Same fractions, different pixels — the point of storing a fraction. */
    private val fourK = Rectangle(0, 0, 3840, 2120)

    private val screens = listOf(laptop, taskbarOnLeft, fourK)

    // ── Test isolation ──────────────────────────────────────────────────────
    // DockPlacement writes a real file. build.gradle.kts already redirects the
    // whole data directory away from the developer's profile, but every test
    // in the module shares that one directory, and these tests care about the
    // ABSENCE of dock.properties as much as its contents. So each gets its own
    // empty directory and the property is restored afterwards.

    private lateinit var previous: String
    private lateinit var dir: File

    @Before
    fun redirectDataDir() {
        previous = System.getProperty(DesktopPrefs.DATA_DIR_PROPERTY).orEmpty()
        dir = File(
            System.getProperty("java.io.tmpdir"),
            "halachclock-dock-${System.nanoTime()}",
        ).apply { mkdirs() }
        System.setProperty(DesktopPrefs.DATA_DIR_PROPERTY, dir.absolutePath)
    }

    @After
    fun restoreDataDir() {
        if (previous.isEmpty()) {
            System.clearProperty(DesktopPrefs.DATA_DIR_PROPERTY)
        } else {
            System.setProperty(DesktopPrefs.DATA_DIR_PROPERTY, previous)
        }
        dir.deleteRecursively()
    }

    // ── Link 2: the file ────────────────────────────────────────────────────

    @Test
    fun `nothing saved yet reads as null, not as a default`() {
        assertNull(
            "A first-ever launch must fall through to Main.kt's LEFT/0.5f default",
            DockPlacement.load(),
        )
    }

    @Test
    fun `every edge survives a save and a fresh read`() {
        for (edge in DockEdge.entries) {
            DockPlacement.save(edge, 0.37f)
            val back = DockPlacement.load()
            assertNotNull("$edge did not come back at all", back)
            assertEquals(edge, back!!.edge)
            assertEquals(0.37f, back.along, 1e-6f)
        }
    }

    @Test
    fun `the saved file is the one dock_properties beside settings`() {
        DockPlacement.save(DockEdge.BOTTOM, 0.2f)
        // Named explicitly: the whole feature is invisible if this lands in a
        // directory the next launch does not read, and "it saved fine" is
        // exactly what a wrong-directory bug reports.
        assertTrue(File(dir, "dock.properties").isFile)
    }

    @Test
    fun `a fraction outside the edge is stored clamped, not rejected`() {
        DockPlacement.save(DockEdge.TOP, 4.2f)
        assertEquals(1f, DockPlacement.load()!!.along, 1e-6f)
        DockPlacement.save(DockEdge.TOP, -3f)
        assertEquals(0f, DockPlacement.load()!!.along, 1e-6f)
    }

    @Test
    fun `a corrupt file reads as null rather than throwing at startup`() {
        // A half-written file after a power cut must not stop the app booting.
        File(dir, "dock.properties").writeText("edge=DIAGONAL\nalong=banana\n")
        assertNull(DockPlacement.load())
    }

    // ── Links 1 and 3: the geometry, and that they invert each other ────────

    @Test
    fun `a dropped tab comes back at the same point on the same edge`() {
        for (wa in screens) {
            for (edge in DockEdge.entries) {
                // Drop the tab a third of the way along the edge — an
                // arbitrary point that is neither the centre (which the
                // default would also produce, hiding a total failure to
                // persist) nor an extreme (which the clamp would rescue).
                val dropped = droppedAt(edge, fraction = 1f / 3f, wa = wa)

                val (foundEdge, along) = nearestEdge(dropped, wa)
                assertEquals("wrong edge chosen on $wa", edge, foundEdge)

                // Through the file, exactly as a real quit-and-relaunch does.
                DockPlacement.save(foundEdge, along)
                val loaded = DockPlacement.load()!!
                val placed = dockBounds(loaded.edge, loaded.along, wa)

                // ±1px: `along` is a Float and dockBounds truncates to whole
                // pixels, so a third of 2120 can land a pixel either side.
                // Measured, not assumed — the tolerance is here because the
                // 4K case actually exercises it.
                assertEquals(
                    "$edge on $wa came back in the wrong place",
                    centreAlong(edge, dropped).toDouble(),
                    centreAlong(edge, placed).toDouble(),
                    1.0,
                )
            }
        }
    }

    @Test
    fun `the restored tab is flush against its edge`() {
        // The bookmark is a bookmark because it touches the screen edge. A
        // placement that is merely NEAR the edge reads as a stray window.
        for (wa in screens) {
            assertEquals(wa.x, dockBounds(DockEdge.LEFT, 0.5f, wa).x)
            dockBounds(DockEdge.RIGHT, 0.5f, wa).let {
                assertEquals(wa.x + wa.width, it.x + it.width)
            }
            assertEquals(wa.y, dockBounds(DockEdge.TOP, 0.5f, wa).y)
            dockBounds(DockEdge.BOTTOM, 0.5f, wa).let {
                assertEquals(wa.y + wa.height, it.y + it.height)
            }
        }
    }

    @Test
    fun `the extremes of an edge stay wholly on screen`() {
        // Dragging the tab into a corner saves a fraction of 0 or 1. Placing
        // its centre there would put half the tab past the end of the edge.
        for (wa in screens) {
            for (edge in DockEdge.entries) {
                for (along in listOf(0f, 1f)) {
                    val b = dockBounds(edge, along, wa)
                    assertTrue(
                        "$edge at $along ran off $wa: $b",
                        b.x >= wa.x && b.y >= wa.y &&
                            b.x + b.width <= wa.x + wa.width &&
                            b.y + b.height <= wa.y + wa.height,
                    )
                }
            }
        }
    }

    @Test
    fun `the same fraction is the same relative position on any screen`() {
        // Why a fraction is stored instead of pixels: change the monitor and
        // the tab is still two-thirds of the way down, not off the bottom.
        for (edge in DockEdge.entries) {
            val relative = screens.map { wa ->
                val b = dockBounds(edge, 0.67f, wa)
                val span = if (edge.isVertical) wa.height else wa.width
                val origin = if (edge.isVertical) wa.y else wa.x
                (centreAlong(edge, b) - origin).toDouble() / span
            }
            for (r in relative) assertEquals("$edge drifted between screens", 0.67, r, 0.01)
        }
    }

    @Test
    fun `a tab dropped in open space picks the edge it is nearest`() {
        // nearestEdge measures from the tab's centre to all four edges. A drop
        // in the middle of the desktop still has to produce a definite answer.
        val wa = laptop
        // Far left, vertically central -> LEFT.
        assertEquals(DockEdge.LEFT, nearestEdge(Rectangle(4, 500, 36, 96), wa).first)
        // Near the top, horizontally central -> TOP.
        assertEquals(DockEdge.TOP, nearestEdge(Rectangle(900, 2, 96, 36), wa).first)
        // Bottom-right corner: 1920x1040 is wider than tall, so the BOTTOM
        // edge is the nearer of the two by construction.
        assertEquals(DockEdge.BOTTOM, nearestEdge(Rectangle(1800, 1000, 96, 36), wa).first)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** The tab's position along its edge — y for a side edge, x for a top/bottom one. */
    private fun centreAlong(edge: DockEdge, r: Rectangle): Int =
        if (edge.isVertical) r.y + r.height / 2 else r.x + r.width / 2

    /**
     * A tab rectangle as it would sit at the instant the owner releases it:
     * hard against [edge], [fraction] of the way along it. Built from
     * [dockBounds] itself so the tab's own size never has to be duplicated
     * here — the drop point, not the size, is what this is varying.
     */
    private fun droppedAt(edge: DockEdge, fraction: Float, wa: Rectangle): Rectangle =
        dockBounds(edge, fraction, wa)
}
