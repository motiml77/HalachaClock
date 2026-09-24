package com.zmanimclock.app.feature.womensarea.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zmanimclock.app.feature.calendar.model.HebrewMonthSequence
import com.zmanimclock.app.feature.calendar.model.MonthGrid
import com.zmanimclock.app.feature.calendar.model.MonthGridBuilder
import com.zmanimclock.app.feature.womensarea.data.WomensAreaDao
import com.zmanimclock.app.feature.womensarea.data.WomensAreaEntryEntity
import com.zmanimclock.app.feature.womensarea.data.WomensAreaEntryType
import com.zmanimclock.app.feature.womensarea.model.Onah
import com.zmanimclock.app.feature.womensarea.model.VesetPrediction
import com.zmanimclock.app.feature.womensarea.model.WomensAreaCalculator
import com.zmanimclock.app.feature.womensarea.model.WomensAreaMarker
import com.zmanimclock.app.feature.womensarea.model.WomensAreaMarkers
import com.zmanimclock.app.feature.womensarea.scheduling.WomensAreaReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class WomensAreaViewModel @Inject constructor(
    private val dao: WomensAreaDao,
    private val reminderScheduler: WomensAreaReminderScheduler,
) : ViewModel() {

    val allEntries: StateFlow<List<WomensAreaEntryEntity>> = dao.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The latest veset, the one before it (for the haflaga), and the latest
     * first-clean-day — only the LATEST of each kind drives the calendar.
     * "Latest" is by date, never by insert order: a backfilled entry must not
     * displace a later one.
     */
    private val latestEntries: StateFlow<LatestEntries> = allEntries
        .map { entries ->
            val vesets = entries.filter { it.type == WomensAreaEntryType.PERIOD_START }
                .sortedByDescending { it.epochDay }
            LatestEntries(
                veset = vesets.getOrNull(0),
                previousVeset = vesets.getOrNull(1),
                firstCleanDay = entries.filter { it.type == WomensAreaEntryType.FIRST_CLEAN_DAY }
                    .maxByOrNull { it.epochDay },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LatestEntries())

    /** The latest veset's full prediction, for the separation-days card under the calendar. */
    val latestPrediction: StateFlow<VesetPrediction?> = latestEntries
        .map { latest ->
            latest.veset?.let { veset ->
                WomensAreaCalculator.predict(
                    start = veset.date,
                    onah = veset.onah,
                    previousStart = latest.previousVeset?.date,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** date -> marker, recomputed whenever the latest entries change. */
    val markersByDate: StateFlow<Map<LocalDate, WomensAreaMarker>> = latestEntries
        .map { latest ->
            WomensAreaMarkers.build(
                latestVeset = latest.veset?.date,
                latestVesetOnah = latest.veset?.onah,
                previousVeset = latest.previousVeset?.date,
                latestFirstCleanDay = latest.firstCleanDay?.date,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ------------------------------------------------------------- grid
    // The grid itself (dates/Hebrew labels/geometry) never depends on this
    // feature's entries — only the marker overlay above does — so it's safe
    // to cache exactly the way CalendarViewModel caches its own grid.
    private val gridCache = object : LinkedHashMap<Int, MonthGrid>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, MonthGrid>) = size > 24
    }

    fun monthGrid(index: Int): MonthGrid = synchronized(gridCache) {
        gridCache.getOrPut(index) { MonthGridBuilder.build(HebrewMonthSequence.refAt(index)) }
    }

    // ---------------------------------------------------------- actions

    /** [date] is the Hebrew day of the veset — for [Onah.NIGHT], the evening before it. */
    fun addPeriodStart(date: LocalDate, onah: Onah) = viewModelScope.launch {
        dao.insert(
            WomensAreaEntryEntity(type = WomensAreaEntryType.PERIOD_START, epochDay = date.toEpochDay(), onah = onah)
        )
    }

    fun addFirstCleanDay(date: LocalDate) = viewModelScope.launch {
        val id = dao.insert(
            WomensAreaEntryEntity(type = WomensAreaEntryType.FIRST_CLEAN_DAY, epochDay = date.toEpochDay())
        )
        reminderScheduler.scheduleSevenDayCount(id, date)
    }

    /** [onah] is ignored for a FIRST_CLEAN_DAY, which has none. */
    fun updateEntry(entry: WomensAreaEntryEntity, newDate: LocalDate, onah: Onah?) = viewModelScope.launch {
        val isVeset = entry.type == WomensAreaEntryType.PERIOD_START
        dao.update(entry.copy(epochDay = newDate.toEpochDay(), onah = if (isVeset) onah else null))
        if (entry.type == WomensAreaEntryType.FIRST_CLEAN_DAY) {
            reminderScheduler.cancel(entry.id)
            reminderScheduler.scheduleSevenDayCount(entry.id, newDate)
        }
    }

    fun deleteEntry(entry: WomensAreaEntryEntity) = viewModelScope.launch {
        dao.delete(entry)
        if (entry.type == WomensAreaEntryType.FIRST_CLEAN_DAY) reminderScheduler.cancel(entry.id)
    }
}

private data class LatestEntries(
    val veset: WomensAreaEntryEntity? = null,
    val previousVeset: WomensAreaEntryEntity? = null,
    val firstCleanDay: WomensAreaEntryEntity? = null,
)

private val WomensAreaEntryEntity.date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
