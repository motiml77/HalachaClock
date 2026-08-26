package com.zmanimclock.desktop.data

import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The alert list AS THE USER OPERATES IT: add one, edit it, turn it off,
 * delete it — through [DesktopZmanimService], the same calls the UI makes.
 *
 * The pane's buttons are thin wrappers over these four methods, so proving
 * them here proves the screen works without driving a mouse across a live
 * window. What this deliberately cannot prove is that the buttons are WIRED to
 * them; that is what the rendered shots and the live run are for.
 */
class AlertCrudTest {

    private fun service(vararg alerts: ZmanAlert) =
        DesktopZmanimService(DesktopPrefs(alerts = alerts.toList()))

    private fun alert(
        id: String = "a1",
        name: String = "לצאת לתפילה",
        kind: ZmanKind = ZmanKind.MINCHA_KETANA,
        offset: Int = -20,
        days: Int = ZmanAlert.EVERY_DAY,
        enabled: Boolean = true,
    ) = ZmanAlert(id, name, kind, offset, days, enabled)

    // ---- create -----------------------------------------------------------

    @Test
    fun `adding an alert puts it in the list`() {
        val svc = service()
        assertTrue(svc.prefs.alerts.isEmpty())

        svc.putAlert(alert())

        assertEquals(1, svc.prefs.alerts.size)
        assertEquals("לצאת לתפילה", svc.prefs.alerts.single().name)
    }

    @Test
    fun `two alerts can sit on the same zman with different offsets`() {
        val svc = service()
        svc.putAlert(alert(id = "a1", name = "להתכונן", kind = ZmanKind.SHKIA, offset = -30))
        svc.putAlert(alert(id = "a2", name = "הדלקה", kind = ZmanKind.SHKIA, offset = -18))

        assertEquals(2, svc.prefs.alerts.size)
        assertEquals(listOf(-30, -18), svc.prefs.alerts.map { it.offsetMinutes })
    }

    // ---- edit -------------------------------------------------------------

    /** putAlert is add-or-replace: the editor saves through the same call. */
    @Test
    fun `saving an edit replaces in place rather than duplicating`() {
        val svc = service(alert(id = "a1", offset = -20))

        svc.putAlert(svc.prefs.alerts.single().copy(name = "שם חדש", offsetMinutes = -5))

        assertEquals(1, svc.prefs.alerts.size)
        val saved = svc.prefs.alerts.single()
        assertEquals("שם חדש", saved.name)
        assertEquals(-5, saved.offsetMinutes)
        assertEquals("a1", saved.id)
    }

    @Test
    fun `changing the days keeps everything else`() {
        val svc = service(alert(id = "a1"))
        val onlyFriday = ZmanAlert.bitFor(DayOfWeek.FRIDAY)

        svc.putAlert(svc.prefs.alerts.single().copy(days = onlyFriday))

        val saved = svc.prefs.alerts.single()
        assertEquals(onlyFriday, saved.days)
        assertTrue(saved.firesOn(DayOfWeek.FRIDAY))
        assertFalse(saved.firesOn(DayOfWeek.SUNDAY))
        assertEquals("לצאת לתפילה", saved.name)
        assertEquals(-20, saved.offsetMinutes)
    }

    // ---- enable / disable -------------------------------------------------

    @Test
    fun `toggling off keeps the alert but disarms it`() {
        val svc = service(alert(id = "a1", enabled = true))

        svc.putAlert(svc.prefs.alerts.single().copy(enabled = false))

        assertEquals("it must stay in the list, just off", 1, svc.prefs.alerts.size)
        assertFalse(svc.prefs.alerts.single().enabled)

        svc.putAlert(svc.prefs.alerts.single().copy(enabled = true))
        assertTrue(svc.prefs.alerts.single().enabled)
    }

    // ---- delete -----------------------------------------------------------

    @Test
    fun `deleting removes exactly one and leaves the rest`() {
        val svc = service(alert(id = "a1"), alert(id = "a2"), alert(id = "a3"))

        svc.removeAlert("a2")

        assertEquals(listOf("a1", "a3"), svc.prefs.alerts.map { it.id })
    }

    @Test
    fun `deleting something already gone is not an error`() {
        val svc = service(alert(id = "a1"))
        svc.removeAlert("nope")
        assertEquals(1, svc.prefs.alerts.size)
    }

    // ---- ordering ---------------------------------------------------------

    /**
     * The list is a picture of the day in the order the day happens, so this
     * is ordering by RESOLVED CLOCK TIME — not by creation, not by name, and
     * not by the zman's own position before the offset is applied.
     */
    @Test
    fun `the list is ordered by when each alert will actually fire`() {
        val svc = service(
            alert(id = "a1", name = "ערבית", kind = ZmanKind.TZEIT_LECHUMRA, offset = 0),
            alert(id = "a2", name = "ותיקין", kind = ZmanKind.HANETZ, offset = 0),
            alert(id = "a3", name = "מנחה", kind = ZmanKind.MINCHA_KETANA, offset = 0),
        )

        val rows = svc.alertsInFiringOrder(LocalDate.of(2026, 8, 26))

        assertEquals(listOf("a2", "a3", "a1"), rows.map { it.alert.id })
        rows.forEach { assertNotNull("every one of these occurs daily", it.firesAt) }
    }

    /**
     * An offset can reorder two alerts relative to their own zmanim — which is
     * exactly why sorting on the zman rather than the resolved time would be
     * wrong.
     */
    @Test
    fun `a large offset moves an alert past one on a later zman`() {
        // Jerusalem, 26.8.2026: מנחה קטנה 16:28, שקיעה 19:10. Four hours
        // before שקיעה is 15:10 — earlier than מנחה קטנה — so the alert hung
        // off the LATER zman must sort FIRST. (The first draft of this test
        // used two hours and failed: 17:10 is still after 16:28. Worth
        // keeping the real numbers written down.)
        val svc = service(
            alert(id = "late-zman", kind = ZmanKind.SHKIA, offset = -240),
            alert(id = "early-zman", kind = ZmanKind.MINCHA_KETANA, offset = 0),
        )

        val rows = svc.alertsInFiringOrder(LocalDate.of(2026, 8, 26))

        assertEquals(listOf("late-zman", "early-zman"), rows.map { it.alert.id })
        assertEquals("15:10", rows.first().firesAt)
        assertEquals("16:28", rows.last().firesAt)
    }

    /**
     * A zman that does not occur — הדלקת נרות midweek — must keep its row and
     * say so. Hiding it would read as the alert having been thrown away.
     */
    @Test
    fun `an alert whose zman does not occur today keeps its row with no time`() {
        // 2026-08-26 is a Wednesday: no candle lighting.
        val wednesday = LocalDate.of(2026, 8, 26)
        val svc = service(
            alert(id = "a1", kind = ZmanKind.CANDLE_LIGHTING, offset = 0),
            alert(id = "a2", kind = ZmanKind.SHKIA, offset = 0),
        )

        val rows = svc.alertsInFiringOrder(wednesday)

        assertEquals(2, rows.size)
        val candle = rows.single { it.alert.id == "a1" }
        assertNull("no time, and not silently dropped", candle.firesAt)
        assertNotNull(rows.single { it.alert.id == "a2" }.firesAt)
        assertEquals("the timeless one sorts last", "a1", rows.last().alert.id)
    }

    // ---- persistence through the real file format -------------------------

    /**
     * The list goes through Properties, one record per alert. This is the
     * shape the file on disk actually takes, so an encoding change that broke
     * a name or a day mask would show up here rather than on a user's machine.
     */
    @Test
    fun `the whole list survives a save and load cycle`() {
        val alerts = listOf(
            alert(id = "a1", name = "ותיקין", kind = ZmanKind.HANETZ, offset = 0),
            alert(
                id = "a2",
                name = "הדלקת נרות | מוקדם",
                kind = ZmanKind.SHKIA,
                offset = -18,
                days = ZmanAlert.bitFor(DayOfWeek.FRIDAY),
                enabled = false,
            ),
        )

        val p = java.util.Properties()
        alerts.forEach { p["alert.${it.id}"] = it.encode() }

        val restored = p.stringPropertyNames()
            .filter { it.startsWith("alert.") }
            .sorted()
            .mapNotNull { ZmanAlert.decode(it.removePrefix("alert."), p.getProperty(it)) }

        assertEquals(alerts, restored)
    }
}
