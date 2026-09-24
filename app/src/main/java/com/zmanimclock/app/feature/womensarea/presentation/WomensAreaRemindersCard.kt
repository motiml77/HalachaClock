package com.zmanimclock.app.feature.womensarea.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zmanimclock.app.feature.womensarea.model.WomensAreaReminderTimes
import java.time.LocalTime

/** Which of the two reminder kinds a section edits. */
data class ReminderSectionState(
    val enabled: Boolean,
    val times: List<LocalTime>,
    val onEnabledChange: (Boolean) -> Unit,
    val onAddTime: (LocalTime) -> Unit,
    val onRemoveTime: (LocalTime) -> Unit,
)

/**
 * The reminders she can choose, each off until she turns it on and then at
 * the times of day she picks (up to [WomensAreaReminderTimes.MAX] each):
 * the clean days, and ערב הטבילה (the 7th day, before its tzeit). Lives
 * inside the area, behind the device lock — never in the app's Settings or
 * its מעורר tab.
 *
 * Turning either on asks for the notification permission first on Android
 * 13+, and stays off if it is refused — a switch that says "on" while nothing
 * can ever be shown would be worse than no switch.
 */
@Composable
fun WomensAreaRemindersCard(clean: ReminderSectionState, tevila: ReminderSectionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("תזכורות", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "רק כאן, באיזור הנשי — לא במעורר. במסך הנעילה מופיע רק \"תזכורת\", בלי פירוט.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReminderSection(
                title = "בימי הנקיים",
                subtitle = "בכל אחד משבעת הימים, בשעות שתבחרי",
                state = clean,
                defaultPickerTime = LocalTime.of(8, 0),
            )
            HorizontalDivider()
            ReminderSection(
                title = "בערב הטבילה",
                subtitle = "ביום השביעי, לפני צאת הכוכבים — להתכונן",
                state = tevila,
                defaultPickerTime = LocalTime.of(16, 0),
            )
        }
    }
}

@Composable
private fun ReminderSection(
    title: String,
    subtitle: String,
    state: ReminderSectionState,
    defaultPickerTime: LocalTime,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) state.onEnabledChange(true) }
    var picking by remember { mutableStateOf(false) }

    fun toggle(on: Boolean) {
        val needsPermission = on &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            state.onEnabledChange(on)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = state.enabled, onCheckedChange = ::toggle)
    }
    if (state.enabled) {
        state.times.forEach { time ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    WomensAreaReminderTimes.format(time),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { state.onRemoveTime(time) }) {
                    Icon(Icons.Filled.Close, contentDescription = "הסרת השעה", modifier = Modifier.size(18.dp))
                }
            }
        }
        if (state.times.isEmpty()) {
            Text(
                "לא נבחרה שעה — לא תגיע תזכורת.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.times.size < WomensAreaReminderTimes.MAX) {
            TextButton(onClick = { picking = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" הוספת שעה")
            }
        }
    }

    if (picking) {
        ReminderTimePickerDialog(
            initial = defaultPickerTime,
            onConfirm = { time ->
                state.onAddTime(time)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("שעת תזכורת") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("הוספה") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}
