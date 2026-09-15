package com.zmanimclock.app.feature.chaitables.data

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * One real row from the bundled visible-sunrise asset (chai_tables_preloaded.json):
 * a (month, day) solar key, the local wall-clock time ChaiTables recorded for
 * it, and [sourceEpochDay] — the REAL Gregorian date that time was recorded
 * on, needed to re-base Israel's DST offset onto whatever year the row is
 * actually being read for (see [rebasedInstant]).
 */
data class BundledSunriseRow(
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val sourceEpochDay: Long,
) {
    /** The (month, day) key this row is stored and looked up under. */
    val solarKey: Int get() = SolarDayKey.of(month, day)
}

/** The parsed contents of chai_tables_preloaded.json. */
data class BundledChaiTablesAsset(
    val version: Int,
    /** Metro area name (e.g. "jerusalem") -> its rows, one per solar day. */
    val metros: Map<String, List<BundledSunriseRow>>,
    /** City id (Hebrew CityCatalog id, or a legacy English id) -> metro name. */
    val cityToMetro: Map<String, String>,
)

/**
 * Parses and reads the ChaiTables visible-sunrise asset shared by every
 * platform, and re-bases a bundled row onto a target date's DST offset.
 *
 * Kept in :zmanim-engine, not duplicated per platform: this asset's row
 * format and its DST correction are exactly the kind of thing that silently
 * forks between two hand-written copies (this is what actually happened
 * before — the desktop build had no ChaiTables integration at all, and the
 * Android one had a version that never stamped sourceEpochDay, both real
 * bugs). One implementation, read by every consumer.
 */
object ChaiTablesBundledAsset {

    /** Row format: `[month, day, hour, minute, second, sourceEpochDay]`. */
    fun parse(json: String): BundledChaiTablesAsset {
        val root = JSONObject(json)
        val version = root.optInt("version", 1)

        val metrosJson = root.getJSONObject("metros")
        val metros = LinkedHashMap<String, List<BundledSunriseRow>>()
        val metroNames = metrosJson.keys()
        while (metroNames.hasNext()) {
            val name = metroNames.next()
            val rows = metrosJson.getJSONArray(name)
            metros[name] = (0 until rows.length()).map { i ->
                val row = rows.getJSONArray(i)
                BundledSunriseRow(
                    month = row.getInt(0),
                    day = row.getInt(1),
                    hour = row.getInt(2),
                    minute = row.getInt(3),
                    second = row.getInt(4),
                    sourceEpochDay = row.getLong(5),
                )
            }
        }

        val cityToMetroJson = root.getJSONObject("cityToMetro")
        val cityToMetro = LinkedHashMap<String, String>()
        val cityKeys = cityToMetroJson.keys()
        while (cityKeys.hasNext()) {
            val id = cityKeys.next()
            cityToMetro[id] = cityToMetroJson.getString(id)
        }

        return BundledChaiTablesAsset(version, metros, cityToMetro)
    }

    /**
     * The row for [date] in [rows], keyed by (month, day) — 29 Feb falls back
     * to 28 Feb, its solar twin, exactly like [SolarDayKey.fallbackFor].
     */
    fun rowFor(rows: List<BundledSunriseRow>, date: LocalDate): BundledSunriseRow? {
        val byKey = rows.associateBy { it.solarKey }
        val key = SolarDayKey.of(date)
        return byKey[key] ?: SolarDayKey.fallbackFor(date)?.let { byKey[it] }
    }

    /** Convenience form of [rebasedInstant] for a parsed bundled row. */
    fun rebasedInstant(row: BundledSunriseRow, date: LocalDate, zoneId: String): Instant =
        rebasedInstant(row.hour, row.minute, row.second, row.sourceEpochDay, date, zoneId)

    /**
     * Combine a stored wall-clock time ([hour]:[minute]:[second]) with the
     * real date in [zoneId], correcting for DST.
     *
     * The asset is generated with Israel's DST rules in force at the time
     * the row was recorded ([sourceEpochDay]). Reusing that wall-clock
     * string verbatim in a year whose DST boundary falls on a different
     * date shifts the result by a full hour on the days between the two
     * boundaries — correct for it by shifting by the difference between the
     * target and source UTC offsets, which leaves the underlying solar
     * moment the row actually encodes unchanged.
     */
    fun rebasedInstant(
        hour: Int,
        minute: Int,
        second: Int,
        sourceEpochDay: Long,
        date: LocalDate,
        zoneId: String,
    ): Instant {
        val zone = ZoneId.of(zoneId)
        val stored = LocalTime.of(hour, minute, second)
        val corrected = runCatching {
            val rules = zone.rules
            val sourceDate = LocalDate.ofEpochDay(sourceEpochDay)
            val sourceOffset = rules.getOffset(sourceDate.atTime(stored))
            val targetOffset = rules.getOffset(date.atTime(stored))
            stored.plusSeconds((targetOffset.totalSeconds - sourceOffset.totalSeconds).toLong())
        }.getOrDefault(stored)
        return date.atTime(corrected).atZone(zone).toInstant()
    }
}
