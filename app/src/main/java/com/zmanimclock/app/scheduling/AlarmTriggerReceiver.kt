package com.zmanimclock.app.scheduling

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.zmanimclock.app.R
import com.zmanimclock.app.ZmanimApp
import com.zmanimclock.app.feature.alarm.presentation.AlarmActivity

class AlarmTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alertId = intent.getLongExtra(EXTRA_ALERT_ID, -1)
        val zmanName = intent.getStringExtra(EXTRA_ZMAN_NAME) ?: "זמן הלכתי"
        val zmanTime = intent.getStringExtra(EXTRA_ZMAN_TIME) ?: ""
        val useSound = intent.getBooleanExtra(EXTRA_USE_SOUND, true)
        val useVibration = intent.getBooleanExtra(EXTRA_USE_VIBRATION, true)
        val isFullScreen = intent.getBooleanExtra(EXTRA_FULL_SCREEN, false)

        if (isFullScreen) {
            launchFullScreenAlarm(context, alertId, zmanName, zmanTime)
        } else {
            showNotification(context, alertId, zmanName, zmanTime, useVibration)
        }
    }

    private fun showNotification(
        context: Context,
        alertId: Long,
        zmanName: String,
        zmanTime: String,
        useVibration: Boolean,
    ) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        val notification = NotificationCompat.Builder(context, ZmanimApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(zmanName)
            .setContentText(if (zmanTime.isNotEmpty()) "בשעה $zmanTime" else "עכשיו")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .apply {
                if (useVibration) {
                    setVibrate(longArrayOf(0, 500, 200, 500))
                }
            }
            .build()

        manager.notify(alertId.toInt(), notification)
    }

    private fun launchFullScreenAlarm(
        context: Context,
        alertId: Long,
        zmanName: String,
        zmanTime: String,
    ) {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ALERT_ID, alertId)
            putExtra(EXTRA_ZMAN_NAME, zmanName)
            putExtra(EXTRA_ZMAN_TIME, zmanTime)
        }
        context.startActivity(intent)
    }

    companion object {
        const val EXTRA_ALERT_ID = "alert_id"
        const val EXTRA_ZMAN_NAME = "zman_name"
        const val EXTRA_ZMAN_TIME = "zman_time"
        const val EXTRA_USE_SOUND = "use_sound"
        const val EXTRA_USE_VIBRATION = "use_vibration"
        const val EXTRA_FULL_SCREEN = "full_screen"
    }
}
