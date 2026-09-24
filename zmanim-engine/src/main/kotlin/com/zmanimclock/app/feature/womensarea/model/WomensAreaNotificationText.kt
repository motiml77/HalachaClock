package com.zmanimclock.app.feature.womensarea.model

import java.time.LocalDate

/** Title, one-line text and expanded text of one Women's Area notification. */
data class NotificationText(val title: String, val text: String, val bigText: String)

/**
 * The words of the Women's Area notifications, in one testable place.
 *
 * What the lock screen shows is NOT here: it is always just "תזכורת" (the
 * notification's public version), so nothing personal is readable without
 * unlocking the phone.
 */
object WomensAreaNotificationText {

    /** Day [day] (1..7) of the count after a hefsek on [hefsek]. */
    fun cleanDay(day: Int, hefsek: LocalDate): NotificationText {
        val tevilaDay = WomensAreaCalculator.tevilaDay(hefsek)
        val left = WomensAreaCalculator.CLEAN_DAYS_COUNT - day
        val title = "שבעה נקיים · יום $day מתוך ${WomensAreaCalculator.CLEAN_DAYS_COUNT}"
        return if (left == 0) {
            NotificationText(
                title = title,
                text = "היום האחרון — הלילה טבילה, לאחר צאת הכוכבים בלבד",
                bigText = "היום האחרון לספירה.\n★ טבילה: ${WomensAreaLabels.tevilaTiming(tevilaDay)}",
            )
        } else {
            NotificationText(
                title = title,
                text = "זמן בדיקה",
                bigText = "זמן בדיקה · נותרו עוד $left ימים.\n★ טבילה: ${WomensAreaLabels.tevilaTiming(tevilaDay)}",
            )
        }
    }

    /** ערב טבילה — on the 7th clean day, before its tzeit. */
    fun tevilaEvening(hefsek: LocalDate): NotificationText {
        val tevilaDay = WomensAreaCalculator.tevilaDay(hefsek)
        return NotificationText(
            title = "★ ערב טבילה",
            text = "הערב — לאחר צאת הכוכבים בלבד",
            bigText = "${WomensAreaLabels.tevilaTiming(tevilaDay)}.\nלא לטבול לפני צאת הכוכבים.",
        )
    }
}
