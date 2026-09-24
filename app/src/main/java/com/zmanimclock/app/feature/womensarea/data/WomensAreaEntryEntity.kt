package com.zmanimclock.app.feature.womensarea.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Which of the two independent, self-reported date sequences an entry belongs to. */
enum class WomensAreaEntryType { PERIOD_START, FIRST_CLEAN_DAY }

/**
 * One self-reported date in either sequence. [epochDay] is
 * `LocalDate.toEpochDay()` — a calendar date with no time-of-day, matching
 * [com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesEntity]'s own
 * epoch-day convention (epoch-millis would wrongly imply an instant/timezone).
 *
 * Nothing computed (predictions, the 7-day count) is ever stored here — it is
 * always recomputed from these rows, the same way CalendarDayMeta's omerDay
 * is recomputed rather than cached. That also means editing or deleting an
 * entry self-corrects every derived marker, with no extra bookkeeping.
 */
@Entity(tableName = "womens_area_entries", indices = [Index(value = ["type", "epochDay"])])
data class WomensAreaEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: WomensAreaEntryType = WomensAreaEntryType.PERIOD_START,
    val epochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
)
