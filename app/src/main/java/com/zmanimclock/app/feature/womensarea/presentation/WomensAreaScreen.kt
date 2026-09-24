package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.calendar.model.HebrewMonthSequence
import com.zmanimclock.app.feature.calendar.presentation.MonthHeader
import com.zmanimclock.app.feature.calendar.presentation.WeekdayRow
import com.zmanimclock.app.feature.womensarea.data.WomensAreaEntryEntity
import com.zmanimclock.app.feature.womensarea.data.WomensAreaEntryType
import com.zmanimclock.app.feature.womensarea.model.PrishaDay
import com.zmanimclock.app.feature.womensarea.model.VesetPrediction
import com.zmanimclock.app.feature.womensarea.model.WomensAreaLabels
import com.zmanimclock.app.feature.womensarea.model.clashesWithTevila
import com.zmanimclock.app.ui.OnWomensAreaLilacContainer
import com.zmanimclock.app.ui.WomensAreaCleanGreen
import com.zmanimclock.app.ui.WomensAreaCountBlue
import com.zmanimclock.app.ui.WomensAreaLilac
import com.zmanimclock.app.ui.WomensAreaLilacContainer
import com.zmanimclock.app.ui.WomensAreaPrishaRed
import com.zmanimclock.app.ui.WomensAreaSpringIcon
import com.zmanimclock.app.ui.WomensAreaTevilaBlue
import com.zmanimclock.app.ui.WomensAreaVesetMarker
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

    val entries by viewModel.allEntries.collectAsStateWithLifecycle()
    val hefsek by viewModel.latestHefsek.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    // The day whose sheet is open, if any.
    var tapped by remember { mutableStateOf<LocalDate?>(null) }

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
            onDayClick = { tapped = it },
        )
        CalendarLegend()

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (entries.isEmpty()) FirstTimeCard()
            Button(
                onClick = { tapped = today },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WomensAreaLilac),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("  רישום להיום", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "או הקישי על כל יום בלוח.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            prediction?.let { PrishaSummaryCard(it) }
            hefsek?.let { TaharaSummaryCard(it, prediction) }
            WomensAreaRemindersCard(reminders = reminders, onChange = viewModel::setReminders)
            OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  היסטוריית רשומות", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    tapped?.let { date ->
        WomensAreaDaySheet(
            date = date,
            today = today,
            entriesOnDay = entries.filter { it.epochDay == date.toEpochDay() },
            previousVeset = entries
                .filter { it.type == WomensAreaEntryType.PERIOD_START && it.epochDay < date.toEpochDay() }
                .maxOfOrNull { it.epochDay }
                ?.let(LocalDate::ofEpochDay),
            vesetOnOrBefore = entries.latestVesetOnOrBefore(date),
            savedReminders = reminders,
            onSaveVeset = { onah ->
                viewModel.addPeriodStart(date, onah)
                tapped = null
            },
            onSaveHefsek = { chosen ->
                viewModel.addHefsekTahara(date, chosen)
                tapped = null
            },
            onDelete = { entry ->
                viewModel.deleteEntry(entry)
                tapped = null
            },
            onDismiss = { tapped = null },
        )
    }
}

/** What each mark on the calendar means — one line, under the grid. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarLegend() {
    val cs = MaterialTheme.colorScheme
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LegendItem("ראייה") { Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(WomensAreaVesetMarker.copy(alpha = 0.5f))) }
        LegendItem("ספירת הימים לחישוב הפרישות") {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(WomensAreaCountBlue), contentAlignment = Alignment.Center) {
                Text("1", color = Color.White, fontSize = 7.5.sp, lineHeight = 8.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        LegendItem("פרישה") { Box(Modifier.size(14.dp).border(2.dp, WomensAreaPrishaRed, RoundedCornerShape(4.dp))) }
        LegendItem("נקיים") { Box(Modifier.size(14.dp).border(2.dp, WomensAreaCleanGreen, RoundedCornerShape(4.dp))) }
        LegendItem("טבילה") { Text("★", color = WomensAreaTevilaBlue, fontSize = 13.sp, lineHeight = 14.sp) }
        LegendItem("היום") { Box(Modifier.size(14.dp).border(2.dp, cs.primary, RoundedCornerShape(4.dp))) }
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        swatch()
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Shown until the first entry: what to do, in one sentence, with the area's own icon. */
@Composable
private fun FirstTimeCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WomensAreaLilacContainer)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(WomensAreaSpringIcon, contentDescription = null, tint = OnWomensAreaLilacContainer, modifier = Modifier.size(32.dp))
        Text(
            "כדי להתחיל, הקישי על היום שבו התחילה הראייה ובחרי \"התחלת ווסת\". ימי הפרישה יסומנו בלוח מעצמם.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnWomensAreaLilacContainer,
        )
    }
}

/** The latest הפסק טהרה, its 7 clean days and the tevila — and a warning if the tevila night is a separation night. */
@Composable
private fun TaharaSummaryCard(hefsek: LocalDate, prediction: VesetPrediction?) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("שבעה נקיים וטבילה", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "הפסק טהרה: ${WomensAreaLabels.hefsekTiming(hefsek)}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            TaharaDetails(hefsek)
            if (prediction?.clashesWithTevila(hefsek) == true) {
                Text(
                    "ליל הטבילה חל ביום פרישה — יש לשאול רב.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = WomensAreaPrishaRed,
                )
            }
        }
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
                        ?: "${WomensAreaLabels.hebrewDate(prediction.sourceStart)} — חסר ביום או בלילה. " +
                        "יש להשלים בהיסטוריית הרשומות (עריכה)"
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )

            prediction.prishaDays.forEach { PrishaRow(it) }

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
private fun PrishaRow(day: PrishaDay) {
    FramedBlock(WomensAreaPrishaRed) {
        Text(
            text = WomensAreaLabels.prishaTitle(day),
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

/** The latest veset recorded on or before [date] — the one a hefsek on [date] ends. */
internal fun List<WomensAreaEntryEntity>.latestVesetOnOrBefore(date: LocalDate): LocalDate? =
    filter { it.type == WomensAreaEntryType.PERIOD_START && it.epochDay <= date.toEpochDay() }
        .maxOfOrNull { it.epochDay }
        ?.let(LocalDate::ofEpochDay)
