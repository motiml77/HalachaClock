package com.zmanimclock.app.ui.components

import com.zmanimclock.app.feature.widget.WidgetPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The selection rules behind the zman checklists.
 *
 * Two invariants matter enough to pin:
 *  - the widget picker can never overshoot [WidgetPrefs.MAX_ZMANIM], no
 *    matter what sequence of toggles arrives;
 *  - the "הזמן הבא" filter's empty set keeps meaning "all zmanim" — in
 *    particular, unchecking the last zman must return to empty rather than
 *    being blocked or collapsing to some default.
 */
class ZmanChecklistLogicTest {

    // ---- capped (widget picker) ----

    @Test
    fun `capped toggle adds a missing name`() {
        assertEquals(
            listOf("A", "B"),
            ZmanChecklistLogic.toggleCapped(listOf("A"), "B", max = 8),
        )
    }

    @Test
    fun `capped toggle removes a present name`() {
        assertEquals(
            listOf("A", "C"),
            ZmanChecklistLogic.toggleCapped(listOf("A", "B", "C"), "B", max = 8),
        )
    }

    @Test
    fun `capped toggle preserves the order the user picked in`() {
        var selection = emptyList<String>()
        listOf("SHKIA", "HANETZ", "CHATZOT").forEach {
            selection = ZmanChecklistLogic.toggleCapped(selection, it, max = 8)
        }
        assertEquals(listOf("SHKIA", "HANETZ", "CHATZOT"), selection)
    }

    @Test
    fun `capped toggle refuses to add beyond the cap`() {
        val full = List(WidgetPrefs.MAX_ZMANIM) { "Z$it" }
        val after = ZmanChecklistLogic.toggleCapped(full, "ONE_MORE", WidgetPrefs.MAX_ZMANIM)
        assertEquals(full, after)
    }

    @Test
    fun `at the cap, removing still works — that is how the user frees a slot`() {
        val full = List(WidgetPrefs.MAX_ZMANIM) { "Z$it" }
        val after = ZmanChecklistLogic.toggleCapped(full, "Z0", WidgetPrefs.MAX_ZMANIM)
        assertEquals(full - "Z0", after)
        assertTrue(after.size < WidgetPrefs.MAX_ZMANIM)
    }

    // ---- uncapped (the "הזמן הבא" filter) ----

    @Test
    fun `uncapped toggle flips membership both ways`() {
        val once = ZmanChecklistLogic.toggleUncapped(emptySet(), "SHKIA")
        assertEquals(setOf("SHKIA"), once)
        assertEquals(emptySet<String>(), ZmanChecklistLogic.toggleUncapped(once, "SHKIA"))
    }

    @Test
    fun `unchecking the last zman returns the filter to empty, which means all`() {
        // Empty must remain reachable: it is the "כל הזמנים" state, and the
        // master row in settings relies on selected.isEmpty() to show as
        // checked again after this.
        val after = ZmanChecklistLogic.toggleUncapped(setOf("HANETZ"), "HANETZ")
        assertTrue(after.isEmpty())
    }
}
