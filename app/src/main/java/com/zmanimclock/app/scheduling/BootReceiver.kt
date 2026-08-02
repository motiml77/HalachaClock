package com.zmanimclock.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import androidx.core.content.getSystemService
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-arms all alarms after events that wipe or invalidate AlarmManager state:
 * device boot, app update, wall-clock changes and timezone changes.
 * (AlarmManager alarms do not survive a reboot — this receiver is what makes
 * the alarm engine reliable across restarts.)
 *
 * Direct Boot (A3): on LOCKED_BOOT_COMPLETED — before first unlock —
 * WorkManager is unavailable, so we reschedule directly via the injected
 * scheduler (which reads the device-protected DB + prefs shadow). After the
 * user unlocks, BOOT_COMPLETED arrives and we go through WorkManager as usual.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            // Midnight rollover — the zmanim for the new day differ, and both
            // the widget and the status notification must move with it.
            Intent.ACTION_DATE_CHANGED,
            -> {
                Log.i(TAG, "Rescheduling alarms due to ${intent.action}")
                val unlocked = context.getSystemService<UserManager>()?.isUserUnlocked ?: true
                if (unlocked) {
                    // Normal path — durable, retried
                    RescheduleWorker.enqueueUnique(context)
                    StatusNotificationReceiver.ping(context)
                } else {
                    // Pre-unlock: WorkManager unavailable — reschedule inline
                    val pending = goAsync()
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            alarmScheduler.rescheduleAll()
                        } catch (e: Exception) {
                            Log.e(TAG, "Direct-boot reschedule failed", e)
                        } finally {
                            pending.finish()
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
