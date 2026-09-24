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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the Women's Area notifications — its own class and its own channel,
 * apart from the app's NotificationHelper and its alarm/reminder channels, so
 * nothing of this feature shows up anywhere else in the app, and she can set
 * this channel's sound (or silence it) in the system settings on its own.
 *
 * PRIVACY, which decides every choice below:
 * - The lock screen shows only the public version: "תזכורת", nothing else.
 * - The small icon is the app's ordinary one, not the feature's spring, so
 *   even the status bar gives nothing away.
 * - The channel's name in the system settings is the neutral "תזכורות אישיות".
 */
@Singleton
class WomensAreaNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun show(notificationId: Int, content: NotificationText) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        ensureChannel(manager)
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_zman)
            .setContentTitle("תזכורת")
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_zman)
            .setColor(LILAC)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        manager.notify(notificationId, notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(CHANNEL, "תזכורות אישיות", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "תזכורות שבחרת בתוך האפליקציה"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL = "womens_area_private"
        /** WomensAreaLilac (0xFF9C7AB8) — the notification's accent. */
        const val LILAC = 0xFF9C7AB8.toInt()
    }
}
