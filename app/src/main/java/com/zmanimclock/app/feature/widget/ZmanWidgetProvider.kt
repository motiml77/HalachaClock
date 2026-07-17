package com.zmanimclock.app.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home-screen widget provider. Rendering (including the live countdown
 * Chronometer) is delegated to [WidgetRenderer], reached via a Hilt EntryPoint
 * since AppWidgetProvider can't be an @AndroidEntryPoint.
 */
class ZmanWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun widgetRenderer(): WidgetRenderer
    }

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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refresh(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetPrefs.remove(context, it) }
    }

    private fun refresh(context: Context) {
        val renderer = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .widgetRenderer()
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                renderer.renderAll()
            } catch (e: Exception) {
                Log.e(TAG, "Widget render failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ZmanWidgetProvider"
        const val ACTION_REFRESH = "com.zmanimclock.app.widget.REFRESH"

        /** Ask all widgets to re-render (from zman-boundary pings, city change…). */
        fun refresh(context: Context) {
            context.sendBroadcast(
                Intent(context, ZmanWidgetProvider::class.java).setAction(ACTION_REFRESH)
            )
        }
    }
}
