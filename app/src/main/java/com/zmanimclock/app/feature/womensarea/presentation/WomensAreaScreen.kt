package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.calendar.model.HebrewMonthSequence
import com.zmanimclock.app.feature.calendar.presentation.MonthHeader
import com.zmanimclock.app.feature.calendar.presentation.WeekdayRow
import com.zmanimclock.app.ui.WomensAreaLilac
import java.time.LocalDate

/**
 * The Women's Area's real content — reached only after the security gate.
 * MonthHeader/WeekdayRow are the actual composables from the main Calendar
 * tab, reused unmodified; only the pager/day-cell are this feature's own
 * (see WomensAreaMonthGridView's doc for why).
 */
@Composable
fun WomensAreaScreen(
    onOpenHistory: () -> Unit,
    viewModel: WomensAreaViewModel = hiltViewModel(),
) {
    val today = LocalDate.now()
    val markersByDate by viewModel.markersByDate.collectAsStateWithLifecycle()

    var visibleMonthIndex by remember { mutableStateOf(HebrewMonthSequence.indexOf(today)) }
    val pagerState = rememberPagerState(
        initialPage = HebrewMonthSequence.clamp(visibleMonthIndex),
        pageCount = { HebrewMonthSequence.size },
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { visibleMonthIndex = it }
    }

    var showPeriodStartDialog by remember { mutableStateOf(false) }
    var showFirstCleanDayDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(bottom = 24.dp)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MonthHeader(
            grid = viewModel.monthGrid(HebrewMonthSequence.clamp(visibleMonthIndex)),
            showTodayButton = visibleMonthIndex != HebrewMonthSequence.indexOf(today),
            onPrevious = { visibleMonthIndex = (visibleMonthIndex - 1).coerceAtLeast(0) },
            onNext = { visibleMonthIndex = (visibleMonthIndex + 1).coerceAtMost(HebrewMonthSequence.size - 1) },
            onToday = { visibleMonthIndex = HebrewMonthSequence.indexOf(today) },
            onTitleClick = {},
        )
        WeekdayRow()
        WomensAreaMonthPager(
            pagerState = pagerState,
            gridAt = viewModel::monthGrid,
            markersAt = { markersByDate[it] },
            today = today,
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = { showPeriodStartDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WomensAreaLilac),
            ) {
                Text("תחילת ראייה")
            }
            OutlinedButton(
                onClick = { showFirstCleanDayDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("יום ראשון לנקיים")
            }
            OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  היסטוריית רשומות", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (showPeriodStartDialog) {
        WomensAreaDateEntryDialog(
            title = "תחילת ראייה",
            onConfirm = { date ->
                viewModel.addPeriodStart(date)
                showPeriodStartDialog = false
            },
            onDismiss = { showPeriodStartDialog = false },
        )
    }
    if (showFirstCleanDayDialog) {
        WomensAreaDateEntryDialog(
            title = "יום ראשון לנקיים",
            onConfirm = { date ->
                viewModel.addFirstCleanDay(date)
                showFirstCleanDayDialog = false
            },
            onDismiss = { showFirstCleanDayDialog = false },
        )
    }
}
