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

    /**
     * The mock renders `SAMPLE_TIMES[kind] ?: "--:--"`, so a kind offered in
     * the picker with no sample time shows a literal "--:--" next to its name
     * — in the exact screen where the user is deciding whether that row is
     * worth a slot. PLAG_HAMINCHA_GRA reached the picker that way.
     */
    @Test
    fun `every offered zman has a sample time in the mock`() {
        val without = grouped.filter { it !in SAMPLE_TIMES }
        assertTrue(
            "these would render \"--:--\" in the preview: " + without.joinToString { it.name },
            without.isEmpty(),
        )
    }

    /**
     * The real widget sorts its rows by instant (`relevantTimedZmanim` ends in
     * `sortedBy { instant }`), and the mock claims to list them "in the order
     * the real widget lists them". So each group must be in time order, or the
     * preview is telling the user something the widget will not do.
     *
     * Groups are buckets by time of day rather than one continuous stream, so
     * the check is within a group, not across all of them.
     */
    @Test
    fun `each group is listed in the order the widget would render it`() {
        fun minutes(hhmm: String) =
            hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()

        ZMAN_GROUPS.forEach { (title, kinds) ->
            val times = kinds.map { it to minutes(SAMPLE_TIMES.getValue(it)) }
            times.zipWithNext { (aKind, a), (bKind, b) ->
                assertTrue(
                    "in group '$title', ${aKind.name} (${SAMPLE_TIMES[aKind]}) is listed before " +
                        "${bKind.name} (${SAMPLE_TIMES[bKind]}), but the widget sorts by time " +
                        "and would render them the other way round",
                    a <= b,
                )
            }
        }
    }

    /**
     * The two plag rows are the pair most likely to be reversed, because the
     * names give no clue which is earlier. The GRA one measures back from
     * shkia and the luach's from tzeit, so the GRA one is earlier on every
     * date, everywhere — and must be offered first.
     */
    @Test
    fun `the GRA plag is offered before the luach plag`() {
        val gra = grouped.indexOf(ZmanKind.PLAG_HAMINCHA_GRA)
        val luach = grouped.indexOf(ZmanKind.PLAG_HAMINCHA)
        assertTrue("both plag rows must be offered", gra >= 0 && luach >= 0)
        assertTrue("the GRA plag must come first — it is always the earlier one", gra < luach)
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
