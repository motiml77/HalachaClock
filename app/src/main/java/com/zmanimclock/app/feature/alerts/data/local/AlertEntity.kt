package com.zmanimclock.app.feature.alerts.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val zmanId: String,
    val offsetMinutes: Int = 0,
    val offsetBefore: Boolean = true,
    val useSound: Boolean = true,
    val useVibration: Boolean = true,
    val useNotification: Boolean = true,
    val isFullScreenAlarm: Boolean = false,
    val isActive: Boolean = true,
    val isDaily: Boolean = true,
    val skipShabbat: Boolean = false,
    val skipYomTov: Boolean = false,
    val label: String = "",
    val snoozeDurationMinutes: Int = 5,
    val createdAt: Long = System.currentTimeMillis(),
)
