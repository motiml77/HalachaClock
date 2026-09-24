package com.zmanimclock.app.feature.womensarea.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WomensAreaNotificationTextTest {

    @Test
    fun `a clean day says only "התראה אישית" and the day number`() {
        assertEquals(NotificationText("התראה אישית", "יום 3"), WomensAreaNotificationText.cleanDay(3))
        assertEquals(NotificationText("התראה אישית", "יום 7 · אחרון"), WomensAreaNotificationText.cleanDay(7))
    }

    @Test
    fun `the evening reminder says only when`() {
        assertEquals(
            NotificationText("התראה אישית", "הערב · לאחר צאת הכוכבים"),
            WomensAreaNotificationText.tevilaEvening(),
        )
    }

    @Test
    fun `no notification names what it is about`() {
        val all = (1..7).map { WomensAreaNotificationText.cleanDay(it) } + WomensAreaNotificationText.tevilaEvening()
        all.forEach { n ->
            WomensAreaNotificationText.FORBIDDEN_WORDS.forEach { word ->
                assertTrue("'$word' in $n", word !in n.title && word !in n.text)
            }
        }
    }
}
