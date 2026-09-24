package com.zmanimclock.app.feature.womensarea.model

import com.zmanimclock.app.feature.womensarea.model.WomensAreaLabels.hebrewName
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WomensAreaLabelsTest {

    // 15 Nissan 5786 = Thursday 2026-04-02 (OmerCountTest's anchor).
    private val fifteenNissan = LocalDate.of(2026, 4, 2)

    @Test
    fun `the Hebrew date is read off the daytime of the stored date`() {
        assertEquals("ט״ו ניסן תשפ״ו", WomensAreaLabels.hebrewDate(fifteenNissan))
        assertEquals("חמישי", WomensAreaLabels.weekdayName(fifteenNissan))
    }

    @Test
    fun `a night onah is the evening BEFORE the stored date`() {
        assertEquals(
            "ליל חמישי ט״ו ניסן — הערב של יום רביעי 1.4, אחרי השקיעה",
            WomensAreaLabels.onahTiming(fifteenNissan, Onah.NIGHT),
        )
        assertEquals("יום חמישי ט״ו ניסן (2.4)", WomensAreaLabels.onahTiming(fifteenNissan, Onah.DAY))
        // ליל ראשון is מוצאי שבת: its evening is Shabbat itself.
        assertEquals(
            "ליל ראשון י״ח ניסן — הערב של שבת 4.4, אחרי השקיעה",
            WomensAreaLabels.onahTiming(LocalDate.of(2026, 4, 5), Onah.NIGHT),
        )
    }

    @Test
    fun `names`() {
        assertEquals("עונה בינונית", VesetKind.ONAH_BEINONIT.hebrewName)
        assertEquals("בלילה", Onah.NIGHT.hebrewName)
    }
}
