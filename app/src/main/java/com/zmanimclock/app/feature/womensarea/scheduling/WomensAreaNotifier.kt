package com.zmanimclock.app.feature.womensarea.scheduling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
 * - The small icon and the colour are the app's ordinary ones, not the
 *   area's spring and lilac, so even the status bar gives nothing away.
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
            .setSmallIcon(R.drawable.ic_stat_zman)
            .setColor(APP_ACCENT)
            .setContentTitle(WomensAreaNotificationText.TITLE)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_zman)
            .setColor(APP_ACCENT)
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
        /**
         * The app's own navy (NotificationHelper's ACCENT), not the area's
         * lilac: the notification should look like any other reminder from
         * this app.
         */
        const val APP_ACCENT = 0xFF123A8B.toInt()
    }
}
