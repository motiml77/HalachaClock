package com.zmanimclock.app.scheduling

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.getSystemService
import com.zmanimclock.app.feature.zmanim.data.ZmanimCalculator
import com.zmanimclock.app.feature.zmanim.data.model.ZmanId
import com.zmanimclock.app.feature.zmanim.data.model.ZmanTime
import com.zmanimclock.app.location.model.AppGeoLocation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Foreground service that keeps a persistent notification showing the next
 * upcoming halachic zman. Updates automatically every minute and shows brief
 * announcements when each zman arrives.
 *
 * Lifecycle:
 *  - Started from MainActivity / boot receiver.
 *  - Runs continuously; recalculates at midnight for the new day.
 *  - User can toggle on/off via Settings (persisted in SharedPreferences).
 */
class ZmanimForegroundService : Service() {

    companion object {
        const val PREFS_NAME = "zmanim_service_prefs"
        const val PREF_SERVICE_ENABLED = "persistent_notification_enabled"
        const val PREF_ANNOUNCEMENT_ZMANIM = "announcement_zmanim"

        private const val UPDATE_INTERVAL_MS = 60_000L // 1 minute
        private const val COUNTDOWN_INTERVAL_MS = 30_000L // 30 seconds for countdown refresh

        /** Convenience to start the service if the user preference allows it. */
        fun startIfEnabled(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            if (prefs.getBoolean(PREF_SERVICE_ENABLED, true)) {
                val intent = Intent(context, ZmanimForegroundService::class.java)
                context.startForegroundService(intent)
            }
        }

        /** Stop the service explicitly. */
        fun stop(context: Context) {
            context.stopService(Intent(context, ZmanimForegroundService::class.java))
        }

        /** Check if the service preference is enabled. */
        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(PREF_SERVICE_ENABLED, true)
        }

        /** Save which zmanim should trigger announcements. */
        fun setAnnouncementZmanim(context: Context, zmanIds: Set<String>) {
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putStringSet(PREF_ANNOUNCEMENT_ZMANIM, zmanIds)
                .apply()
        }

        /** Get which zmanim trigger announcements (defaults to all enabled zmanim). */
        fun getAnnouncementZmanim(context: Context): Set<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getStringSet(PREF_ANNOUNCEMENT_ZMANIM, null)
                ?: ZmanId.entries.filter { it.defaultEnabled }.map { it.name }.toSet()
        }
    }

    private lateinit var notificationHelper: ZmanimNotificationHelper
    private lateinit var zmanimCalculator: ZmanimCalculator
    private lateinit var prefs: SharedPreferences
    private lateinit var notificationManager: NotificationManager

    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // Current state
    private var todayZmanim: List<ZmanTime> = emptyList()
    private var currentZmanIndex: Int = -1
    private var lastCalculationDay: Int = -1
    private var announcedZmanim: MutableSet<String> = mutableSetOf()

    // The location used for calculation
    private var currentLocation: AppGeoLocation = getDefaultLocation()

    // --- Lifecycle ---

    override fun onCreate() {
        super.onCreate()
        notificationHelper = ZmanimNotificationHelper(this)
        zmanimCalculator = ZmanimCalculator()
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        notificationManager = getSystemService()!!
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Show a loading notification immediately to satisfy foreground requirement
        startForeground(
            ZmanimNotificationHelper.PERSISTENT_NOTIFICATION_ID,
            notificationHelper.buildLoadingNotification()
        )

        // Load saved location from SharedPreferences
        loadSavedLocation()

        // Calculate and start update loop
        calculateAndUpdate()
        startUpdateLoop()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // --- Core Logic ---

    /**
     * Main calculation: get today's zmanim, find the next one, update notification.
     */
    private fun calculateAndUpdate() {
        val now = Calendar.getInstance()
        val today = now.get(Calendar.DAY_OF_YEAR)

        // Recalculate if it's a new day or first run
        if (today != lastCalculationDay) {
            lastCalculationDay = today
            announcedZmanim.clear()
            recalculateZmanim(now)
        }

        updateNotification()
    }

    /**
     * Recalculate all zmanim for today using the current location.
     */
    private fun recalculateZmanim(date: Calendar = Calendar.getInstance()) {
        try {
            val dayZmanim = zmanimCalculator.calculateZmanim(currentLocation, date)

            // Keep only zmanim that have actual times (not shaah zmanit etc.)
            // and filter to user-enabled ones
            val enabledIds = ZmanId.entries.filter { it.defaultEnabled }.map { it }.toSet()

            todayZmanim = dayZmanim.zmanim
                .filter { it.time != null && it.id in enabledIds }
                .sortedBy { it.time!!.time }
        } catch (e: Exception) {
            todayZmanim = emptyList()
        }
    }

    /**
     * Update the persistent notification with the next upcoming zman.
     * Also check if any zman has just arrived and show an announcement.
     */
    private fun updateNotification() {
        val now = Date()

        // Check for zman arrivals and show announcements
        checkForArrivals(now)

        // Find next upcoming zman
        val nextZman = todayZmanim.firstOrNull { it.time != null && it.time.after(now) }

        if (nextZman != null && nextZman.time != null) {
            val zmanName = getString(nextZman.id.hebrewNameRes)
            val zmanTime = timeFormat.format(nextZman.time)
            val countdown = formatCountdown(nextZman.time.time - now.time)

            // Load saved location name
            val locationName = prefs.getString("saved_location_name", currentLocation.cityNameHebrew)
                ?: currentLocation.cityNameHebrew

            val notification = notificationHelper.buildPersistentNotification(
                zmanName = zmanName,
                zmanTime = zmanTime,
                countdown = countdown,
                locationName = locationName,
            )

            notificationManager.notify(
                ZmanimNotificationHelper.PERSISTENT_NOTIFICATION_ID,
                notification
            )
        } else {
            // All zmanim for today have passed; show "waiting for tomorrow"
            val notification = notificationHelper.buildPersistentNotification(
                zmanName = "ממתין ליום חדש",
                zmanTime = "",
                countdown = "",
                locationName = currentLocation.cityNameHebrew,
            )
            notificationManager.notify(
                ZmanimNotificationHelper.PERSISTENT_NOTIFICATION_ID,
                notification
            )
        }
    }

    /**
     * Check if any zman has arrived since last check and fire announcements.
     */
    private fun checkForArrivals(now: Date) {
        val announcementIds = getAnnouncementZmanim(this)

        for (zman in todayZmanim) {
            val time = zman.time ?: continue
            val key = zman.id.name

            // Skip if already announced or not in announcement set
            if (key in announcedZmanim) continue
            if (key !in announcementIds) continue

            // If the zman time has passed (within the last 2 minutes), announce it
            val diffMs = now.time - time.time
            if (diffMs in 0..120_000) {
                announcedZmanim.add(key)
                showArrivalNotification(zman)
            } else if (diffMs > 120_000) {
                // Mark as announced without showing (we missed it)
                announcedZmanim.add(key)
            }
        }
    }

    /**
     * Show a brief notification that a zman has arrived.
     */
    private fun showArrivalNotification(zman: ZmanTime) {
        val zmanName = getString(zman.id.hebrewNameRes)
        val zmanTime = if (zman.time != null) timeFormat.format(zman.time) else ""
        val notificationId = ZmanimNotificationHelper.ANNOUNCEMENT_NOTIFICATION_ID_BASE +
                zman.id.ordinal

        val notification = notificationHelper.buildZmanArrivalNotification(
            zmanName = zmanName,
            zmanTime = zmanTime,
            notificationId = notificationId,
        )

        notificationManager.notify(notificationId, notification)
    }

    // --- Update Loop ---

    private val updateRunnable = object : Runnable {
        override fun run() {
            calculateAndUpdate()
            handler.postDelayed(this, COUNTDOWN_INTERVAL_MS)
        }
    }

    private fun startUpdateLoop() {
        handler.removeCallbacks(updateRunnable)
        handler.postDelayed(updateRunnable, COUNTDOWN_INTERVAL_MS)
    }

    // --- Location ---

    /**
     * Load the saved location from SharedPreferences. Falls back to Jerusalem default.
     * The main app writes location data to these prefs when the user picks a city.
     */
    private fun loadSavedLocation() {
        val lat = prefs.getFloat("saved_latitude", Float.MIN_VALUE)
        val lon = prefs.getFloat("saved_longitude", Float.MIN_VALUE)

        if (lat != Float.MIN_VALUE && lon != Float.MIN_VALUE) {
            currentLocation = AppGeoLocation(
                cityNameHebrew = prefs.getString("saved_location_name", "ירושלים") ?: "ירושלים",
                cityNameEnglish = prefs.getString("saved_location_name_en", "Jerusalem") ?: "Jerusalem",
                latitude = lat.toDouble(),
                longitude = lon.toDouble(),
                elevation = prefs.getFloat("saved_elevation", 0f).toDouble(),
                timeZone = TimeZone.getTimeZone(
                    prefs.getString("saved_timezone", "Asia/Jerusalem") ?: "Asia/Jerusalem"
                ),
            )
        }
    }

    /** Save location data so the service can read it on restart. */
    fun saveLocation(location: AppGeoLocation) {
        prefs.edit()
            .putFloat("saved_latitude", location.latitude.toFloat())
            .putFloat("saved_longitude", location.longitude.toFloat())
            .putFloat("saved_elevation", location.elevation.toFloat())
            .putString("saved_location_name", location.cityNameHebrew)
            .putString("saved_location_name_en", location.cityNameEnglish)
            .putString("saved_timezone", location.timeZone.id)
            .apply()
    }

    // --- Helpers ---

    private fun formatCountdown(millis: Long): String {
        if (millis <= 0) return ""
        val hours = millis / 3_600_000
        val minutes = (millis % 3_600_000) / 60_000
        val seconds = (millis % 60_000) / 1_000
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    private fun getDefaultLocation(): AppGeoLocation {
        return AppGeoLocation(
            cityNameHebrew = "ירושלים",
            cityNameEnglish = "Jerusalem",
            latitude = 31.778,
            longitude = 35.235,
            elevation = 800.0,
            timeZone = TimeZone.getTimeZone("Asia/Jerusalem"),
        )
    }
}
