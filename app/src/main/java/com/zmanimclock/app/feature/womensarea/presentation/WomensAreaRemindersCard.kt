package com.zmanimclock.app.feature.womensarea.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zmanimclock.app.feature.womensarea.model.NotificationText
import com.zmanimclock.app.feature.womensarea.model.WomensAreaNotificationText
import com.zmanimclock.app.feature.womensarea.model.WomensAreaReminderTimes
import com.zmanimclock.app.feature.womensarea.security.WomensAreaReminders
import com.zmanimclock.app.ui.OnWomensAreaLilacContainer
import com.zmanimclock.app.ui.WomensAreaLilac
import com.zmanimclock.app.ui.WomensAreaLilacContainer
import com.zmanimclock.app.ui.WomensAreaSpringIcon
import java.time.LocalTime

/**
 * The reminders card on the area's main screen — the same two choices she is
 * offered when she records a הפסק טהרה ([ReminderChoices]), for changing them
 * later. Lives inside the area, behind the device lock — never in the app's
 * Settings or its מעורר tab.
 */
@Composable
fun WomensAreaRemindersCard(reminders: WomensAreaReminders, onChange: (WomensAreaReminders) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("התראות", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ReminderChoices(reminders, onChange)
        }
    }
}

/**
 * The two reminder switches, their times, and a preview of what the
 * notification will actually say — so she sees before choosing that it
 * reads only "התראה אישית · יום 3".
 */
@Composable
fun ReminderChoices(reminders: WomensAreaReminders, onChange: (WomensAreaReminders) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ReminderToggle(
            title = "בכל אחד משבעת הימים",
            enabled = reminders.cleanEnabled,
            times = reminders.cleanTimes,
            defaultPickerTime = LocalTime.of(8, 0),
            onEnabledChange = { onChange(reminders.copy(cleanEnabled = it)) },
            onTimesChange = { onChange(reminders.copy(cleanTimes = it)) },
        )
        HorizontalDivider()
        ReminderToggle(
            title = "בערב היום השביעי",
            enabled = reminders.tevilaEnabled,
            times = reminders.tevilaTimes,
            defaultPickerTime = LocalTime.of(16, 0),
            onEnabledChange = { onChange(reminders.copy(tevilaEnabled = it)) },
            onTimesChange = { onChange(reminders.copy(tevilaTimes = it)) },
        )
        if (reminders.cleanEnabled || reminders.tevilaEnabled) {
            NotificationPreview(
                if (reminders.cleanEnabled) WomensAreaNotificationText.cleanDay(3)
                else WomensAreaNotificationText.tevilaEvening(),
            )
        }
    }
}

/**
 * One reminder kind: a switch, and once on, its times as removable chips plus
 * "הוספת שעה" (up to [WomensAreaReminderTimes.MAX]).
 *
 * Turning it on asks for the notification permission first on Android 13+,
 * and stays off if it is refused — a switch that says "on" while nothing can
 * ever be shown would be worse than no switch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderToggle(
    title: String,
    enabled: Boolean,
    times: List<LocalTime>,
    defaultPickerTime: LocalTime,
    onEnabledChange: (Boolean) -> Unit,
    onTimesChange: (List<LocalTime>) -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onEnabledChange(true) }
    var picking by remember { mutableStateOf(false) }

    fun toggle(on: Boolean) {
        val needsPermission = on &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else onEnabledChange(on)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(WomensAreaLilacContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = OnWomensAreaLilacContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text("התראה $title", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (enabled) "בשעות:" else "כבוי",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = ::toggle)
        }
        if (enabled) {
            FlowRow(
                modifier = Modifier.padding(start = 46.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                times.forEach { time ->
                    InputChip(
                        selected = false,
                        onClick = { onTimesChange(times - time) },
                        label = { Text(WomensAreaReminderTimes.format(time)) },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, contentDescription = "הסרת השעה", modifier = Modifier.size(16.dp))
                        },
                    )
                }
                if (times.size < WomensAreaReminderTimes.MAX) {
                    AssistChip(
                        onClick = { picking = true },
                        label = { Text("הוספת שעה") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
            if (times.isEmpty()) {
                Text(
                    "לא נבחרה שעה — לא תגיע התראה.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 46.dp),
                )
            }
        }
    }

    if (picking) {
        ReminderTimePickerDialog(
            initial = defaultPickerTime,
            onConfirm = { time ->
                onTimesChange((times + time).distinct().sorted())
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * A drawn copy of the notification as the system will show it — the area's
 * spring in lilac, the app's name, then exactly the words the notifier posts.
 */
@Composable
private fun NotificationPreview(content: NotificationText) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "כך תיראה ההתראה — בלי פירוט, ובמסך הנעילה רק \"${WomensAreaNotificationText.TITLE}\":",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier.size(18.dp).clip(CircleShape).background(WomensAreaLilac),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(WomensAreaSpringIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    Text(
                        "Halacha Clock · עכשיו",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(content.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(content.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("שעת התראה") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("הוספה") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}
