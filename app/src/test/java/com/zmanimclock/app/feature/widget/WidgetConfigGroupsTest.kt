package com.zmanimclock.app.feature.widget

import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget picker groups the zmanim by time of day instead of listing all
 * 19 flat. That grouping is a hand-written list, which means a ZmanKind added
 * later would simply not appear in the picker — with nothing failing, and no
 * way for the user to tell the option is missing rather than absent by
 * design. These tests make that a build failure instead.
 */
class WidgetConfigGroupsTest {

    private val grouped = ZMAN_GROUPS.flatMap { (_, kinds) -> kinds }

    @Test
    fun `every zman kind appears in exactly one group`() {
        val missing = ZmanKind.entries.filter { it !in grouped }
        assertTrue(
            "these kinds are not offered in the widget picker: " +
                missing.joinToString { it.name },
            missing.isEmpty(),
        )
    }

    @Test
    fun `no zman kind is listed twice`() {
        val duplicates = grouped.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue(
            "listed in more than one group: " + duplicates.joinToString { it.name },
            duplicates.isEmpty(),
        )
    }

    @Test
    fun `the groups cover the enum exactly`() {
        assertEquals(ZmanKind.entries.size, grouped.size)
        assertEquals(ZmanKind.entries.toSet(), grouped.toSet())
    }

    @Test
    fun `every group has a title and at least one zman`() {
        ZMAN_GROUPS.forEach { (title, kinds) ->
            assertTrue("a group has a blank title", title.isNotBlank())
            assertTrue("group '$title' is empty", kinds.isNotEmpty())
        }
    }

    @Test
    fun `the default widget selection is a real, offered selection`() {
        // DEFAULT_SELECTION is what a brand-new widget shows before the user
        // touches anything, so every entry must both resolve to a real kind
        // and be reachable in the picker.
        val defaults = WidgetPrefs.DEFAULT_SELECTION.map {
            ZmanKind.fromNameOrNull(it) ?: error("DEFAULT_SELECTION has an unknown kind: $it")
        }
        defaults.forEach { kind ->
            assertTrue("default '${kind.name}' is not in any picker group", kind in grouped)
        }
        assertTrue(
            "the default selection exceeds the widget's own row limit",
            defaults.size <= WidgetPrefs.MAX_ZMANIM,
        )
    }
}
