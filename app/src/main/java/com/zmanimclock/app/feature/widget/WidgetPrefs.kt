package com.zmanimclock.app.feature.widget

import android.content.Context
import com.zmanimclock.app.feature.zmanim.model.ZmanKind

/**
 * Per-widget content configuration.
 *
 * Stored on device-protected storage so the widget renders after a reboot
 * before first unlock, matching the alarm engine. Everything is keyed by
 * widget id: two widgets on the same home screen can show completely
 * different things — one a bare Hebrew-date strip, another a full board with
 * zmanim and the user's alarms.
 */
object WidgetPrefs {
    private const val FILE = "widget_prefs"

    const val MAX_ZMANIM = 8
    const val MAX_ALARMS = 4

    val DEFAULT_SELECTION = listOf(
        ZmanKind.HANETZ, ZmanKind.SOF_ZMAN_SHMA_GRA, ZmanKind.SHKIA, ZmanKind.TZEIT_HAKOCHAVIM,
    ).map { it.name }

    /** One widget's full configuration. */
    data class Config(
        val showHebrewDate: Boolean = true,
        val showNextZman: Boolean = true,
        val showZmanim: Boolean = true,
        val zmanim: List<String> = DEFAULT_SELECTION,
        val showAlarms: Boolean = false,
        val alarmCount: Int = 3,
    ) {
        /** Everything switched off would render an empty box. */
        val isEmpty: Boolean
            get() = !showHebrewDate && !showNextZman &&
                (!showZmanim || zmanim.isEmpty()) && !showAlarms
    }

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getConfig(context: Context, widgetId: Int): Config {
        val p = prefs(context)
        // getString returning null means the key was NEVER WRITTEN — a
        // brand-new widget — and DEFAULT_SELECTION is the right fallback.
        // getString returning "" means the user explicitly unchecked every
        // zman and saved; that used to collapse through the exact same
        // ?: DEFAULT_SELECTION and silently resurrect all four defaults the
        // user had just removed, with the config screen (if #13's
        // reconfigure route is even reachable) still showing them unchecked.
        val raw = p.getString(zmanimKey(widgetId), null)
        val zmanim = if (raw == null) DEFAULT_SELECTION else raw.split(",").filter { it.isNotBlank() }
        val config = Config(
            showHebrewDate = p.getBoolean(key(widgetId, "date"), true),
            showNextZman = p.getBoolean(key(widgetId, "next"), true),
            showZmanim = p.getBoolean(key(widgetId, "zmanim"), true),
            zmanim = zmanim,
            showAlarms = p.getBoolean(key(widgetId, "alarms"), false),
            alarmCount = p.getInt(key(widgetId, "alarm_count"), 3).coerceIn(1, MAX_ALARMS),
        )
        // A widget configured down to nothing is a blank rectangle the user
        // cannot recover from without removing it — fall back to the date.
        return if (config.isEmpty) config.copy(showHebrewDate = true) else config
    }

    fun setConfig(context: Context, widgetId: Int, config: Config) {
        prefs(context).edit()
            .putBoolean(key(widgetId, "date"), config.showHebrewDate)
            .putBoolean(key(widgetId, "next"), config.showNextZman)
            .putBoolean(key(widgetId, "zmanim"), config.showZmanim)
            .putString(zmanimKey(widgetId), config.zmanim.joinToString(","))
            .putBoolean(key(widgetId, "alarms"), config.showAlarms)
            .putInt(key(widgetId, "alarm_count"), config.alarmCount)
            .apply()
    }

    fun remove(context: Context, widgetId: Int) {
        prefs(context).edit()
            .remove(zmanimKey(widgetId))
            .remove(key(widgetId, "date"))
            .remove(key(widgetId, "next"))
            .remove(key(widgetId, "zmanim"))
            .remove(key(widgetId, "alarms"))
            .remove(key(widgetId, "alarm_count"))
            .apply()
    }

    // The zmanim key keeps its original name so widgets configured by an
    // earlier version keep their selection across the upgrade.
    private fun zmanimKey(widgetId: Int) = "widget_${widgetId}_zmanim"
    private fun key(widgetId: Int, suffix: String) = "widget_${widgetId}_$suffix"
}
