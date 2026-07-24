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
        const val CHANNEL_SERVICE = "zmanim_service"

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
        // Title: "הזמן הבא: " + BOLD(name) + " · time"
        val title = android.text.SpannableStringBuilder("הזמן הבא: ")
        val nameStart = title.length
        title.append(zmanName)
        title.setSpan(
            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
            nameStart, title.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        if (zmanTime.isNotEmpty()) title.append(" · $zmanTime")
        // Second line: a touch smaller than the title
        val body = android.text.SpannableString(
            nextAlarmText?.let { "השעון הבא: $it" } ?: "אין שעון מעורר פעיל"
        )
        body.setSpan(
            android.text.style.RelativeSizeSpan(0.85f),
            0, body.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_zman)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
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
            "שירות רקע",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "שירותי רקע של האפליקציה"
        }

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
