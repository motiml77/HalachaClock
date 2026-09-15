package com.zmanimclock.app.feature.chaitables.data

import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesDao
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesEntity

/**
 * Map-backed stand-in for the Room table `chai_tables_cache`, reproducing
 * exactly the four queries [ChaiTablesDao] declares. Rows are stored as the
 * real callers hand them over (fetchedAt, sourceEpochDay and all).
 */
class FakeChaiTablesDao : ChaiTablesDao {

    private val rows = LinkedHashMap<Pair<String, Int>, ChaiTablesEntity>()

    override suspend fun getSunrise(locationKey: String, dayOfYear: Int): ChaiTablesEntity? =
        rows[locationKey to dayOfYear]

    override suspend fun getCountForLocation(locationKey: String): Int =
        rows.keys.count { it.first == locationKey }

    override suspend fun insertAll(entries: List<ChaiTablesEntity>) {
        for (e in entries) rows[e.locationKey to e.dayOfYear] = e // REPLACE on PK conflict
    }

    override suspend fun deleteForLocation(locationKey: String) {
        rows.keys.removeAll { it.first == locationKey }
    }

    /** Snapshot of all rows (test-side inspection only; not part of the DAO). */
    fun allRows(): List<ChaiTablesEntity> = rows.values.toList()
}
