package com.zmanimclock.desktop.data

import com.zmanimclock.app.feature.chaitables.data.BundledChaiTablesAsset
import com.zmanimclock.app.feature.chaitables.data.ChaiTablesBundledAsset
import java.time.Instant
import java.time.LocalDate

/**
 * Desktop's ChaiTables visible-sunrise source: the bundled asset only, no
 * live network fetch.
 *
 * Android needs Room plus a fetcher because it also caches network results
 * across app restarts and has to survive the process being killed mid-fetch.
 * Desktop has neither problem for the bundled data — the asset is under
 * 100 KB, so it is simply parsed into memory once per run, using the exact
 * same parser and DST re-basing Android's preloader uses
 * ([ChaiTablesBundledAsset], in :zmanim-engine) so the two platforms cannot
 * silently compute this differently.
 *
 * Any city not in the bundled [BundledChaiTablesAsset.cityToMetro] (the same
 * 38 ids Android ships with) simply has no visible netz here — exactly the
 * mishor fallback Android itself shows for an unsupported or offline city.
 * Live network fetching for OTHER cities is not implemented on desktop.
 */
class DesktopChaiTablesRepository {

    private val asset: BundledChaiTablesAsset by lazy {
        val json = javaClass.getResourceAsStream(ASSET_FILE)!!.bufferedReader().use { it.readText() }
        ChaiTablesBundledAsset.parse(json)
    }

    /** The visible sunrise for [cityId] on [date], or null when this city has no bundled data. */
    fun getVisibleSunrise(cityId: String, date: LocalDate, zoneId: String): Instant? {
        val metro = asset.cityToMetro[cityId] ?: return null
        val rows = asset.metros[metro] ?: return null
        val row = ChaiTablesBundledAsset.rowFor(rows, date) ?: return null
        return ChaiTablesBundledAsset.rebasedInstant(row, date, zoneId)
    }

    companion object {
        // Same classpath resource Android's ChaiTablesPreloader reads —
        // one copy, in :zmanim-engine.
        private const val ASSET_FILE = "/chai_tables_preloaded.json"
    }
}
