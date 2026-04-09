package com.zmanimclock.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.getSystemService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZmanimApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService<NotificationManager>() ?: return

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            "התראות זמנים",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "התראות על זמני הלכה"
            enableVibration(true)
            setBypassDnd(true)
        }

        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            "תזכורות",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "תזכורות שקטות על זמנים קרבים"
        }

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "שירות רקע",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "שירות חישוב זמנים ברקע"
        }

        manager.createNotificationChannels(listOf(alarmChannel, reminderChannel, serviceChannel))
    }

    companion object {
        const val CHANNEL_ALARM = "zmanim_alarm"
        const val CHANNEL_REMINDER = "zmanim_reminder"
        const val CHANNEL_SERVICE = "zmanim_service"
    }
}
