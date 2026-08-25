package com.zmanimclock.app.feature.calendar.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.calendar.model.HebrewMonthSequence
import com.zmanimclock.app.ui.theme.Ext
import com.zmanimclock.app.ui.theme.ZmanListTimeStyle
import java.time.LocalDate

/**
 * The לוח שנה tab: a Hebrew month grid over the day it selects.
 *
 * One screen rather than a pushed detail or a bottom sheet — the point of the
 * feature is moving BETWEEN days, and a modal would make every day an
 * open/read/dismiss cycle. Here the grid never leaves, so the next tap is
 * always one tap.
 */
@Composable
fun CalendarScreen(
    onCreateZmanAlarm: (String) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detail by viewModel.dayDetail.collectAsStateWithLifecycle()
    val alerted by viewModel.alertedKinds.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResumed()
        onPauseOrDispose { }
    }

    val pagerState = rememberPagerState(
        initialPage = HebrewMonthSequence.clamp(state.visibleMonthIndex),
        pageCount = { HebrewMonthSequence.size },
    )

    // The pager tells the header which month is on screen…
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { viewModel.onMonthVisible(it) }
    }
    // …and selecting a day in another month makes the pager follow. Guarded on
    // inequality so the two cannot drive each other in a loop.
    LaunchedEffect(state.visibleMonthIndex) {
        if (pagerState.currentPage != state.visibleMonthIndex) {
            pagerState.animateScrollToPage(state.visibleMonthIndex)
        }
    }

    val grid = remember(state.visibleMonthIndex) { viewModel.monthGrid(state.visibleMonthIndex) }
    val todayMonth = remember(state.today) { HebrewMonthSequence.indexOf(state.today) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            MonthHeader(
                grid = grid,
                showTodayButton = state.visibleMonthIndex != todayMonth,
                onPrevious = { viewModel.onMonthVisible(state.visibleMonthIndex - 1) },
                onNext = { viewModel.onMonthVisible(state.visibleMonthIndex + 1) },
                onToday = { viewModel.goToToday() },
                onTitleClick = { viewModel.goToToday() },
            )
        }
        item { WeekdayRow() }
        item {
            MonthPager(
                pagerState = pagerState,
                gridAt = { viewModel.monthGrid(it) },
                today = state.today,
                selected = state.selectedDate,
                onSelect = viewModel::select,
            )
        }
        item {
            MonthEventRibbon(events = grid.events, onSelect = viewModel::select)
        }
        item { DayHero(detail) }
        item {
            DayBar(
                detail = detail,
                onPreviousDay = { viewModel.shiftSelectedDay(-1) },
                onNextDay = { viewModel.shiftSelectedDay(1) },
            )
        }
        if (detail.undefined && !detail.loading) {
            item {
                Text(
                    text = "לא ניתן לחשב זמנים ליום זה במיקום הנבחר.",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(detail.rows, key = { it.kind.name }) { row ->
            CalendarZmanRow(
                row = row,
                hasAlert = row.kind.name in alerted,
                onBellClick = { onCreateZmanAlarm(row.kind.name) },
            )
        }
    }
}

/** The navy hero — the same visual language as the זמנים tab. */
@Composable
private fun DayHero(detail: CalendarViewModel.DayDetail) {
    val ext = Ext.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(ext.heroTop, ext.heroBottom)))
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = detail.hebrewDate,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = "${detail.gregorianDate} · ${detail.locationName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.heroLabel,
                )
            }
        }

        if (detail.headlineLabel != null && detail.headlineTime != null) {
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ext.heroInner)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column {
                    Text(
                        text = detail.headlineLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = ext.heroLabel,
                    )
                    Text(
                        text = detail.headlineTime,
                        style = ZmanListTimeStyle,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        detail.notes.takeIf { it.isNotEmpty() }?.let { notes ->
            Text(
                text = notes.joinToString(" · "),
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = ext.accentGold,
            )
        }
    }
}

/** Weekday plus arrows, so a day can be stepped without returning to the grid. */
@Composable
private fun DayBar(
    detail: CalendarViewModel.DayDetail,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepArrow(forward = false, contentDescription = "היום הקודם", onClick = onPreviousDay)
        Text(
            text = detail.weekdayName,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        StepArrow(forward = true, contentDescription = "היום הבא", onClick = onNextDay)
    }
}

@Composable
private fun CalendarZmanRow(
    row: CalendarViewModel.ZmanRow,
    hasAlert: Boolean,
    onBellClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = Ext.colors
    val background = when {
        row.isNext -> ext.nextRow
        hasAlert -> ext.reminderTint
        else -> cs.surface
    }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(background)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBellClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (hasAlert) Icons.Filled.NotificationsActive
                    else Icons.Filled.NotificationsNone,
                    contentDescription = "צור התראה ל${row.name}",
                    tint = if (hasAlert) ext.accentGold else cs.onSurfaceVariant,
                )
            }
            Text(
                text = row.name,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                color = if (row.isPast) cs.onSurfaceVariant else cs.onSurface,
            )
            Text(
                text = row.time,
                style = ZmanListTimeStyle,
                fontWeight = if (row.isNext) FontWeight.Bold else FontWeight.Normal,
                color = if (row.isPast) cs.onSurfaceVariant else cs.onSurface,
            )
        }
        HorizontalDivider(thickness = 2.dp, color = cs.outlineVariant)
    }
}
