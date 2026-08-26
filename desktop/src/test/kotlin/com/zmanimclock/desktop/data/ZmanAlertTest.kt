package com.zmanimclock.desktop.data

import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/**
 * The alert model: what it means, what survives a save, and what an edit does.
 *
 * These are the operations the user actually performs — create one, turn it
 * off, change its days, delete it — pinned as behaviour rather than assumed
 * from the UI. Everything here is pure data; the scheduling half is covered by
 * ReminderSchedulerTest and the layout by RenderShotTest.
 */
class ZmanAlertTest {

    private fun alert(
        id: String = "a1",
        name: String = "לצאת לתפילה",
        kind: ZmanKind = ZmanKind.SHKIA,
        offset: Int = -20,
        days: Int = ZmanAlert.EVERY_DAY,
        enabled: Boolean = true,
    ) = ZmanAlert(id, name, kind, offset, days, enabled)

    // ---- weekdays ---------------------------------------------------------

    /**
     * The bit order is the Android build's — Sunday first — so the two never
     * have to be reasoned about differently. Pinned explicitly because
     * java.time counts Monday=1..Sunday=7 and the obvious `ordinal` would
     * silently produce a mask shifted by one.
     */
    @Test
    fun `sunday is bit zero and saturday is bit six`() {
        assertEquals(0, ZmanAlert.bitIndex(DayOfWeek.SUNDAY))
        assertEquals(1, ZmanAlert.bitIndex(DayOfWeek.MONDAY))
        assertEquals(6, ZmanAlert.bitIndex(DayOfWeek.SATURDAY))
    }

    @Test
    fun `every day fires on all seven`() {
        val a = alert(days = ZmanAlert.EVERY_DAY)
        assertTrue(a.isEveryDay)
        DayOfWeek.entries.forEach { assertTrue("$it", a.firesOn(it)) }
        assertNull("every day is not worth spelling out on the row", a.daysDescription)
    }

    @Test
    fun `a narrowed selection fires only on the days chosen`() {
        // Sunday + Wednesday
        val a = alert(days = ZmanAlert.bitFor(DayOfWeek.SUNDAY) or ZmanAlert.bitFor(DayOfWeek.WEDNESDAY))
        assertTrue(a.firesOn(DayOfWeek.SUNDAY))
        assertTrue(a.firesOn(DayOfWeek.WEDNESDAY))
        assertFalse(a.firesOn(DayOfWeek.MONDAY))
        assertFalse(a.firesOn(DayOfWeek.SATURDAY))
        assertFalse(a.isEveryDay)
        assertEquals("א׳، ד׳", a.daysDescription)
    }

    /**
     * THE BUG THE ANDROID BUILD SHIPPED, pinned so this one cannot repeat it:
     * an empty mask there was read as "unset" and rang every day — the exact
     * opposite of what had just been asked for.
     */
    @Test
    fun `an empty mask fires on no day at all, never on every day`() {
        val a = alert(days = 0)
        DayOfWeek.entries.forEach { assertFalse("$it", a.firesOn(it)) }
        assertFalse(a.isEveryDay)
    }

    // ---- persistence ------------------------------------------------------

    @Test
    fun `a full round trip preserves every field`() {
        val original = alert(
            name = "ערבית",
            kind = ZmanKind.TZEIT_LECHUMRA,
            offset = 15,
            days = ZmanAlert.bitFor(DayOfWeek.FRIDAY),
            enabled = false,
        )
        val restored = ZmanAlert.decode(original.id, original.encode())
        assertEquals(original, restored)
    }

    /**
     * Names are free text, and the separator is a character a person can type.
     * The name is serialised LAST with a limited split for exactly this.
     */
    @Test
    fun `a name containing the separator survives`() {
        val original = alert(name = "מנחה | ערבית")
        assertEquals(original, ZmanAlert.decode(original.id, original.encode()))
    }

    @Test
    fun `a record from before weekdays existed reads as every day`() {
        // The old four-field shape: enabled|offset|KIND|name
        val restored = ZmanAlert.decode("a3", "true|-18|SHKIA|הדלקת נרות")
        assertNotNull(restored)
        assertEquals("הדלקת נרות", restored!!.name)
        assertEquals(-18, restored.offsetMinutes)
        assertTrue("an alert that used to fire daily must keep doing so", restored.isEveryDay)
    }

    @Test
    fun `a corrupt record is dropped rather than throwing`() {
        assertNull(ZmanAlert.decode("a1", ""))
        assertNull(ZmanAlert.decode("a1", "true|0"))
        assertNull(ZmanAlert.decode("a1", "true|0|NOT_A_ZMAN|127|x"))
    }

    @Test
    fun `an absurd offset is clamped rather than trusted`() {
        val restored = ZmanAlert.decode("a1", "true|99999|SHKIA|127|x")
        assertEquals(ZmanAlert.MAX_OFFSET_MINUTES, restored!!.offsetMinutes)
    }

    // ---- ids --------------------------------------------------------------

    @Test
    fun `a new id never collides with one already in use`() {
        val existing = listOf(alert(id = "a1"), alert(id = "a2"), alert(id = "a5"))
        val fresh = ZmanAlert.newId(existing)
        assertEquals("a3", fresh)
        assertTrue(existing.none { it.id == fresh })
    }

    @Test
    fun `the first id of an empty list is stable`() {
        assertEquals("a1", ZmanAlert.newId(emptyList()))
    }

    // ---- wording ----------------------------------------------------------

    @Test
    fun `the description says which side of the zman it falls on`() {
        assertEquals("20 דקות לפני שקיעה", alert(offset = -20).description)
        assertEquals("20 דקות אחרי שקיעה", alert(offset = 20).description)
        assertEquals("בדיוק בשקיעה", alert(offset = 0).description)
    }
}
