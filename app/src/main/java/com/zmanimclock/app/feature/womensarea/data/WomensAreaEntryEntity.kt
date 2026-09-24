package com.zmanimclock.app.feature.womensarea.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zmanimclock.app.feature.womensarea.model.Onah

/**
 * Which of the two self-reported kinds an entry is. Stored by NAME (Room's
 * enum handling), so a constant here must never be renamed without a
 * migration — see v12→v13, which retired FIRST_CLEAN_DAY.
 */
enum class WomensAreaEntryType {
    /** התחלת ווסת — with its [WomensAreaEntryEntity.onah]. */
    PERIOD_START,
    /** הפסק טהרה, made before shkia; שבעה נקיים start on the next Hebrew day. */
    HEFSEK_TAHARA,
}

/**
 * One self-reported date in either sequence. [epochDay] is
 * `LocalDate.toEpochDay()` of the HEBREW DAY the entry is for — the date its
 * cell in the Hebrew month grid carries, i.e. the Gregorian date on whose
 * daytime that Hebrew date falls. A veset seen after shkia belongs to the
 * next Hebrew day and is stored on that day with [onah] = NIGHT; see
 * VesetPrediction for the whole convention. A calendar date with no
 * time-of-day, matching
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
    /**
     * ביום / בלילה, for a PERIOD_START. Null for a HEFSEK_TAHARA, and for a
     * period start saved before the question was asked (v11 rows).
     */
    val onah: Onah? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
