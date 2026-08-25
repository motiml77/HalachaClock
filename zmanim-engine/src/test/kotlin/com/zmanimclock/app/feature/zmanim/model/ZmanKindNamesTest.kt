package com.zmanimclock.app.feature.zmanim.model

import com.zmanimclock.app.feature.zmanim.engine.EngineLocation
import com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine
import com.zmanimclock.app.feature.zmanim.format.asZmanTimeOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * [ZmanKind.name] carries two jobs at once, and the MGA pair once failed at
 * both. It is the identifier a reader trusts when deciding which shita a row
 * shows — and it is written VERBATIM into saved alarms (`alarm.zmanId`), each
 * widget's selection and the next-zman filter.
 *
 * The pair used to be called _MGA and _MGA_72, where the constant whose name
 * ended in 72 was in fact the 16.1°-depression reading and the unsuffixed one
 * was the 72-zmaniyot shita. Reading the name instead of the mapping is how a
 * wrong row reached docs/VERIFICATION_TABLES.md. This test pins the two halves
 * that have to agree from here on: the NAME against the value and label it
 * goes with, and every RETIRED name against the constant that means today what
 * it meant when a user saved it.
 */
class ZmanKindNamesTest {

    private val jerusalem = EngineLocation("ירושלים", 31.778, 35.235, 0.0, "Asia/Jerusalem")
    private val zone = ZoneId.of("Asia/Jerusalem")

    // Deep winter, where the gap between the two shitot is far too wide to be
    // read as rounding — the same day the pair was checked against Hebcal.
    private val date = LocalDate.of(2026, 12, 15)
    private val day = MaranZmanimEngine().calculate(jerusalem, date)

    private fun t(kind: ZmanKind) = day.instantOf(kind)?.asZmanTimeOrNull(zone)

    @Test
    fun `the shita in the name is the shita in the value`() {
        assertEquals(day.sofZmanShmaMga, day.instantOf(ZmanKind.SOF_ZMAN_SHMA_MGA_72_ZMANIYOT))
        assertEquals(day.sofZmanShmaMga16, day.instantOf(ZmanKind.SOF_ZMAN_SHMA_MGA_16_1_DEG))
        assertEquals(day.sofZmanTfilaMga, day.instantOf(ZmanKind.SOF_ZMAN_TFILA_MGA_72_ZMANIYOT))
        assertEquals(day.sofZmanTfilaMga16, day.instantOf(ZmanKind.SOF_ZMAN_TFILA_MGA_16_1_DEG))
    }

    @Test
    fun `the shita in the name is the shita on the label`() {
        assertTrue(ZmanKind.SOF_ZMAN_SHMA_MGA_72_ZMANIYOT.hebrewName.contains("72"))
        assertTrue(ZmanKind.SOF_ZMAN_TFILA_MGA_72_ZMANIYOT.hebrewName.contains("72"))
        assertTrue(ZmanKind.SOF_ZMAN_SHMA_MGA_16_1_DEG.hebrewName.contains("16.1"))
        assertTrue(ZmanKind.SOF_ZMAN_TFILA_MGA_16_1_DEG.hebrewName.contains("16.1"))
    }

    @Test
    fun `Jerusalem 15 December 2026 - the two MGA readings are minutes apart`() {
        // Hebcal for the same day and place gives sofZmanShmaMGA16Point1 as
        // 08:23:57. Nine minutes separate the shitot: a row labelled with one
        // and computed from the other is plainly wrong, not merely imprecise.
        assertEquals("08:23", t(ZmanKind.SOF_ZMAN_SHMA_MGA_16_1_DEG))
        assertEquals("08:32", t(ZmanKind.SOF_ZMAN_SHMA_MGA_72_ZMANIYOT))
    }

    @Test
    fun `an alarm saved under a retired name still points at the same zman`() {
        assertEquals(
            ZmanKind.SOF_ZMAN_SHMA_MGA_72_ZMANIYOT,
            ZmanKind.fromNameOrNull("SOF_ZMAN_SHMA_MGA"),
        )
        assertEquals(
            ZmanKind.SOF_ZMAN_SHMA_MGA_16_1_DEG,
            ZmanKind.fromNameOrNull("SOF_ZMAN_SHMA_MGA_72"),
        )
        assertEquals(
            ZmanKind.SOF_ZMAN_TFILA_MGA_72_ZMANIYOT,
            ZmanKind.fromNameOrNull("SOF_ZMAN_TFILA_MGA"),
        )
        assertEquals(
            ZmanKind.SOF_ZMAN_TFILA_MGA_16_1_DEG,
            ZmanKind.fromNameOrNull("SOF_ZMAN_TFILA_MGA_72"),
        )
    }

    @Test
    fun `every current name still resolves to itself`() {
        for (kind in ZmanKind.entries) {
            assertEquals(kind, ZmanKind.fromNameOrNull(kind.name))
        }
    }

    @Test
    fun `a name that never existed resolves to nothing`() {
        assertNull(ZmanKind.fromNameOrNull("SOF_ZMAN_SHMA_MGA_90"))
        assertNull(ZmanKind.fromNameOrNull(""))
    }

    @Test
    fun `a stored selection is read back under todays names`() {
        val saved = listOf(
            "SOF_ZMAN_SHMA_MGA_72", "SHKIA", "NOT_A_REAL_ZMAN", "SOF_ZMAN_TFILA_MGA",
        )
        assertEquals(
            listOf(
                ZmanKind.SOF_ZMAN_SHMA_MGA_16_1_DEG.name,
                ZmanKind.SHKIA.name,
                ZmanKind.SOF_ZMAN_TFILA_MGA_72_ZMANIYOT.name,
            ),
            ZmanKind.canonicalNames(saved),
        )
    }

    @Test
    fun `a retired name is never reused for a different zman`() {
        // The one way this scheme can break: a future constant taking a name
        // that is already a retired key, which would silently repoint every
        // alarm saved under the old meaning at the new zman.
        val retired = setOf(
            "SOF_ZMAN_SHMA_MGA", "SOF_ZMAN_SHMA_MGA_72",
            "SOF_ZMAN_TFILA_MGA", "SOF_ZMAN_TFILA_MGA_72",
        )
        assertEquals(emptySet<String>(), retired intersect ZmanKind.entries.map { it.name }.toSet())
    }

    @Test
    fun `the next-zman filter honours a selection saved under a retired name`() {
        // The filter is a set of stored name strings, so it is the one place
        // a rename could silently narrow the headline to nothing.
        val august = LocalDate.of(2026, 8, 5)
        val summerDay = MaranZmanimEngine().calculate(jerusalem, august)
        val at630 = august.atTime(6, 30).atZone(zone).toInstant()

        val next = nextRelevantZman(
            summerDay, august, at630, null, setOf("SOF_ZMAN_SHMA_MGA_72"),
        )
        assertEquals(ZmanKind.SOF_ZMAN_SHMA_MGA_16_1_DEG, next?.first)
    }
}
