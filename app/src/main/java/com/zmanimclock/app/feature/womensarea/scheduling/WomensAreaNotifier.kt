package com.zmanimclock.app.feature.womensarea.scheduling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.zmanimclock.app.MainActivity
import com.zmanimclock.app.R
import com.zmanimclock.app.feature.womensarea.model.NotificationText
import com.zmanimclock.app.feature.womensarea.model.WomensAreaNotificationText
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the Women's Area notifications — its own class and its own channel,
 * apart from the app's NotificationHelper and its alarm/reminder channels, so
 * nothing of this feature shows up anywhere else in the app, and she can set
 * this channel's sound (or silence it) in the system settings on its own.
 *
 * MODESTY, which decides every choice below:
 * - The words never say what it is about: "התראה אישית · יום 3" (see
 *   WomensAreaNotificationText, whose test forbids the telling words).
 * - The lock screen shows only the public version: "התראה אישית", no text.
 * - The small icon is the logo reduced to one colour (ic_stat_womens_area —
 *   an arch over waves; no words) in its lilac: Android draws a small icon from
 *   its alpha alone. The full-colour logo is the LARGE icon, on the unlocked
 *   notification only — the lock screen's public version stays bare. The
 *   app's own ic_stat_zman is left as it is.
 * - The channel's name in the system settings is the neutral "התראות אישיות".
 */
@Singleton
class WomensAreaNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun show(notificationId: Int, content: NotificationText) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        ensureChannel(manager)
        // Opens the app the ordinary way, on its first tab — never straight
        // into the Women's Area, so a tap by anyone else lands on the zmanim.
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_womens_area)
            .setColor(LILAC)
            .setContentTitle(WomensAreaNotificationText.TITLE)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_womens_area)
            .setLargeIcon(largeIcon())
            .setColor(LILAC)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        manager.notify(notificationId, notification)
    }

    /** The full-colour logo at a notification-friendly size; null (no large icon) if it cannot be decoded. */
    private fun largeIcon(): Bitmap? = runCatching {
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.women_area_logo)
        Bitmap.createScaledBitmap(logo, LARGE_ICON_PX, LARGE_ICON_PX, true)
    }.getOrNull()

    /** Idempotent: re-creating an existing channel only updates its name and description. */
    private fun ensureChannel(manager: NotificationManager) {
        val channel = NotificationChannel(CHANNEL, "התראות אישיות", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "התראות שבחרת בתוך האפליקציה"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL = "womens_area_private"
        const val LARGE_ICON_PX = 192
        /** WomensAreaLilac (0xFF9C7AB8) — the area's own colour, at the owner's request. */
        const val LILAC = 0xFF9C7AB8.toInt()
    }
}
