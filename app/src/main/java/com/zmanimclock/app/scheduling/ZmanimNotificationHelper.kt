package com.zmanimclock.app.scheduling

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.zmanimclock.app.MainActivity
import com.zmanimclock.app.R
import com.zmanimclock.app.ZmanimApp

/**
 * Helper class for building and managing zmanim-related notifications.
 *
 * Two types:
 *  1. Persistent foreground notification – always visible, shows next upcoming zman.
 *  2. Zman arrival announcement – brief notification when a zman time arrives.
 */
class ZmanimNotificationHelper(private val context: Context) {

    companion object {
        const val PERSISTENT_NOTIFICATION_ID = 7700
        const val ANNOUNCEMENT_NOTIFICATION_ID_BASE = 7800
    }

    private val openAppIntent: PendingIntent by lazy {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Build the persistent foreground notification showing the next zman.
     *
     * @param zmanName  Hebrew display name of the next zman (e.g. "שקיעה")
     * @param zmanTime  Formatted time string (e.g. "17:45")
     * @param countdown Remaining time string (e.g. "1:23:45")
     * @param locationName Name of the current location
     */
    fun buildPersistentNotification(
        zmanName: String,
        zmanTime: String,
        countdown: String,
        locationName: String,
    ): Notification {
        // Custom layout for rich RTL Hebrew display
        val remoteViews = RemoteViews(context.packageName, R.layout.notification_zman_persistent).apply {
            setTextViewText(R.id.notification_zman_name, zmanName)
            setTextViewText(R.id.notification_zman_time, zmanTime)
            setTextViewText(R.id.notification_countdown, countdown)
            setTextViewText(R.id.notification_location, locationName)
        }

        return NotificationCompat.Builder(context, ZmanimApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCustomContentView(remoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .build()
    }

    /**
     * Build a brief announcement notification when a zman time arrives.
     * Uses the REMINDER channel (medium priority, brief sound).
     *
     * @param zmanName  Hebrew display name (e.g. "שקיעת החמה")
     * @param zmanTime  Formatted time string (e.g. "17:45")
     * @param notificationId Unique ID for this announcement
     */
    fun buildZmanArrivalNotification(
        zmanName: String,
        zmanTime: String,
        notificationId: Int,
    ): Notification {
        val title = "\u200F$zmanName" // RTL mark for proper display
        val text = "\u200F$zmanName - $zmanTime"

        return NotificationCompat.Builder(context, ZmanimApp.CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setTimeoutAfter(5 * 60 * 1000L) // Auto-dismiss after 5 minutes
            .build()
    }

    /**
     * Build a minimal "starting" notification for when the service starts
     * but hasn't finished calculating zmanim yet.
     */
    fun buildLoadingNotification(): Notification {
        return NotificationCompat.Builder(context, ZmanimApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("שעון זמנים")
            .setContentText("מחשב זמנים...")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }
}
