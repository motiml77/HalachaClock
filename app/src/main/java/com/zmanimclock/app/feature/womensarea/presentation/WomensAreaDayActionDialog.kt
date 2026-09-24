package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zmanimclock.app.feature.womensarea.data.WomensAreaEntryEntity
import com.zmanimclock.app.feature.womensarea.data.WomensAreaEntryType
import com.zmanimclock.app.feature.womensarea.model.Onah
import com.zmanimclock.app.feature.womensarea.model.WomensAreaCalculator
import com.zmanimclock.app.feature.womensarea.model.WomensAreaLabels
import com.zmanimclock.app.feature.womensarea.model.WomensAreaLabels.hebrewName
import com.zmanimclock.app.ui.WomensAreaCleanGreen
import com.zmanimclock.app.ui.WomensAreaLilac
import java.time.LocalDate

private enum class Step { CHOOSE, VESET, HEFSEK }

/**
 * What opens when a day on the calendar is tapped: record a התחלת ווסת on it
 * (then ביום or בלילה), or a הפסק טהרה (before shkia), or delete what is
 * already recorded on it.
 *
 * WHICH DAYS CAN TAKE WHICH ENTRY
 * A veset can be recorded up to tomorrow's Hebrew date: tonight after shkia
 * already IS tomorrow's date (ליל …), and its cell is tomorrow's. A hefsek is
 * made before shkia, so only a day that has already begun — today or earlier.
 */
@Composable
fun WomensAreaDayActionDialog(
    date: LocalDate,
    today: LocalDate,
    entriesOnDay: List<WomensAreaEntryEntity>,
    onVeset: (Onah) -> Unit,
    onHefsek: () -> Unit,
    onDelete: (WomensAreaEntryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(Step.CHOOSE) }
    var onah by remember { mutableStateOf<Onah?>(null) }
    val canVeset = date <= today.plusDays(1)
    val canHefsek = date <= today

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("יום ${WomensAreaLabels.weekdayName(date)}, ${WomensAreaLabels.hebrewDate(date)}")
                Text(
                    "${date.dayOfMonth}.${date.monthValue}.${date.year}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (step) {
                    Step.CHOOSE -> {
                        if (!canVeset) {
                            Text("אפשר לרשום רק ימים שכבר הגיעו.", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (canVeset) {
                            Button(
                                onClick = { step = Step.VESET },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = WomensAreaLilac),
                            ) { Text("התחלת ווסת") }
                        }
                        if (canHefsek) {
                            OutlinedButton(onClick = { step = Step.HEFSEK }, modifier = Modifier.fillMaxWidth()) {
                                Text("הפסק טהרה (לפני שקיעה)")
                            }
                        }
                        entriesOnDay.forEach { entry -> RecordedEntryRow(entry, onDelete = { onDelete(entry) }) }
                    }

                    Step.VESET -> {
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
                                text = WomensAreaLabels.onahTiming(date, it),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        HebrewDayInfoNote()
                    }

                    Step.HEFSEK -> {
                        val clean = WomensAreaCalculator.cleanDayDates(date)
                        Text(
                            "הפסק טהרה: ${WomensAreaLabels.hefsekTiming(date)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "שבעה נקיים: מיום ${WomensAreaLabels.weekdayName(clean.first())} " +
                                "${WomensAreaLabels.hebrewDayAndMonth(clean.first())} עד יום " +
                                "${WomensAreaLabels.weekdayName(clean.last())} ${WomensAreaLabels.hebrewDayAndMonth(clean.last())}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WomensAreaCleanGreen,
                        )
                        Text(
                            "★ טבילה: ${WomensAreaLabels.tevilaTiming(WomensAreaCalculator.tevilaDay(date))}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "שבעה נקיים מתחילים ביום העברי שאחרי ההפסק — מצאת הכוכבים של אותו ערב.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                Step.CHOOSE -> TextButton(onClick = onDismiss) { Text("סגירה") }
                Step.VESET -> TextButton(enabled = onah != null, onClick = { onah?.let(onVeset) }) { Text("שמירה") }
                Step.HEFSEK -> TextButton(onClick = onHefsek) { Text("שמירה") }
            }
        },
        dismissButton = {
            if (step != Step.CHOOSE) TextButton(onClick = { step = Step.CHOOSE }) { Text("חזרה") }
        },
    )
}

@Composable
private fun RecordedEntryRow(entry: WomensAreaEntryEntity, onDelete: () -> Unit) {
    val label = when (entry.type) {
        WomensAreaEntryType.PERIOD_START -> "רשום: התחלת ווסת" + (entry.onah?.let { " ${it.hebrewName}" } ?: "")
        WomensAreaEntryType.HEFSEK_TAHARA -> "רשום: הפסק טהרה"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(" מחיקה")
        }
    }
}
