package com.zmanimclock.desktop.data

import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import java.io.File
import java.util.Properties

/**
 * Settings, in `%LOCALAPPDATA%\HalachClock\settings.properties`.
 *
 * No database. The desktop build has no alarms to store, so the entire
 * persistent state is a handful of scalars — Room and DataStore would be
 * machinery without a job.
 *
 * The two offsets below are the ONLY user settings that change a halachic
 * result, and their defaults are read from the shared engine's constants
 * rather than written here, so the phone and the computer cannot start from
 * different numbers.
 */
data class DesktopPrefs(
    var cityId: String = DEFAULT_CITY_ID,
    var candleLightingMinutes: Long = com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine.DEFAULT_CANDLE_OFFSET_MINUTES,
    var tzeitShabbatMinutes: Long = com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine.TZEIT_SHABBAT_FIXED_MINUTES,
    /** Which zmanim may be announced as "הזמן הבא". Empty = all, as on Android. */
    var nextZmanFilter: Set<String> = emptySet(),
    /** Zmanim the user asked to be reminded about. Empty by default: opt-in. */
    var reminderZmanim: Set<String> = emptySet(),
    var startWithWindows: Boolean = false,
    var widgetPinnedToDesktop: Boolean = false,
    var widgetVisible: Boolean = false,
    var widgetZmanim: Set<String> = DEFAULT_WIDGET_ZMANIM,
) {

    fun save() {
        val p = Properties()
        p["cityId"] = cityId
        p["candleLightingMinutes"] = candleLightingMinutes.toString()
        p["tzeitShabbatMinutes"] = tzeitShabbatMinutes.toString()
        p["nextZmanFilter"] = nextZmanFilter.joinToString(",")
        p["reminderZmanim"] = reminderZmanim.joinToString(",")
        p["startWithWindows"] = startWithWindows.toString()
        p["widgetPinnedToDesktop"] = widgetPinnedToDesktop.toString()
        p["widgetVisible"] = widgetVisible.toString()
        p["widgetZmanim"] = widgetZmanim.joinToString(",")
        runCatching {
            file().parentFile?.mkdirs()
            file().outputStream().use { p.store(it, "HalachClock settings") }
        }
    }

    companion object {
        const val DEFAULT_CITY_ID = "ירושלים"

        val DEFAULT_WIDGET_ZMANIM = setOf("HANETZ", "SOF_ZMAN_SHMA_GRA", "SHKIA", "TZEIT_LECHUMRA")

        private fun dir(): File {
            val base = System.getenv("LOCALAPPDATA")
                ?: System.getProperty("user.home")
            return File(base, "HalachClock")
        }

        private fun file() = File(dir(), "settings.properties")

        fun load(): DesktopPrefs {
            val f = file()
            if (!f.exists()) return DesktopPrefs()
            val p = Properties()
            runCatching { f.inputStream().use { p.load(it) } }.getOrElse { return DesktopPrefs() }
            // Every one of these sets holds ZmanKind.name strings, saved
            // under whatever names the constants carried at the time — read
            // them back through canonicalNames so a rename does not silently
            // empty a filter. See ZmanKind.legacyName.
            fun set(key: String): Set<String> = ZmanKind.canonicalNames(
                p.getProperty(key).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
            ).toSet()
            val defaults = DesktopPrefs()
            return DesktopPrefs(
                cityId = p.getProperty("cityId") ?: defaults.cityId,
                candleLightingMinutes = p.getProperty("candleLightingMinutes")?.toLongOrNull()
                    ?: defaults.candleLightingMinutes,
                tzeitShabbatMinutes = p.getProperty("tzeitShabbatMinutes")?.toLongOrNull()
                    ?: defaults.tzeitShabbatMinutes,
                nextZmanFilter = set("nextZmanFilter"),
                reminderZmanim = set("reminderZmanim"),
                startWithWindows = p.getProperty("startWithWindows")?.toBoolean() ?: false,
                widgetPinnedToDesktop = p.getProperty("widgetPinnedToDesktop")?.toBoolean() ?: false,
                widgetVisible = p.getProperty("widgetVisible")?.toBoolean() ?: false,
                widgetZmanim = set("widgetZmanim").ifEmpty { DEFAULT_WIDGET_ZMANIM },
            )
        }
    }
}
