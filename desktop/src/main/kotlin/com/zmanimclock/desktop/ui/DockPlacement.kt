package com.zmanimclock.desktop.ui

import java.io.File
import java.util.Properties

/**
 * Where the folded bookmark sits: which edge, and how far along it.
 *
 * Beside settings.properties, but a separate file — the same reasoning as
 * [com.zmanimclock.desktop.widget.WidgetPlacement]: placement is not a
 * setting.
 *
 * [along] IS A FRACTION (0f..1f) OF THE EDGE'S LENGTH, not a pixel offset.
 * The widget's placement is stored in pixels because it can sit ANYWHERE and
 * needs a monitor to be meaningful; the bookmark only ever needs "how far
 * along the edge it currently occupies", and a fraction answers that exactly
 * the same way whether the screen is 1080p or 4K, and after a resolution
 * change or a different monitor without ever going stale — there is no
 * "off-screen" fraction to guard against, which is the entire class of bug
 * the widget's monitor-signature matching exists to prevent.
 */
internal object DockPlacement {

    data class Saved(val edge: DockEdge, val along: Float)

    private fun file(): File = File(
        File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "HalachClock"),
        "dock.properties",
    )

    fun save(edge: DockEdge, along: Float) {
        runCatching {
            val p = Properties()
            p["edge"] = edge.name
            p["along"] = along.coerceIn(0f, 1f).toString()
            val f = file()
            f.parentFile?.mkdirs()
            f.outputStream().use { p.store(it, "HalachClock bookmark placement") }
        }
    }

    /** Null when nothing was ever saved — the caller falls back to LEFT, centred. */
    fun load(): Saved? = runCatching {
        val f = file()
        if (!f.exists()) return null
        val p = Properties()
        f.inputStream().use { p.load(it) }
        val edge = p.getProperty("edge")?.let { name ->
            runCatching { DockEdge.valueOf(name) }.getOrNull()
        } ?: return null
        val along = p.getProperty("along")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
        Saved(edge, along)
    }.getOrNull()
}
