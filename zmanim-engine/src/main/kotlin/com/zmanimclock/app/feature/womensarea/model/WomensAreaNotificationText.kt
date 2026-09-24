package com.zmanimclock.app.feature.womensarea.model

/** Title and one-line text of one Women's Area notification. */
data class NotificationText(val title: String, val text: String)

/**
 * The words of the Women's Area notifications, in one testable place.
 *
 * MODEST BY DESIGN — the owner's rule: a notification never names what it is
 * about. No "נקיים", "טבילה", "מקווה", "ווסת", "הפסק" or "פרישה" — only
 * "התראה אישית" and a day number or "הערב". She knows what it means; anyone
 * glancing at the phone, even unlocked, does not. [FORBIDDEN_WORDS] is
 * checked by a test so a later edit cannot slip one in.
 *
 * The lock screen shows even less: just [TITLE] (the public version).
 */
object WomensAreaNotificationText {

    const val TITLE = "התראה אישית"

    /** Words no notification may contain. */
    val FORBIDDEN_WORDS = listOf("נקי", "טבילה", "מקווה", "ווסת", "וסת", "הפסק", "פרישה", "טהרה", "ראייה")

    /** Day [day] (1..7) of the count. */
    fun cleanDay(day: Int): NotificationText = NotificationText(
        title = TITLE,
        text = if (day == WomensAreaCalculator.CLEAN_DAYS_COUNT) "יום $day · אחרון" else "יום $day",
    )

    /** The evening of the 7th day, before its tzeit. */
    fun tevilaEvening(): NotificationText = NotificationText(
        title = TITLE,
        text = "הערב · לאחר צאת הכוכבים",
    )
}
