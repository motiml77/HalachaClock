package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A single date entry — used for both "תחילת ראייה" and "יום ראשון לנקיים".
 * No Hebrew-date entry UI exists anywhere in this codebase to build on, so
 * this uses Compose Material3's stock Gregorian DatePicker; the Hebrew date
 * stays the PRIMARY display everywhere else in this feature, this is only
 * the entry seam.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WomensAreaDateEntryDialog(
    title: String,
    initialDate: LocalDate = LocalDate.now(),
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } else {
                        onDismiss()
                    }
                },
            ) { Text("אישור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    ) {
        DatePicker(state = state, title = { Text(title) })
    }
}
