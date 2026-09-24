package com.zmanimclock.app.feature.womensarea.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WomensAreaNotificationTextTest {

    // Hefsek Sunday 5.4.2026 -> clean days Mon 6.4 .. Sun 12.4 -> tevila Sunday 12.4 after tzeit.
    private val hefsek = LocalDate.of(2026, 4, 5)

    @Test
    fun `a clean day says which day, how many are left, and when the tevila is`() {
        val text = WomensAreaNotificationText.cleanDay(3, hefsek)
        assertEquals("שבעה נקיים · יום 3 מתוך 7", text.title)
        assertEquals("זמן בדיקה", text.text)
        assertTrue(text.bigText.startsWith("זמן בדיקה · נותרו עוד 4 ימים."))
        assertTrue(text.bigText.contains("ביום ראשון כ״ה ניסן (12.4), לאחר צאת הכוכבים בלבד"))
    }

    @Test
    fun `the 7th day says tonight, after tzeit only`() {
        val text = WomensAreaNotificationText.cleanDay(7, hefsek)
        assertEquals("היום האחרון — הלילה טבילה, לאחר צאת הכוכבים בלבד", text.text)
    }

    @Test
    fun `the tevila evening`() {
        val text = WomensAreaNotificationText.tevilaEvening(hefsek)
        assertEquals("★ ערב טבילה", text.title)
        assertTrue(text.bigText.startsWith("ביום ראשון כ״ה ניסן (12.4), לאחר צאת הכוכבים בלבד — ליל שני כ״ו ניסן."))
    }
}
