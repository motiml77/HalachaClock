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
    fun `the tevila is after tzeit of the 7th day, which opens the next Hebrew day`() {
        // 7th clean day Sunday 12.4 (כ״ה ניסן) -> tevila Sunday after tzeit = ליל שני כ״ו ניסן.
        assertEquals(
            "ביום ראשון כ״ה ניסן (12.4), לאחר צאת הכוכבים בלבד — ליל שני כ״ו ניסן",
            WomensAreaLabels.tevilaTiming(LocalDate.of(2026, 4, 12)),
        )
        assertEquals(
            "במוצאי שבת כ״ד ניסן (11.4), לאחר צאת הכוכבים בלבד — ליל ראשון כ״ה ניסן",
            WomensAreaLabels.tevilaTiming(LocalDate.of(2026, 4, 11)),
        )
        assertEquals("יום שלישי כ׳ ניסן (7.4), לפני השקיעה", WomensAreaLabels.hefsekTiming(LocalDate.of(2026, 4, 7)))
    }

    @Test
    fun `names`() {
        assertEquals("עונה בינונית", VesetKind.ONAH_BEINONIT.hebrewName)
        assertEquals("בלילה", Onah.NIGHT.hebrewName)
    }
}
