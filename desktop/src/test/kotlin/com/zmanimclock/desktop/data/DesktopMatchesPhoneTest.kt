package com.zmanimclock.desktop.data

import com.zmanimclock.app.feature.location.CityCatalog
import com.zmanimclock.app.feature.zmanim.engine.EngineLocation
import com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine
import com.zmanimclock.app.feature.zmanim.format.asZmanTime
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.feature.zmanim.model.hebrewNameOf
import com.zmanimclock.app.feature.zmanim.model.relevantTimedZmanim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The phone and the computer must show THE SAME TIMES. Pinned, not assumed.
 *
 * Sharing :zmanim-engine makes agreement likely, not certain. The desktop
 * still owns its own presentation layer — [DesktopZmanimService.view] decides
 * which rows become [ZmanRow]s, what name each carries, and how each instant
 * becomes a string. Any of those could drift from Android without a single
 * engine test noticing: a row could be filtered out, re-sorted, given a
 * different label, or formatted with a different pattern.
 *
 * So this reproduces the Android display path directly from the engine —
 * `relevantTimedZmanim(date)` -> `kind.hebrewName` / `instant.asZmanTime(zone)`
 * — and asserts the desktop's own view is identical to it, row for row, name
 * for name, character for character.
 *
 * The plag rows are the reason this test exists. They were added late, they
 * are the two rows most likely to be dropped or reordered by a hand-written
 * list, and their whole point is that a user can compare two shitot side by
 * side. A build where the phone shows both and the computer shows one is the
 * exact failure this catches.
 */
class DesktopMatchesPhoneTest {

    private val engine = MaranZmanimEngine()

    /**
     * Deliberately spread: latitude, elevation, season, and both DST edges.
     * These are cities.json IDs, which use underscores — a typo here would
     * otherwise be invisible, because the service quietly falls back to
     * Jerusalem for an unknown ID and the whole test would pass while
     * comparing Jerusalem against itself six times. [serviceFor] asserts the
     * ID actually resolved.
     */
    private val cityIds = listOf(
        "ירושלים", "תל_אביב-בת_ים-חולון", "חיפה", "באר_שבע", "צפת", "אילת",
    )
    private val dates = listOf(
        LocalDate.of(2026, 1, 15),   // deep winter
        LocalDate.of(2026, 3, 27),   // DST begins
        LocalDate.of(2026, 6, 21),   // solstice
        LocalDate.of(2026, 8, 23),   // the plag date checked against yeshiva.org.il
        LocalDate.of(2026, 10, 25),  // DST ends
        LocalDate.of(2026, 12, 15),  // shortest days
        LocalDate.of(2028, 2, 29),   // Gregorian leap day
    )

    private fun serviceFor(cityId: String): DesktopZmanimService {
        val service = DesktopZmanimService(DesktopPrefs(cityId = cityId))
        assertEquals(
            "'$cityId' is not a cities.json ID — the service fell back to its default, " +
                "which would make every comparison below compare a city against itself",
            cityId,
            service.city.id,
        )
        return service
    }

    /** Exactly what the Android list shows, derived from the engine alone. */
    private fun phoneRows(cityId: String, date: LocalDate): List<Pair<String, String>> {
        val c = CityCatalog.byId(cityId) ?: error("unknown city $cityId")
        val zone = ZoneId.of(c.timeZoneId)
        val loc = EngineLocation(c.nameHebrew, c.latitude, c.longitude, 0.0, c.timeZoneId)
        val day = engine.calculate(loc, date)
        return day.relevantTimedZmanim(date)
            // hebrewNameOf, not kind.hebrewName: the netz row is renamed per
            // day depending on whether it is the visible or the mishor sunrise,
            // and that rule is shared. If one platform ever stops calling it,
            // this is where it shows up.
            .map { (kind, instant) -> day.hebrewNameOf(kind) to instant.asZmanTime(zone) }
    }

    @Test
    fun `the desktop lists exactly the rows the phone lists, in the same order`() {
        var compared = 0
        for (cityId in cityIds) {
            val service = serviceFor(cityId)
            for (date in dates) {
                val phone = phoneRows(cityId, date)
                val desktop = service.view(date).rows.map { it.name to it.time }
                assertEquals("$cityId $date", phone, desktop)
                assertTrue("$cityId $date produced no rows at all", phone.isNotEmpty())
                compared += phone.size
            }
        }
        // A silently empty comparison would pass every assertion above.
        assertTrue("expected hundreds of rows, compared $compared", compared > 400)
    }

    @Test
    fun `both plag rows reach the desktop, GRA first, with the same labels as the phone`() {
        for (cityId in cityIds) {
            val service = serviceFor(cityId)
            for (date in dates) {
                val rows = service.view(date).rows
                val gra = rows.indexOfFirst { it.kind == ZmanKind.PLAG_HAMINCHA_GRA }
                val luach = rows.indexOfFirst { it.kind == ZmanKind.PLAG_HAMINCHA }

                assertTrue("$cityId $date: the desktop is missing the GRA plag row", gra >= 0)
                assertTrue("$cityId $date: the desktop is missing the luach plag row", luach >= 0)
                assertTrue("$cityId $date: the GRA plag must be listed first", gra < luach)

                // The name the user reads has to say WHICH shita it is; two
                // rows both called "פלג המנחה" would be worse than one.
                assertEquals(ZmanKind.PLAG_HAMINCHA_GRA.hebrewName, rows[gra].name)
                assertEquals(ZmanKind.PLAG_HAMINCHA.hebrewName, rows[luach].name)
                assertTrue(
                    "$cityId $date: the two plag rows are indistinguishable by name",
                    rows[gra].name != rows[luach].name,
                )
                // They are different shitot, so they must not collapse onto the
                // same displayed minute either.
                assertTrue(
                    "$cityId $date: both plag rows render ${rows[gra].time}",
                    rows[gra].time != rows[luach].time,
                )
            }
        }
    }

    /**
     * The desktop's own zman list must not lose a kind the engine offers. If a
     * row is deliberately unavailable there — as the ChaiTables visible-sunrise
     * layer currently is — it must be missing on BOTH platforms for the same
     * date, never on one alone.
     */
    @Test
    fun `no zman kind is shown on one platform and hidden on the other`() {
        for (cityId in cityIds) {
            val service = serviceFor(cityId)
            for (date in dates) {
                val phone = phoneRows(cityId, date).map { it.first }.toSet()
                val desktop = service.view(date).rows.map { it.name }.toSet()
                assertEquals(
                    "$cityId $date: only on the phone ${phone - desktop}, " +
                        "only on the desktop ${desktop - phone}",
                    phone,
                    desktop,
                )
            }
        }
    }
}
