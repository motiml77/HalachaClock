package com.zmanimclock.app.feature.womensarea.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zmanimclock.app.feature.calendar.model.HebrewMonthSequence
import com.zmanimclock.app.feature.calendar.model.MonthGrid
import com.zmanimclock.app.feature.calendar.model.MonthGridBuilder
import com.zmanimclock.app.feature.womensarea.data.WomensAreaDao
import com.zmanimclock.app.feature.womensarea.data.WomensAreaEntryEntity
import com.zmanimclock.app.feature.womensarea.data.WomensAreaEntryType
import com.zmanimclock.app.feature.womensarea.model.VesetKind
import com.zmanimclock.app.feature.womensarea.model.VesetPrediction
import com.zmanimclock.app.feature.womensarea.model.WomensAreaCalculator
import com.zmanimclock.app.feature.womensarea.scheduling.WomensAreaReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Everything this feature draws on a day: which veset predictions land here, and/or which clean-count day this is. */
data class WomensAreaMarker(
    val vesetKinds: Set<VesetKind> = emptySet(),
    val cleanDayNumber: Int? = null,
)

@HiltViewModel
class WomensAreaViewModel @Inject constructor(
    private val dao: WomensAreaDao,
    private val reminderScheduler: WomensAreaReminderScheduler,
) : ViewModel() {

    val allEntries: StateFlow<List<WomensAreaEntryEntity>> = dao.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Only the LATEST entry of each kind drives the calendar — older cycles don't accumulate markers. */
    val latestPeriodStart: StateFlow<LocalDate?> = allEntries
        .map { entries -> latestOf(entries, WomensAreaEntryType.PERIOD_START) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val previousPeriodStart: StateFlow<LocalDate?> = allEntries
        .map { entries ->
            entries.filter { it.type == WomensAreaEntryType.PERIOD_START }
                .map { it.epochDay }.sortedDescending().getOrNull(1)?.let(LocalDate::ofEpochDay)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val latestFirstCleanDay: StateFlow<LocalDate?> = allEntries
        .map { entries -> latestOf(entries, WomensAreaEntryType.FIRST_CLEAN_DAY) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** date -> marker, recomputed whenever the latest entries change. Cheap — at most a handful of dates. */
    val markersByDate: StateFlow<Map<LocalDate, WomensAreaMarker>> = combine(
        latestPeriodStart, previousPeriodStart, latestFirstCleanDay,
    ) { start, previousStart, firstCleanDay ->
        buildMarkers(start, previousStart, firstCleanDay)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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

    fun addPeriodStart(date: LocalDate) = viewModelScope.launch {
        dao.insert(WomensAreaEntryEntity(type = WomensAreaEntryType.PERIOD_START, epochDay = date.toEpochDay()))
    }

    fun addFirstCleanDay(date: LocalDate) = viewModelScope.launch {
        val id = dao.insert(
            WomensAreaEntryEntity(type = WomensAreaEntryType.FIRST_CLEAN_DAY, epochDay = date.toEpochDay())
        )
        reminderScheduler.scheduleSevenDayCount(id, date)
    }

    fun updateEntryDate(entry: WomensAreaEntryEntity, newDate: LocalDate) = viewModelScope.launch {
        dao.update(entry.copy(epochDay = newDate.toEpochDay()))
        if (entry.type == WomensAreaEntryType.FIRST_CLEAN_DAY) {
            reminderScheduler.cancel(entry.id)
            reminderScheduler.scheduleSevenDayCount(entry.id, newDate)
        }
    }

    fun deleteEntry(entry: WomensAreaEntryEntity) = viewModelScope.launch {
        dao.delete(entry)
        if (entry.type == WomensAreaEntryType.FIRST_CLEAN_DAY) reminderScheduler.cancel(entry.id)
    }

    /** For an immediate "here's what this would predict" preview, before the entry is even saved. */
    suspend fun predictFor(newStart: LocalDate): VesetPrediction {
        val previous = dao.getPreviousByType(WomensAreaEntryType.PERIOD_START, newStart.toEpochDay())
        return WomensAreaCalculator.predict(newStart, previous?.let { LocalDate.ofEpochDay(it.epochDay) })
    }

    companion object {
        private fun latestOf(entries: List<WomensAreaEntryEntity>, type: WomensAreaEntryType): LocalDate? =
            entries.filter { it.type == type }.maxByOrNull { it.epochDay }?.let { LocalDate.ofEpochDay(it.epochDay) }

        fun buildMarkers(
            latestPeriodStart: LocalDate?,
            previousPeriodStart: LocalDate?,
            latestFirstCleanDay: LocalDate?,
        ): Map<LocalDate, WomensAreaMarker> {
            val markers = mutableMapOf<LocalDate, WomensAreaMarker>()
            fun addVeset(date: LocalDate?, kind: VesetKind) {
                date ?: return
                val existing = markers[date] ?: WomensAreaMarker()
                markers[date] = existing.copy(vesetKinds = existing.vesetKinds + kind)
            }
            latestPeriodStart?.let { start ->
                val prediction = WomensAreaCalculator.predict(start, previousPeriodStart)
                addVeset(prediction.onahBeinonit, VesetKind.ONAH_BEINONIT)
                addVeset(prediction.haflaga, VesetKind.HAFLAGA)
                addVeset(prediction.yomHachodesh, VesetKind.YOM_HACHODESH)
            }
            latestFirstCleanDay?.let { firstCleanDay ->
                WomensAreaCalculator.cleanDayDates(firstCleanDay).forEachIndexed { i, date ->
                    val existing = markers[date] ?: WomensAreaMarker()
                    markers[date] = existing.copy(cleanDayNumber = i + 1)
                }
            }
            return markers
        }
    }
}
