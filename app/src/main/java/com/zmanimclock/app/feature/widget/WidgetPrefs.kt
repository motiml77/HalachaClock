package com.zmanimclock.app.feature.widget

import android.content.Context
import com.zmanimclock.app.feature.zmanim.model.ZmanKind

/**
 * Per-widget zman selection, stored on device-protected storage so the widget
 * renders after a reboot before first unlock (matching the alarm engine).
 */
object WidgetPrefs {
    private const val FILE = "widget_prefs"

    val DEFAULT_SELECTION = listOf(
        ZmanKind.HANETZ, ZmanKind.SOF_ZMAN_SHMA_GRA, ZmanKind.SHKIA, ZmanKind.TZEIT_HAKOCHAVIM,
    ).map { it.name }

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getSelection(context: Context, widgetId: Int): List<String> {
        val raw = prefs(context).getString(key(widgetId), null) ?: return DEFAULT_SELECTION
        return raw.split(",").filter { it.isNotBlank() }.ifEmpty { DEFAULT_SELECTION }
    }

    fun setSelection(context: Context, widgetId: Int, kinds: List<String>) {
        prefs(context).edit().putString(key(widgetId), kinds.joinToString(",")).apply()
    }

    fun remove(context: Context, widgetId: Int) {
        prefs(context).edit().remove(key(widgetId)).apply()
    }

    private fun key(widgetId: Int) = "widget_${widgetId}_zmanim"
}
