package com.zmanimclock.app.scheduling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.zmanimclock.app.R
import com.zmanimclock.app.feature.alarm.presentation.AlarmActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns notification channels and builders for the alarm engine.
 *
 * Channel design (yuriykulikov/AlarmClock pattern): the ALARM channel itself is
 * SILENT — sound and vibration are produced by [AlarmSoundService] so that we
 * control ramp-up volume, looping and stop/snooze precisely. The channel only
 * carries importance + full-screen-intent capability.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ALARM = "zmanim_alarm"
        const val CHANNEL_REMINDER = "zmanim_reminder"

        /**
         * Status-line channel. A channel's settings are immutable once created,
         * so the id is versioned: bumping it recreates the channel with the
         * lock-screen visibility below on devices that already had v1.
         */
        const val CHANNEL_SERVICE = "zmanim_status_v2"
        private const val CHANNEL_SERVICE_LEGACY = "zmanim_service"

        const val ALARM_NOTIFICATION_ID = 1001
        const val STATUS_NOTIFICATION_ID = 1002

        /** style-1D primary — notification accent. */
        private const val ACCENT = 0xFF123A8B.toInt()
    }

    /**
     * Android 14+ lets the system/user revoke USE_FULL_SCREEN_INTENT. When
     * revoked, the ringing screen won't auto-open (sound still plays; tapping
     * the notification opens it) — surfaced in onboarding + settings.
     */
    fun canUseFullScreenIntent(): Boolean =
        android.os.Build.VERSION.SDK_INT < 34 ||
            context.getSystemService<NotificationManager>()?.canUseFullScreenIntent() == true

    /**
     * The persistent status notification: always-visible line with the next
     * zman of the day and the next armed alarm. Silent, ongoing, updates in
     * place (no re-alert).
     */
    fun showOngoingStatus(zmanName: String, zmanTime: String, nextAlarmText: String?) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, com.zmanimclock.app.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // A CUSTOM view guarantees the zman name is bold on every OEM skin —
        // the standard title slot is rendered in regular weight by several
        // skins, and partial StyleSpans get stripped. DecoratedCustomViewStyle
        // keeps the system icon/header chrome around our two lines.
        val body = nextAlarmText?.let { "השעון הבא: $it" } ?: "אין שעון מעורר פעיל"
        val content = android.widget.RemoteViews(context.packageName, R.layout.notification_status).apply {
            setTextViewText(R.id.status_zman_name, zmanName)
            setTextViewText(R.id.status_zman_time, zmanTime)
            setTextViewText(R.id.status_next_alarm, body)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_zman)
            .setColor(ACCENT)
            .setSubText("הזמן הבא")
            // Plain title/text kept as the fallback for surfaces that ignore
            // custom views (some lock screens, Wear, Android Auto)
            .setContentTitle(if (zmanTime.isNotEmpty()) "$zmanName · $zmanTime" else zmanName)
            .setContentText(body)
            .setCustomContentView(content)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            // Full content on the lock screen — readable the moment the screen
            // wakes, without unlocking
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .build()
        manager.notify(STATUS_NOTIFICATION_ID, notification)
    }

    fun cancelOngoingStatus() {
        context.getSystemService<NotificationManager>()?.cancel(STATUS_NOTIFICATION_ID)
    }

    fun createChannels() {
        val manager = context.getSystemService<NotificationManager>() ?: return

        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            "התראות זמנים",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "צלצול בזמני הלכה (הצליל מנוהל על ידי האפליקציה)"
            setSound(null, null) // sound comes from AlarmSoundService
            enableVibration(false) // vibration comes from AlarmSoundService
            setBypassDnd(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        val reminder = NotificationChannel(
            CHANNEL_REMINDER,
            "תזכורות",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "תזכורות שקטות על זמנים קרבים"
        }

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            "שורת הזמן הבא",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "הזמן ההלכתי הבא והשעון המעורר הבא — קבוע בהתראות ובמסך הנעילה"
            // Show the full line on the lock screen, not "תוכן מוסתר"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setShowBadge(false)
        }

        // Retire the v1 status channel so upgraders don't keep its settings
        runCatching { manager.deleteNotificationChannel(CHANNEL_SERVICE_LEGACY) }
        manager.createNotificationChannels(listOf(alarm, reminder, service))
    }

    /**
     * The ongoing full-screen alarm notification shown while
     * [AlarmSoundService] is ringing. Launches [AlarmActivity] over the
     * lock screen and offers dismiss/snooze actions.
     */
    fun buildAlarmNotification(
        alertId: Long,
        title: String,
        timeText: String,
        snoozeMinutes: Int,
        challenge: String = "NONE",
        shabbatMode: Boolean = false,
        snoozesLeft: Int = -1,
    ): android.app.Notification {
        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmSoundService.EXTRA_ALARM_ID, alertId)
            putExtra(AlarmSoundService.EXTRA_TITLE, title)
            putExtra(AlarmSoundService.EXTRA_TIME_TEXT, timeText)
            putExtra(AlarmSoundService.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            putExtra(AlarmSoundService.EXTRA_CHALLENGE, challenge)
            putExtra(AlarmSoundService.EXTRA_SHABBAT, shabbatMode)
            putExtra(AlarmSoundService.EXTRA_SNOOZES_LEFT, snoozesLeft)
        }
        val fullScreenPi = PendingIntent.getActivity(
            context,
            alertId.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissPi = servicePendingIntent(alertId, AlarmSoundService.ACTION_DISMISS, 1)
        val snoozePi = servicePendingIntent(alertId, AlarmSoundService.ACTION_SNOOZE, 2, snoozeMinutes)
        val snoozeLabel = when {
            snoozesLeft == 0 -> null // no snooze action shown
            snoozesLeft > 0 -> "נודניק ($snoozeMinutes ד' · נשארו $snoozesLeft)"
            else -> "נודניק ($snoozeMinutes ד')"
        }

        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_zman)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(
                when {
                    shabbatMode -> "השקיעה בעוד דקות ספורות — שבת שלום!"
                    timeText.isNotEmpty() -> "בשעה $timeText"
                    else -> "עכשיו"
                }
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .addAction(0, "ביטול", dismissPi)
            .apply { snoozeLabel?.let { addAction(0, it, snoozePi) } }
            .build()
    }

    /** A plain (non-ringing) reminder notification for notification-only alerts. */
    fun showReminder(alertId: Long, title: String, timeText: String, vibrate: Boolean) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_zman)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(if (timeText.isNotEmpty()) "בשעה $timeText" else "עכשיו")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .apply { if (vibrate) setVibrate(longArrayOf(0, 400, 200, 400)) }
            .build()
        manager.notify(alertId.toInt(), notification)
    }

    private fun servicePendingIntent(
        alertId: Long,
        action: String,
        requestOffset: Int,
        snoozeMinutes: Int? = null,
    ): PendingIntent {
        val intent = Intent(context, AlarmSoundService::class.java).apply {
            this.action = action
            putExtra(AlarmSoundService.EXTRA_ALARM_ID, alertId)
            snoozeMinutes?.let { putExtra(AlarmSoundService.EXTRA_SNOOZE_MINUTES, it) }
        }
        return PendingIntent.getService(
            context,
            alertId.toInt() * 10 + requestOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
