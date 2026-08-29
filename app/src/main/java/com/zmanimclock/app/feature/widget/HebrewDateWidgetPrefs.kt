package com.zmanimclock.app.feature.widget

import android.content.Context

/**
 * A background the Hebrew-date widget can be painted in.
 *
 * Deliberately all DARK tones: the widget's four lines are white text with no
 * per-preset color of their own (see [HebrewDateParts] / widget_hebrew_date.xml),
 * so every preset here has to stay dark enough for white text to read clearly
 * on it. Adding a light preset later means giving the text its own color too,
 * not just another entry in this list.
 *
 * [color] is fully opaque (alpha FF) — transparency is a SEPARATE knob
 * ([HebrewDateWidgetPrefs.Config.opacityPercent]), applied to the whole
 * painted layer rather than baked into the preset, so any color can be shown
 * at any transparency.
 */
enum class WidgetColorPreset(val key: String, val label: String, val color: Int) {
    NAVY("navy", "כחול", 0xFF123A8B.toInt()),
    BLACK("black", "שחור", 0xFF1A1A1A.toInt()),
    GREEN("green", "ירוק", 0xFF1B4332.toInt()),
    WINE("wine", "בורדו", 0xFF5C1A1A.toInt()),
    PURPLE("purple", "סגול", 0xFF3A1B6E.toInt()),
    SLATE("slate", "אפור-כחול", 0xFF2C3E50.toInt());

    companion object {
        val DEFAULT = NAVY

        /** By persisted [key]; an unknown/corrupt key falls back to [DEFAULT]. */
        fun fromKey(key: String?): WidgetColorPreset = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * Per-widget configuration for the Hebrew-date-only widget.
 *
 * A separate store from [WidgetPrefs] (own file, own keys) even though widget
 * ids are drawn from the same OS-wide pool — the two widgets show unrelated
 * content, and keeping their prefs apart means neither's key names have to
 * account for the other's.
 */
object HebrewDateWidgetPrefs {
    private const val FILE = "hebrew_date_widget_prefs"
    const val MIN_OPACITY_PERCENT = 0
    const val MAX_OPACITY_PERCENT = 100

    data class Config(
        val preset: WidgetColorPreset = WidgetColorPreset.DEFAULT,
        /** 0 = the color layer is fully see-through (text floats on the wallpaper alone); 100 = opaque. */
        val opacityPercent: Int = MAX_OPACITY_PERCENT,
    )

    private fun prefs(context: Context) =
        // Device-protected storage, matching WidgetPrefs: the widget has to
        // render before first unlock after a reboot.
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getConfig(context: Context, widgetId: Int): Config {
        val p = prefs(context)
        return Config(
            preset = WidgetColorPreset.fromKey(p.getString(key(widgetId, "color"), null)),
            opacityPercent = p.getInt(key(widgetId, "opacity"), MAX_OPACITY_PERCENT)
                .coerceIn(MIN_OPACITY_PERCENT, MAX_OPACITY_PERCENT),
        )
    }

    fun setConfig(context: Context, widgetId: Int, config: Config) {
        prefs(context).edit()
            .putString(key(widgetId, "color"), config.preset.key)
            .putInt(key(widgetId, "opacity"), config.opacityPercent.coerceIn(MIN_OPACITY_PERCENT, MAX_OPACITY_PERCENT))
            .apply()
    }

    fun remove(context: Context, widgetId: Int) {
        prefs(context).edit()
            .remove(key(widgetId, "color"))
            .remove(key(widgetId, "opacity"))
            .apply()
    }

    private fun key(widgetId: Int, suffix: String) = "hdwidget_${widgetId}_$suffix"
}
