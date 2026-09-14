package com.zmanimclock.app.scheduling

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * Fires at the exact alarm time. Deliberately thin: hands the alarm id to
 * [AlarmSoundService], which loads the alarm from Room, rings, deactivates
 * one-time alarms and re-enqueues the reschedule chain.
 */
class AlarmTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        if (alarmId < 0) return
        val isWakeCheckRering = intent.getBooleanExtra(EXTRA_IS_WAKE_CHECK_RERING, false)

        // THE ENFORCEMENT POINT THAT CANNOT BE MISSED. Every ring — the regular
        // occurrence, a snooze, a wake-check re-ring — arrives through this
        // receiver, so the subscription is checked HERE rather than only when
        // alarms are armed. Arming-time checks alone would let an alarm armed
        // yesterday, before Play said NO, ring forever: AlarmManager keeps a
        // PendingIntent until it fires, whatever happened in between.
        //
        // Checked BEFORE the foreground service starts, not inside it: the
        // service's first act is a full-screen alarm notification, which would
        // throw the ringing screen up for an alarm it was about to refuse.
        //
        // Read straight from device-protected storage, because this can fire
        // before the first unlock after a reboot, when neither Play nor Hilt's
        // credential-storage-backed graph is available. See AccessPolicy for
        // why only an authoritative NO from Play silences an alarm.
        val entitlementCache = runCatching {
            com.zmanimclock.app.feature.subscription.EntitlementStore(context.applicationContext).cached()
        }.getOrNull()
        if (entitlementCache != null &&
            !com.zmanimclock.app.feature.subscription.AccessPolicy.alarmsAllowed(
                com.zmanimclock.app.BuildConfig.PAYWALL_ENABLED, entitlementCache,
            )
        ) {
            Log.w(TAG, "Alarm $alarmId suppressed — subscription not active")
            runCatching {
                NotificationHelper(context.applicationContext).apply {
                    createChannels()
                    showSubscriptionLapsed(
                        unverified = com.zmanimclock.app.feature.subscription.AccessPolicy
                            .blockedOnlyForLackOfVerification(
                                com.zmanimclock.app.BuildConfig.PAYWALL_ENABLED, entitlementCache,
                            ),
                    )
                }
            }
            // The widget and the ongoing status line still advertise zmanim and
            // this alarm; refresh them now rather than at the next zman boundary.
            runCatching { StatusNotificationReceiver.ping(context.applicationContext) }
            return
        }

        Log.i(TAG, "Alarm fired: id=$alarmId" + if (isWakeCheckRering) " (wake-check re-ring)" else "")

        // Android 12+ only lets an app start a foreground service from the
        // background under an exemption — the relevant one being "an exact
        // alarm fired". When the exact-alarm permission was revoked the
        // scheduler degrades to an INEXACT alarm, and then this start throws
        // ForegroundServiceStartNotAllowedException, which would crash the
        // process at the exact moment the user needed the alarm. Never let
        // that happen: fall back to a full-screen-intent notification, which
        // still wakes the screen and opens the ringing UI.
        val started = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmSoundService::class.java).apply {
                    action = AlarmSoundService.ACTION_START
                    putExtra(AlarmSoundService.EXTRA_ALARM_ID, alarmId)
                    putExtra(AlarmSoundService.EXTRA_IS_WAKE_CHECK_RERING, isWakeCheckRering)
                },
            )
        }.isSuccess

        if (!started) {
            Log.e(TAG, "FGS start blocked for alarm $alarmId — posting FSI notification instead")
            runCatching {
                val helper = NotificationHelper(context.applicationContext)
                helper.createChannels()
                context.getSystemService<NotificationManager>()?.notify(
                    NotificationHelper.ALARM_NOTIFICATION_ID,
                    helper.buildAlarmNotification(
                        alertId = alarmId,
                        title = "שעון מעורר",
                        timeText = "",
                        snoozeMinutes = 0,
                        snoozesLeft = 0,
                        // AlarmSoundService never runs on this path, so it is
                        // the ONLY source of sound/vibration here — CHANNEL_ALARM
                        // is deliberately silent for the normal path, which used
                        // to mean this fallback rang the user's phone with
                        // nothing audible or tactile at all.
                        channelId = NotificationHelper.CHANNEL_ALARM_FALLBACK,
                    ),
                )
            }.onFailure { Log.e(TAG, "FSI fallback also failed", it) }

            // The service normally re-enqueues RescheduleWorker after every
            // firing (AlarmSoundService.start()) — that never runs on this
            // path, so without this the alarm's NEXT occurrence (and any
            // repeat day after this one) is simply never armed again.
            runCatching {
                RescheduleWorker.enqueueUnique(context.applicationContext)
            }.onFailure { Log.e(TAG, "Reschedule enqueue failed on fallback path", it) }
        }
    }

    companion object {
        private const val TAG = "AlarmTriggerReceiver"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_IS_WAKE_CHECK_RERING = "is_wake_check_rering"
    }
}
