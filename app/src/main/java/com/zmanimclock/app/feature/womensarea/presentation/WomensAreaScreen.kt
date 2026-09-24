package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.calendar.model.HebrewMonthSequence
import com.zmanimclock.app.feature.calendar.presentation.MonthHeader
import com.zmanimclock.app.feature.calendar.presentation.WeekdayRow
import com.zmanimclock.app.feature.womensarea.model.PrishaDay
import com.zmanimclock.app.feature.womensarea.model.VesetKind
import com.zmanimclock.app.feature.womensarea.model.VesetPrediction
import com.zmanimclock.app.feature.womensarea.model.WomensAreaLabels
import com.zmanimclock.app.feature.womensarea.model.WomensAreaLabels.hebrewName
import com.zmanimclock.app.ui.WomensAreaLilac
import com.zmanimclock.app.ui.WomensAreaPrishaRed
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
    val prediction by viewModel.latestPrediction.collectAsStateWithLifecycle()

    var visibleMonthIndex by remember { mutableIntStateOf(HebrewMonthSequence.clamp(HebrewMonthSequence.indexOf(today))) }
    val pagerState = rememberPagerState(
        initialPage = visibleMonthIndex,
        pageCount = { HebrewMonthSequence.size },
    )
    // The pager tells the header which month is on screen, and the header's
    // arrows move the pager — guarded on inequality so they cannot loop.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { visibleMonthIndex = it }
    }
    LaunchedEffect(visibleMonthIndex) {
        if (pagerState.currentPage != visibleMonthIndex) pagerState.animateScrollToPage(visibleMonthIndex)
    }

    // The day a new entry starts from — tap any cell to change it.
    var selected by remember { mutableStateOf(today) }
    var showVesetDialog by remember { mutableStateOf(false) }
    var showFirstCleanDayDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(bottom = 24.dp)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MonthHeader(
            grid = viewModel.monthGrid(visibleMonthIndex),
            showTodayButton = visibleMonthIndex != HebrewMonthSequence.indexOf(today),
            onPrevious = { visibleMonthIndex = HebrewMonthSequence.clamp(visibleMonthIndex - 1) },
            onNext = { visibleMonthIndex = HebrewMonthSequence.clamp(visibleMonthIndex + 1) },
            onToday = { visibleMonthIndex = HebrewMonthSequence.clamp(HebrewMonthSequence.indexOf(today)) },
            onTitleClick = {},
        )
        WeekdayRow()
        WomensAreaMonthPager(
            pagerState = pagerState,
            gridAt = viewModel::monthGrid,
            markersAt = { markersByDate[it] },
            today = today,
            selected = selected,
            onSelect = { selected = it },
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            prediction?.let { PrishaSummaryCard(it) }

            Text(
                text = "יום נבחר: ${WomensAreaLabels.weekdayName(selected)}, " +
                    "${WomensAreaLabels.hebrewDate(selected)} — הקישי על יום בלוח כדי לבחור אחר",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { showVesetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WomensAreaLilac),
            ) {
                Text("התחלת ווסת")
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

    if (showVesetDialog) {
        WomensAreaHebrewDateDialog(
            title = "התחלת ווסת",
            initialDate = selected,
            askOnah = true,
            initialOnah = null,
            today = today,
            gridAt = viewModel::monthGrid,
            onConfirm = { date, onah ->
                if (onah != null) viewModel.addPeriodStart(date, onah)
                showVesetDialog = false
            },
            onDismiss = { showVesetDialog = false },
        )
    }
    if (showFirstCleanDayDialog) {
        WomensAreaHebrewDateDialog(
            title = "יום ראשון לנקיים",
            initialDate = selected,
            askOnah = false,
            initialOnah = null,
            today = today,
            gridAt = viewModel::monthGrid,
            onConfirm = { date, _ ->
                viewModel.addFirstCleanDay(date)
                showFirstCleanDayDialog = false
            },
            onDismiss = { showFirstCleanDayDialog = false },
        )
    }
}

/** Every separation day of the latest veset, spelled out in full, with the cases that have none. */
@Composable
private fun PrishaSummaryCard(prediction: VesetPrediction) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ימי פרישה", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "מהראייה של " + (
                    prediction.onah?.let { WomensAreaLabels.onahTiming(prediction.sourceStart, it) }
                        ?: "${WomensAreaLabels.hebrewDate(prediction.sourceStart)} — לא צוין ביום או בלילה; " +
                        "אפשר להשלים בהיסטוריית הרשומות"
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )

            prediction.prishaDays.forEach { PrishaRow(it, prediction) }

            if (prediction.haflaga == null) {
                MissingRow("הפלגה", "אין ראייה קודמת רשומה לחשב ממנה הפלגה")
            }
            if (prediction.yomHachodeshMissing) {
                MissingRow("יום החודש", "הראייה הייתה בל׳, ולחודש הבא אין ל׳ — יש לברר עם רב")
            }

            Text(
                text = "הספירה נעשית לפי התאריכים העבריים שבלוח. החישוב הוא עזר לספירה בלבד; בכל שאלה יש לשאול רב.",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrishaRow(day: PrishaDay, prediction: VesetPrediction) {
    val title = when (day.kind) {
        VesetKind.HAFLAGA -> "${day.kind.hebrewName} (${prediction.haflagaInterval} יום)"
        else -> day.kind.hebrewName
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, WomensAreaPrishaRed, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "$title — יום ${day.dayNumber} לספירה",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = WomensAreaPrishaRed,
        )
        Text(
            text = day.onah?.let { WomensAreaLabels.onahTiming(day.date, it) }
                ?: "${WomensAreaLabels.hebrewDate(day.date)} — ביום או בלילה לפי הראייה (לא צוין)",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun MissingRow(title: String, reason: String) {
    Text(
        text = "$title: $reason",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
