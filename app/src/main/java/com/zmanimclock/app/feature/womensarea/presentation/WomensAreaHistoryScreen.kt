package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.womensarea.data.WomensAreaEntryEntity
import com.zmanimclock.app.feature.womensarea.data.WomensAreaEntryType
import com.zmanimclock.app.feature.womensarea.model.Cycle
import com.zmanimclock.app.feature.womensarea.model.HistoryPattern
import com.zmanimclock.app.feature.womensarea.model.Onah
import com.zmanimclock.app.feature.womensarea.model.WomensAreaCalculator
import com.zmanimclock.app.feature.womensarea.model.WomensAreaHistory
import com.zmanimclock.app.feature.womensarea.model.WomensAreaLabels
import com.zmanimclock.app.ui.OnWomensAreaLilacContainer
import com.zmanimclock.app.ui.WomensAreaCleanGreen
import com.zmanimclock.app.ui.WomensAreaLilac
import com.zmanimclock.app.ui.WomensAreaLilacContainer
import com.zmanimclock.app.ui.WomensAreaPrishaRed
import com.zmanimclock.app.ui.WomensAreaTevilaBlue
import java.time.LocalDate

/**
 * היסטוריה ודפוסים — the last [WomensAreaHistory.KEEP] vesets, laid out so a
 * repeating pattern can be SEEN, top to bottom from the quickest look to the
 * full detail:
 *
 *   1. What repeats, said in words (or that nothing does yet).
 *   2. The haflagot as bars — equal lengths line up at a glance; a faint line
 *      marks 30 days, the onah beinonit.
 *   3. The Hebrew day-of-month of each veset in a strip — for יום החודש.
 *   4. Each cycle as a card: what was computed on it (the three separation
 *      days), its hefsek and tevila, and edit / delete.
 *
 * Newest first everywhere, so the three views read in the same order.
 * The screen only SHOWS what repeats. It never names, suggests or decides
 * any status for it — drawing conclusions is hers, with her rabbi, not the
 * app's (the owner's rule).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WomensAreaHistoryScreen(
    onBack: () -> Unit,
    viewModel: WomensAreaViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val entries by viewModel.allEntries.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<WomensAreaEntryEntity?>(null) }
    var deleting by remember { mutableStateOf<WomensAreaEntryEntity?>(null) }
    // A hefsek edit waiting on the early-hefsek notice: the entry, its new date, its day in the count.
    var earlyEdit by remember { mutableStateOf<Triple<WomensAreaEntryEntity, LocalDate, Int>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("היסטוריה ודפוסים") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "${WomensAreaHistory.KEEP} הראיות האחרונות, מהחדשה לישנה. ראייה חדשה מחליפה את הישנה ביותר.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (history.cycles.isEmpty()) {
                item {
                    Text(
                        "עדיין אין ראיות רשומות.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                item { PatternsCard(history.patterns, history.cycles.size) }
                item { HaflagaChart(history.cycles) }
                item { DayOfMonthStrip(history.cycles) }
                item { Text("הראיות", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(history.cycles, key = { it.veset.id }) { cycle ->
                    CycleCard(
                        cycle = cycle,
                        entryOf = { id -> history.entriesById[id] },
                        onEdit = { editing = it },
                        onDelete = { deleting = it },
                    )
                }
            }
            if (history.otherHefseks.isNotEmpty()) {
                item { Text("הפסקים נוספים", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(history.otherHefseks, key = { it.id }) { entry ->
                    EntryActionsRow(
                        label = "הפסק טהרה: ${WomensAreaLabels.hefsekTiming(entry.date)}",
                        onEdit = { editing = entry },
                        onDelete = { deleting = entry },
                    )
                }
            }
        }
    }

    editing?.let { entry ->
        WomensAreaHebrewDateDialog(
            title = entry.type.label,
            initialDate = entry.date,
            askOnah = entry.type == WomensAreaEntryType.PERIOD_START,
            initialOnah = entry.onah,
            today = LocalDate.now(),
            gridAt = viewModel::monthGrid,
            onConfirm = { date, onah ->
                val veset = entries.latestVesetOnOrBefore(date)
                if (entry.type == WomensAreaEntryType.HEFSEK_TAHARA && WomensAreaCalculator.isEarlyHefsek(veset, date)) {
                    // Same notice as when recording one: shown, then saved as usual.
                    earlyEdit = Triple(entry, date, WomensAreaCalculator.hefsekDayNumber(veset, date) ?: 1)
                } else {
                    viewModel.updateEntry(entry, date, onah)
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    earlyEdit?.let { (entry, date, dayNumber) ->
        WomensAreaEarlyHefsekDialog(
            dayNumber = dayNumber,
            onConfirm = {
                viewModel.updateEntry(entry, date, onah = null)
                earlyEdit = null
            },
            onDismiss = { earlyEdit = null },
        )
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text("למחוק את הרשומה?") },
            text = {
                Text(
                    "${entry.type.label}: ${entry.dateLabel}.\nהמחיקה אינה ניתנת לביטול, וכל החישובים יחושבו מחדש בלעדיה.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteEntry(entry); deleting = null }) {
                    Text("מחיקה", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("ביטול") } },
        )
    }
}

// ------------------------------------------------------------ 1. patterns

@Composable
private fun PatternsCard(patterns: List<HistoryPattern>, cycleCount: Int) {
    HistoryCard {
        Text("דפוסים חוזרים", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (patterns.isEmpty()) {
            Text(
                if (cycleCount < WomensAreaHistory.MIN_REPEAT + 1) {
                    "צריך לפחות ${WomensAreaHistory.MIN_REPEAT + 1} ראיות כדי לראות דפוס. עד עכשיו נרשמו $cycleCount."
                } else {
                    "לא נמצא דבר שחוזר ${WomensAreaHistory.MIN_REPEAT} פעמים ברצף."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        patterns.forEach { pattern ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WomensAreaLilacContainer)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Repeat, contentDescription = null, tint = OnWomensAreaLilacContainer, modifier = Modifier.size(20.dp))
                Text(
                    WomensAreaLabels.patternText(pattern),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OnWomensAreaLilacContainer,
                )
            }
        }
        Text(
            "תצוגה בלבד, לעיון שלך. בכל שאלה — יש להתייעץ עם רב.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --------------------------------------------------------- 2. haflaga bars

private val DATE_COLUMN = 92.dp
private const val THIRTY = 30
/** The share of the bar area the longest bar may take — the rest is its number. */
private const val BAR_SPAN = 0.78f

/**
 * One bar per veset, its length the haflaga that led to it. A value that
 * occurs more than once is drawn in full lilac AND marked "↺" beside its
 * number — never colour alone — the rest in pale lilac. The scale starts at
 * zero and runs to the longest haflaga (at least 35), with a faint line at 30.
 */
@Composable
private fun HaflagaChart(cycles: List<Cycle>) {
    val cs = MaterialTheme.colorScheme
    val values = cycles.mapNotNull { it.haflagaInterval }
    val repeated = values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    val scaleMax = maxOf(values.maxOrNull() ?: 0, 35).toFloat()

    HistoryCard {
        Text("הפלגות", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "ימים מכל ראייה לראייה שאחריה, כולל שתיהן. הקו הדק — 30 יום.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
        cycles.forEach { cycle ->
            Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.width(DATE_COLUMN), verticalAlignment = Alignment.CenterVertically) {
                    OnahIcon(cycle.veset.onah)
                    Text(
                        " ${WomensAreaLabels.hebrewDayAndMonth(cycle.veset.date)}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
                val interval = cycle.haflagaInterval
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    // The 30-day reference line.
                    Box(Modifier.fillMaxWidth(THIRTY / scaleMax * BAR_SPAN).height(28.dp), contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.width(1.dp).height(28.dp).background(cs.outlineVariant))
                    }
                    if (interval != null) {
                        val isRepeated = interval in repeated
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .fillMaxWidth(interval / scaleMax * BAR_SPAN)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                    .background(if (isRepeated) WomensAreaLilac else WomensAreaLilac.copy(alpha = 0.4f)),
                            )
                            Text(
                                "  $interval${if (isRepeated) " ↺" else ""}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isRepeated) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    } else {
                        Text(
                            "— הראשונה בהיסטוריה",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------- 3. day of the month

/** The Hebrew day-of-month of each veset, newest first; the same day in consecutive months is joined by "=". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayOfMonthStrip(cycles: List<Cycle>) {
    HistoryCard {
        Text("תאריך בחודש", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "באיזה יום בחודש העברי הופיעה כל ראייה — לבדיקת יום החודש.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cycles.forEachIndexed { i, cycle ->
                val sameAsNext = cycles.getOrNull(i + 1)?.hebrewDayOfMonth == cycle.hebrewDayOfMonth
                val sameAsPrev = cycles.getOrNull(i - 1)?.hebrewDayOfMonth == cycle.hebrewDayOfMonth
                val highlight = sameAsNext || sameAsPrev
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (highlight) WomensAreaLilacContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .then(if (highlight) Modifier.border(1.5.dp, WomensAreaLilac, RoundedCornerShape(10.dp)) else Modifier)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OnahIcon(cycle.veset.onah)
                    Text(
                        " ${WomensAreaLabels.hebrewNumber(cycle.hebrewDayOfMonth)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                if (sameAsNext) {
                    Text("=", modifier = Modifier.align(Alignment.CenterVertically), color = WomensAreaLilac, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ------------------------------------------------------------ 4. cycles

@Composable
private fun CycleCard(
    cycle: Cycle,
    entryOf: (Long) -> WomensAreaEntryEntity?,
    onEdit: (WomensAreaEntryEntity) -> Unit,
    onDelete: (WomensAreaEntryEntity) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var open by remember(cycle.veset.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OnahIcon(cycle.veset.onah)
                        Text(
                            " ${WomensAreaLabels.hebrewDate(cycle.veset.date)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        cycle.veset.onah?.let { WomensAreaLabels.onahTiming(cycle.veset.date, it) } ?: "לא צוין ביום או בלילה",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                cycle.haflagaInterval?.let {
                    Text(
                        "הפלגה $it",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(WomensAreaLilacContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = OnWomensAreaLilacContainer,
                    )
                }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "סגירה" else "פירוט")
            }
            if (open) {
                Text("ימי הפרישה שחושבו מראייה זו:", style = MaterialTheme.typography.labelLarge)
                cycle.prediction.prishaDays.forEach { day ->
                    FramedBlock(WomensAreaPrishaRed) {
                        Text(WomensAreaLabels.prishaTitle(day), fontWeight = FontWeight.Bold, color = WomensAreaPrishaRed)
                        Text(
                            day.onah?.let { WomensAreaLabels.onahTiming(day.date, it) } ?: WomensAreaLabels.hebrewDate(day.date),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (cycle.prediction.haflaga == null) {
                    Text("הפלגה: אין ראייה קודמת בהיסטוריה.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                if (cycle.prediction.yomHachodeshMissing) {
                    Text("יום החודש: הראייה בל׳, ולחודש הבא אין ל׳.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                cycle.hefsek?.let { hefsek ->
                    FramedBlock(WomensAreaCleanGreen) {
                        Text("הפסק טהרה", fontWeight = FontWeight.Bold, color = WomensAreaCleanGreen)
                        Text(WomensAreaLabels.hefsekTiming(hefsek.date), style = MaterialTheme.typography.bodySmall)
                        Text(
                            "★ טבילה: ${WomensAreaLabels.tevilaTiming(WomensAreaCalculator.tevilaDay(hefsek.date))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = WomensAreaTevilaBlue,
                        )
                    }
                    WomensAreaCalculator.tevilaNightBlock(hefsek.date)?.let { TevilaBlockWarning(it) }
                }
                entryOf(cycle.veset.id)?.let { entry ->
                    EntryActionsRow("הראייה", onEdit = { onEdit(entry) }, onDelete = { onDelete(entry) })
                }
                cycle.hefsek?.let { entryOf(it.id) }?.let { entry ->
                    EntryActionsRow("ההפסק", onEdit = { onEdit(entry) }, onDelete = { onDelete(entry) })
                }
            }
        }
    }
}

@Composable
private fun EntryActionsRow(label: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "עריכה") }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "מחיקה") }
    }
}

@Composable
private fun HistoryCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun OnahIcon(onah: Onah?, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    when (onah) {
        Onah.DAY -> Icon(Icons.Filled.WbSunny, contentDescription = "ביום", tint = tint, modifier = Modifier.size(14.dp))
        Onah.NIGHT -> Icon(Icons.Filled.NightsStay, contentDescription = "בלילה", tint = tint, modifier = Modifier.size(14.dp))
        null -> Text("?", color = tint)
    }
}

private val WomensAreaEntryEntity.date: LocalDate get() = LocalDate.ofEpochDay(epochDay)

private val WomensAreaEntryType.label: String
    get() = when (this) {
        WomensAreaEntryType.PERIOD_START -> "התחלת ווסת"
        WomensAreaEntryType.HEFSEK_TAHARA -> "הפסק טהרה"
    }

/** "ליל חמישי ט״ו ניסן — הערב של …" for a veset with its onah; "יום … לפני השקיעה" for a hefsek. */
private val WomensAreaEntryEntity.dateLabel: String
    get() {
        val onah = onah
        return when {
            type == WomensAreaEntryType.PERIOD_START && onah != null -> WomensAreaLabels.onahTiming(date, onah)
            type == WomensAreaEntryType.PERIOD_START -> "${WomensAreaLabels.hebrewDate(date)} — לא צוין ביום או בלילה"
            else -> WomensAreaLabels.hefsekTiming(date)
        }
    }
