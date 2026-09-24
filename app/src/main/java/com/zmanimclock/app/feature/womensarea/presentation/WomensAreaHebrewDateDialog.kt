package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.zmanimclock.app.feature.calendar.model.CalendarDayMeta
import com.zmanimclock.app.feature.calendar.model.HebrewMonthSequence
import com.zmanimclock.app.feature.calendar.model.MonthGrid
import com.zmanimclock.app.feature.calendar.presentation.MonthHeader
import com.zmanimclock.app.feature.calendar.presentation.WeekdayRow
import com.zmanimclock.app.feature.womensarea.model.Onah
import com.zmanimclock.app.feature.womensarea.model.WomensAreaLabels
import com.zmanimclock.app.feature.womensarea.model.WomensAreaLabels.hebrewName
import com.zmanimclock.app.ui.WomensAreaLilac
import com.zmanimclock.app.ui.WomensAreaLilacContainer
import com.zmanimclock.app.ui.OnWomensAreaLilacContainer
import java.time.LocalDate

/**
 * Picks ONE HEBREW DAY on a Hebrew month grid — never a Gregorian date — and,
 * for a veset, whether it was ביום or בלילה.
 *
 * Why not the stock Gregorian DatePicker this replaced: a veset seen after
 * shkia belongs to the NEXT Hebrew day. Picking "Tuesday" on a civil picker
 * for a Tuesday-evening sighting silently stored the wrong day, and every
 * separation day computed from it was off by one. Here the woman picks the
 * Hebrew date itself (ליל ט״ו is on the ט״ו cell), and the ⓘ note says so
 * where she makes the choice.
 *
 * Days more than one civil day ahead are not selectable: tonight after shkia
 * is already tomorrow's Hebrew date, but nothing later can have happened yet.
 */
@Composable
fun WomensAreaHebrewDateDialog(
    title: String,
    initialDate: LocalDate,
    askOnah: Boolean,
    initialOnah: Onah?,
    today: LocalDate,
    gridAt: (Int) -> MonthGrid,
    onConfirm: (date: LocalDate, onah: Onah?) -> Unit,
    onDismiss: () -> Unit,
) {
    val latestSelectable = today.plusDays(1)
    // Not coerceAtMost: LocalDate is Comparable<ChronoLocalDate>, so that would
    // widen the state's type to ChronoLocalDate.
    var selected by remember { mutableStateOf(if (initialDate > latestSelectable) latestSelectable else initialDate) }
    var onah by remember { mutableStateOf(initialOnah) }
    var monthIndex by remember { mutableIntStateOf(HebrewMonthSequence.clamp(HebrewMonthSequence.indexOf(selected))) }
    val todayIndex = HebrewMonthSequence.indexOf(today)
    val canConfirm = !askOnah || onah != null

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MonthHeader(
                    grid = gridAt(monthIndex),
                    showTodayButton = monthIndex != todayIndex,
                    onPrevious = { monthIndex = HebrewMonthSequence.clamp(monthIndex - 1) },
                    onNext = { monthIndex = HebrewMonthSequence.clamp(monthIndex + 1) },
                    onToday = { monthIndex = HebrewMonthSequence.clamp(todayIndex) },
                    onTitleClick = {},
                )
                WeekdayRow()
                PickerGrid(
                    grid = gridAt(monthIndex),
                    selected = selected,
                    today = today,
                    latestSelectable = latestSelectable,
                    onSelect = { selected = it },
                )

                Text(
                    text = "יום ${WomensAreaLabels.weekdayName(selected)}, ${WomensAreaLabels.hebrewDate(selected)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                if (askOnah) {
                    Text("מתי התחילה הראייה?", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Onah.entries.forEach { option ->
                            FilterChip(
                                selected = onah == option,
                                onClick = { onah = option },
                                label = { Text(option.hebrewName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = WomensAreaLilac,
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }
                    onah?.let {
                        Text(
                            text = WomensAreaLabels.onahTiming(selected, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HebrewDayInfoNote()
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canConfirm, onClick = { onConfirm(selected, if (askOnah) onah else null) }) {
                Text("אישור")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/**
 * The ⓘ note: the Hebrew day starts at the evening before it. This is the one
 * fact a veset entry most easily gets wrong, so it sits right where the
 * choice is made rather than in a help screen.
 */
@Composable
private fun HebrewDayInfoNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WomensAreaLilacContainer)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = OnWomensAreaLilacContainer,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "היום העברי מתחיל בערב שלפניו, מהשקיעה. ראייה אחרי השקיעה שייכת " +
                "ליום העברי הבא: \"ליל שלישי\" הוא הערב של יום שני, אחרי השקיעה. " +
                "לכן בוחרים בלוח את התאריך העברי, ואז \"בלילה\".\n" +
                "ימי הפרישה (עונה בינונית, הפלגה, יום החודש) נספרים לפי התאריכים " +
                "העבריים שבלוח, ובאותה עונה — ביום או בלילה — שבה התחילה הראייה.",
            style = MaterialTheme.typography.bodySmall,
            color = OnWomensAreaLilacContainer,
        )
    }
}

private val PICKER_CELL_HEIGHT = 44.dp

/** Only the displayed Hebrew month's own days — neighbouring months' filler cells stay blank. */
@Composable
private fun PickerGrid(
    grid: MonthGrid,
    selected: LocalDate,
    today: LocalDate,
    latestSelectable: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        grid.weeks.forEach { week ->
            // A 5-row month's all-filler sixth row would only add empty space here.
            if (week.none { it.isInDisplayedMonth }) return@forEach
            Row(Modifier.fillMaxWidth()) {
                week.forEach { meta ->
                    if (!meta.isInDisplayedMonth) {
                        Spacer(Modifier.weight(1f).height(PICKER_CELL_HEIGHT))
                    } else {
                        PickerCell(
                            meta = meta,
                            isSelected = meta.date == selected,
                            isToday = meta.date == today,
                            enabled = meta.date <= latestSelectable,
                            onClick = { onSelect(meta.date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerCell(
    meta: CalendarDayMeta,
    isSelected: Boolean,
    isToday: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val textColor = when {
        isSelected -> Color.White
        !enabled -> cs.onSurfaceVariant.copy(alpha = 0.38f)
        else -> cs.onSurface
    }
    Box(
        modifier = modifier
            .height(PICKER_CELL_HEIGHT)
            .padding(1.dp)
            .clip(shape)
            .background(if (isSelected) WomensAreaLilac else Color.Transparent)
            .then(if (isToday && !isSelected) Modifier.border(2.dp, cs.primary, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Hebrew and Gregorian numerals in separate Text composables, for
            // the same bidi reason as the Calendar tab's own cell.
            Text(meta.hebrewDayLabel, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, color = textColor)
            Text(meta.gregorianDayLabel, fontSize = 9.sp, lineHeight = 10.sp, color = textColor.copy(alpha = 0.7f))
        }
    }
}
