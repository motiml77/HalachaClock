package com.zmanimclock.app.feature.zmanim.model

import com.zmanimclock.app.feature.zmanim.engine.EngineLocation
import com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

/**
 * הנץ מישור is a DISPLAY row and must stay one.
 *
 * The request was explicitly "show it without affecting anything else", so
 * these tests pin the "anything else" half: adding the row must not move a
 * single computed value, and it must not show the same instant twice.
 */
class HanetzMishorTest {

    private val jerusalem = EngineLocation("Jerusalem", 31.7683, 35.2137, 0.0, "Asia/Jerusalem")
    private val date = LocalDate.of(2026, 8, 7)
    private val engine = MaranZmanimEngine()

    /** A visible netz a few minutes after the mishor one, as terrain produces. */
    private fun visible(day: LocalDate) =
        engine.calculate(jerusalem, day).hanetzMishor!!.plus(Duration.ofMinutes(5))

    @Test
    fun `it reports the sea-level sunrise, not the visible one`() {
        val day = engine.calculate(jerusalem, date, visibleSunrise = visible(date))
        assertEquals(day.hanetzMishor, day.instantOf(ZmanKind.HANETZ_MISHOR))
        assertEquals(day.hanetzVisible, day.instantOf(ZmanKind.HANETZ))
    }

    @Test
    fun `with terrain data both rows appear, five minutes apart`() {
        val day = engine.calculate(jerusalem, date, visibleSunrise = visible(date))
        val rows = day.relevantTimedZmanim(date)
        val mishor = rows.firstOrNull { it.first == ZmanKind.HANETZ_MISHOR }
        val netz = rows.firstOrNull { it.first == ZmanKind.HANETZ }
        assertNotNull("הנץ מישור must be listed when a visible netz exists", mishor)
        assertNotNull(netz)
        assertEquals(5, Duration.between(mishor!!.second, netz!!.second).toMinutes())
        // Sorted by time, so the astronomical one comes first.
        assertTrue(rows.indexOf(mishor) < rows.indexOf(netz))
    }

    @Test
    fun `a visible netz inside the same minute is still suppressed`() {
        // Caught on a device, not by the first version of this test: Jerusalem
        // HAS ChaiTables data, but its visible netz falls in the same minute
        // as the mishor one, so gating on "terrain data exists" printed
        // 05:58 twice. What matters is whether the rendered minute differs.
        val mishor = engine.calculate(jerusalem, date).hanetzMishor!!
        val sameMinute = engine.calculate(
            jerusalem, date, visibleSunrise = mishor.plus(Duration.ofSeconds(8)),
        )
        assertTrue(
            "a visible netz 8s later renders as the same minute and must not add a row",
            sameMinute.relevantTimedZmanim(date).none { it.first == ZmanKind.HANETZ_MISHOR },
        )
        // One minute apart is a different number on screen, so it does appear.
        val nextMinute = engine.calculate(
            jerusalem, date, visibleSunrise = mishor.plus(Duration.ofMinutes(1)),
        )
        assertTrue(
            nextMinute.relevantTimedZmanim(date).any { it.first == ZmanKind.HANETZ_MISHOR },
        )
    }

    @Test
    fun `without terrain data the row is suppressed instead of duplicating הנץ`() {
        // HANETZ already falls back to the mishor time, so listing both would
        // print one moment twice — which reads as a bug, not as information.
        val day = engine.calculate(jerusalem, date)
        val rows = day.relevantTimedZmanim(date)
        assertEquals(day.hanetzMishor, day.instantOf(ZmanKind.HANETZ))
        assertTrue(
            "הנץ מישור must not be listed when it equals הנץ",
            rows.none { it.first == ZmanKind.HANETZ_MISHOR },
        )
    }

    @Test
    fun `adding the row moves no other zman by even a millisecond`() {
        // The grid was always built on the mishor day, so this row is pure
        // display. Proven by comparing every other zman with and without a
        // visible netz present — nothing but the הנץ rows may differ.
        val plain = engine.calculate(jerusalem, date)
        val withTerrain = engine.calculate(jerusalem, date, visibleSunrise = visible(date))
        for (kind in ZmanKind.entries) {
            if (kind == ZmanKind.HANETZ || kind == ZmanKind.HANETZ_MISHOR) continue
            assertEquals(
                "$kind moved when a visible netz was supplied",
                plain.instantOf(kind),
                withTerrain.instantOf(kind),
            )
        }
        assertEquals(plain.shaahZmanisGra, withTerrain.shaahZmanisGra)
        assertEquals(plain.hanetzMishor, withTerrain.hanetzMishor)
    }

    /**
     * The inverse of what this test used to assert.
     *
     * It previously pinned "elevation does not reach the sunrise calculation
     * at all", which was the mishor doctrine stated as a fact. That doctrine
     * turned out to be wrong for this luach — see MaranZmanimEngine's own
     * measured table against royzmanim — so the test now pins the behaviour
     * that replaced it. Kept rather than deleted precisely because it is the
     * hinge: if anyone ever flips the convention back, this fails loudly.
     */
    @Test
    fun `elevation moves the halachic day but never the displayed mishor netz`() {
        val atSeaLevel = engine.calculate(jerusalem, date)
        val at800m = engine.calculate(jerusalem.copy(elevationMeters = 800.0), date)

        // הנץ מישורי is sea-level BY DEFINITION and must be immune.
        assertEquals(
            "the mishor netz row must not move with elevation",
            atSeaLevel.hanetzMishor,
            at800m.hanetzMishor,
        )
        // שקיעה מישורית likewise — it is the other half of the same pair.
        assertEquals(
            "the mishor shkia row must not move with elevation",
            atSeaLevel.shkiaMishor,
            at800m.shkiaMishor,
        )

        // The halachic שקיעה, and with it the whole seasonal grid, DOES move.
        assertNotEquals(
            "shkia must follow the city's height",
            atSeaLevel.shkia,
            at800m.shkia,
        )
        assertTrue(
            "at 800m the sun sets later, not earlier",
            at800m.shkia!!.isAfter(atSeaLevel.shkia!!),
        )
        assertNotEquals(
            "the seasonal hour is built on the elevation day",
            atSeaLevel.shaahZmanisGra,
            at800m.shaahZmanisGra,
        )
    }

    /**
     * The size of the shift, not merely its direction — a sanity rail against
     * a units or refraction slip that would still move things the right way.
     * Jerusalem at 800 m: the standard horizon dip puts sunset about 4½
     * minutes late, and פניני הלכה (זמני השבת א,ט) independently gives
     * "קרוב לחמש דקות" at that height.
     */
    @Test
    fun `the Jerusalem shift is about four and a half minutes`() {
        val atSeaLevel = engine.calculate(jerusalem, date)
        val at800m = engine.calculate(jerusalem.copy(elevationMeters = 800.0), date)
        val shiftSeconds = java.time.Duration
            .between(atSeaLevel.shkia, at800m.shkia).seconds
        assertTrue(
            "expected roughly 4-5 minutes at 800m, got ${shiftSeconds}s",
            shiftSeconds in 240..330,
        )
    }
}
