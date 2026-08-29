package com.zmanimclock.app.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home-screen widget: the Hebrew date alone (weekday / day / month / year,
 * centered), with a user-chosen background color and transparency — for
 * someone who wants just the date on their home screen, not a zmanim board.
 *
 * A SEPARATE widget from [ZmanWidgetProvider] (its own manifest entry, own
 * `appwidget-provider` XML) rather than another [WidgetPrefs] toggle, so it
 * shows up as its own tile in the launcher's widget picker — "an additional
 * widget", not a mode of the existing one.
 *
 * Rendering is still done by the shared [WidgetRenderer] — it already reaches
 * this provider's ids alongside the zmanim widget's in one `renderAll()` pass
 * — reached the same way via a Hilt EntryPoint since AppWidgetProvider can't
 * be an @AndroidEntryPoint. Reuses [ZmanWidgetProvider]'s EntryPoint
 * interface rather than declaring a second one: both need exactly the same
 * [WidgetRenderer] singleton.
 */
class HebrewDateWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        newOptions: Bundle,
    ) {
        refresh(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { HebrewDateWidgetPrefs.remove(context, it) }
    }

    private fun refresh(context: Context) {
        val renderer = EntryPointAccessors
            .fromApplication(context.applicationContext, ZmanWidgetProvider.WidgetEntryPoint::class.java)
            .widgetRenderer()
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                renderer.renderAll()
            } catch (e: Exception) {
                Log.e(TAG, "Hebrew date widget render failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "HebrewDateWidgetProvider"
    }
}
