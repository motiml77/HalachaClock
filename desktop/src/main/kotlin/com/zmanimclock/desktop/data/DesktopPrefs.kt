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
    /**
     * The user's alerts — see [ZmanAlert]. Empty by default: an app that
     * starts by interrupting someone about zmanim they never asked for gets
     * its notifications switched off wholesale, and the ones they DID want go
     * with them.
     *
     * This replaced a plain `reminderZmanim: Set<String>`, which could say
     * only "tell me at שקיעה" — no offset, and no name to put on the banner.
     * Old files are migrated on load rather than dropped.
     */
    var alerts: List<ZmanAlert> = emptyList(),
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
        // One property per alert rather than one delimited blob: a blob would
        // have to escape the separator out of user-typed names, and a single
        // corrupt record would take the whole list with it.
        alerts.forEach { p["alert.${it.id}"] = it.encode() }
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

        /**
         * The system property a test build sets to redirect this whole
         * directory somewhere disposable.
         *
         * It exists because it was needed: `save()` had no seam at all, so
         * AlertCrudTest — which exercises add/edit/delete through the real
         * service — wrote its fixtures straight into the developer's own
         * settings.properties, and two test alerts appeared in the running
         * app. A file path with no override is a global variable.
         */
        const val DATA_DIR_PROPERTY = "halachclock.data.dir"

        fun dir(): File {
            System.getProperty(DATA_DIR_PROPERTY)?.takeIf { it.isNotBlank() }
                ?.let { return File(it) }
            val base = System.getenv("LOCALAPPDATA")
                ?: System.getProperty("user.home")
            return File(base, "HalachClock")
        }

        private fun file() = File(dir(), "settings.properties")

        /**
         * Reads `alert.<id>` records, and MIGRATES the old `reminderZmanim`
         * set if no alert records exist: each remembered zman becomes an alert
         * at zero offset, named after the zman itself. Someone who had five
         * reminders configured must not open the new version to an empty list
         * and conclude their settings were thrown away.
         */
        private fun readAlerts(p: Properties): List<ZmanAlert> {
            val stored = p.stringPropertyNames()
                .filter { it.startsWith("alert.") }
                .sorted()
                .mapNotNull { key ->
                    ZmanAlert.decode(key.removePrefix("alert."), p.getProperty(key).orEmpty())
                }
            if (stored.isNotEmpty()) return stored

            val legacy = ZmanKind.canonicalNames(
                p.getProperty("reminderZmanim").orEmpty()
                    .split(',').map { it.trim() }.filter { it.isNotEmpty() },
            ).mapNotNull { ZmanKind.fromNameOrNull(it) }
            return legacy.mapIndexed { i, kind ->
                ZmanAlert(id = "a${i + 1}", name = kind.shortName, kind = kind)
            }
        }

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
                alerts = readAlerts(p),
                startWithWindows = p.getProperty("startWithWindows")?.toBoolean() ?: false,
                widgetPinnedToDesktop = p.getProperty("widgetPinnedToDesktop")?.toBoolean() ?: false,
                widgetVisible = p.getProperty("widgetVisible")?.toBoolean() ?: false,
                widgetZmanim = set("widgetZmanim").ifEmpty { DEFAULT_WIDGET_ZMANIM },
            )
        }
    }
}
