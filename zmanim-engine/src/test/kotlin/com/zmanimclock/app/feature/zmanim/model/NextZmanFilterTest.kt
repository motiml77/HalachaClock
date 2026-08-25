package com.zmanimclock.app.feature.zmanim.model

import com.zmanimclock.app.feature.zmanim.engine.EngineLocation
import com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The "הזמן הבא" headline can be narrowed to a chosen set of zmanim.
 *
 * The app tracks ~19 zmanim and the headline used to walk through every one
 * of them in order. Someone who only watches ק"ש and שקיעה still had עלות,
 * משיכיר and הנץ announced first. With a filter, the headline jumps straight
 * to the next zman that person actually cares about.
 *
 * An EMPTY filter must keep the original behaviour exactly — it is the
 * default, so a regression here silently changes the headline for every user
 * who never opened the setting.
 */
class NextZmanFilterTest {

    private val jerusalem = EngineLocation("ירושלים", 31.778, 35.235, 0.0, "Asia/Jerusalem")
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val date = LocalDate.of(2026, 8, 5)
    private val day = MaranZmanimEngine().calculate(jerusalem, date)

    /** An instant on [date] at the given local wall-clock time. */
    private fun at(hour: Int, minute: Int) =
        date.atTime(hour, minute).atZone(zone).toInstant()

    @Test
    fun `with no filter the headline is simply the next zman`() {
        // 06:30 — the very next zman is one of the morning shma deadlines,
        // NOT שקיעה, because nothing has been narrowed.
        val next = nextRelevantZman(day, date, at(6, 30), null, emptySet())
        assertNotNull(next)
        assertEquals(ZmanKind.SOF_ZMAN_SHMA_MGA_72_ZMANIYOT, next!!.first)
    }

    @Test
    fun `narrowing to one zman skips everything before it`() {
        // The user's own example: at 06:30, with only ק"ש גר"א selected, the
        // headline should already read ק"ש — even though ק"ש מג"א (both
        // shitot) falls in between and would otherwise win.
        val next = nextRelevantZman(
            day, date, at(6, 30), null, setOf(ZmanKind.SOF_ZMAN_SHMA_GRA.name),
        )
        assertNotNull("a selected zman later today must still be found", next)
        assertEquals(ZmanKind.SOF_ZMAN_SHMA_GRA, next!!.first)
    }

    @Test
    fun `an evening selection is found from the afternoon`() {
        // 18:34 with ק"ש and שקיעה selected: ק"ש has passed, so שקיעה wins.
        // This is the exact case that must not come back null.
        val next = nextRelevantZman(
            day, date, at(18, 34), null,
            setOf(ZmanKind.SOF_ZMAN_SHMA_GRA.name, ZmanKind.SHKIA.name),
        )
        assertNotNull("שקיעה is still ahead at 18:34 — it must be the headline", next)
        assertEquals(ZmanKind.SHKIA, next!!.first)
    }

    @Test
    fun `the earliest SELECTED zman wins, not the earliest overall`() {
        val next = nextRelevantZman(
            day, date, at(6, 30), null,
            setOf(ZmanKind.SHKIA.name, ZmanKind.SOF_ZMAN_SHMA_GRA.name),
        )
        // Both are ahead at 06:30; ק"ש is earlier, so it is the one shown.
        assertEquals(ZmanKind.SOF_ZMAN_SHMA_GRA, next!!.first)
    }

    @Test
    fun `once every selected zman has passed the headline is empty`() {
        // Late at night with only morning zmanim selected there is genuinely
        // nothing left today — callers fall back to tomorrow.
        val next = nextRelevantZman(
            day, date, at(23, 30), null, setOf(ZmanKind.SOF_ZMAN_SHMA_GRA.name),
        )
        assertNull(next)
    }

    @Test
    fun `an unknown name in the filter is ignored, not treated as a match`() {
        // A selection saved by a newer build must not break an older one.
        val next = nextRelevantZman(
            day, date, at(18, 34), null, setOf("NOT_A_REAL_ZMAN", ZmanKind.SHKIA.name),
        )
        assertEquals(ZmanKind.SHKIA, next!!.first)
    }

    @Test
    fun `a filter that excludes chatzot layla suppresses the yesterday fallback`() {
        val yesterday = MaranZmanimEngine().calculate(jerusalem, date.minusDays(1))
        val justAfterMidnight = at(0, 20)

        // Unfiltered, yesterday's חצות לילה is the next thing.
        val unfiltered = nextRelevantZman(day, date, justAfterMidnight, yesterday, emptySet())
        assertEquals(ZmanKind.CHATZOT_LAYLA, unfiltered!!.first)

        // Narrowed to only הנץ, the same moment must skip it and pick הנץ.
        val filtered = nextRelevantZman(
            day, date, justAfterMidnight, yesterday, setOf(ZmanKind.HANETZ.name),
        )
        assertEquals(ZmanKind.HANETZ, filtered!!.first)
    }
}
