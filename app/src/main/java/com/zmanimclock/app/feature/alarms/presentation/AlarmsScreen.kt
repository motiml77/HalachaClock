package com.zmanimclock.app.feature.alarms.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.alarms.data.AlarmType
import com.zmanimclock.app.feature.zmanim.model.ZmanKind

private val DAY_LETTERS = listOf("א", "ב", "ג", "ד", "ה", "ו", "ש")

/**
 * The alarms tab: one list for both regular (fixed-time) and zman-anchored
 * alarms. FAB opens a type chooser; rows navigate to the edit screen.
 */
@Composable
fun AlarmsScreen(
    onCreateAlarm: (AlarmType) -> Unit,
    onCreateShabbatAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    viewModel: AlarmsViewModel = hiltViewModel(),
) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    var showTypeChooser by remember { mutableStateOf(false) }

    AlarmsContent(
        alarms = alarms,
        onToggle = viewModel::toggleAlarm,
        onDelete = viewModel::deleteAlarm,
        onAddClick = { showTypeChooser = true },
        onEditAlarm = onEditAlarm,
    )

    if (showTypeChooser) {
        AlarmTypeChooserSheet(
            onChoose = { type ->
                showTypeChooser = false
                onCreateAlarm(type)
            },
            onChooseShabbat = {
                showTypeChooser = false
                onCreateShabbatAlarm()
            },
            onDismiss = { showTypeChooser = false },
        )
    }
}

@Composable
fun AlarmsContent(
    alarms: List<AlarmEntity>,
    onToggle: (AlarmEntity, Boolean) -> Unit,
    onDelete: (AlarmEntity) -> Unit,
    onAddClick: () -> Unit,
    onEditAlarm: (Long) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (alarms.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Alarm,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.height(12.dp))
                Text("אין שעונים מעוררים", style = MaterialTheme.typography.titleMedium)
                Text(
                    "הוסף שעון רגיל או שעון לפי זמן הלכתי",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmCard(alarm, onToggle, onDelete, onClick = { onEditAlarm(alarm.id) })
                }
            }
        }

        FloatingActionButton(
            onClick = onAddClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "הוסף שעון מעורר")
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: AlarmEntity,
    onToggle: (AlarmEntity, Boolean) -> Unit,
    onDelete: (AlarmEntity) -> Unit,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (alarm.shabbatMode) {
                Text("🕯️", style = MaterialTheme.typography.headlineSmall)
            } else {
                Icon(
                    imageVector = if (alarm.type == AlarmType.ZMAN) Icons.Filled.WbTwilight else Icons.Filled.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                // Big anchor line
                Text(
                    text = when (alarm.type) {
                        AlarmType.FIXED -> "%02d:%02d".format(alarm.hour, alarm.minute)
                        AlarmType.ZMAN -> zmanAnchorText(alarm)
                    },
                    style = if (alarm.type == AlarmType.FIXED) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.Bold,
                )
                if (alarm.label.isNotBlank()) {
                    Text(alarm.label, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = daysText(alarm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            IconButton(onClick = { onDelete(alarm) }) {
                Icon(Icons.Filled.Delete, contentDescription = "מחק")
            }
            Switch(
                checked = alarm.isActive,
                onCheckedChange = { onToggle(alarm, it) },
            )
        }
    }
}

private fun zmanAnchorText(alarm: AlarmEntity): String {
    val name = ZmanKind.fromNameOrNull(alarm.zmanId)?.hebrewName ?: alarm.zmanId
    return if (alarm.offsetMinutes == 0) {
        name
    } else {
        "${alarm.offsetMinutes} דק' ${if (alarm.offsetBefore) "לפני" else "אחרי"} $name"
    }
}

private fun daysText(alarm: AlarmEntity): String {
    val base = when (alarm.daysOfWeek) {
        0 -> "חד-פעמי"
        AlarmEntity.ALL_DAYS -> "כל יום"
        AlarmEntity.SUNDAY_TO_FRIDAY -> "א'-ו'"
        AlarmEntity.SUNDAY_TO_THURSDAY -> "א'-ה'"
        else -> DAY_LETTERS.filterIndexed { i, _ -> (alarm.daysOfWeek shr i) and 1 == 1 }
            .joinToString(" ")
    }
    return buildString {
        append(base)
        if (alarm.skipShabbat && alarm.daysOfWeek == AlarmEntity.ALL_DAYS) append(" · ללא שבת")
        if (alarm.skipYomTov) append(" · ללא יו\"ט")
        if (alarm.dismissChallenge != com.zmanimclock.app.feature.alarms.data.DismissChallenge.NONE) {
            append(" · תרגיל לכיבוי")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmTypeChooserSheet(
    onChoose: (AlarmType) -> Unit,
    onChooseShabbat: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("שעון מעורר חדש", style = MaterialTheme.typography.titleLarge)

            TypeCard(
                icon = Icons.Filled.Alarm,
                title = "שעון מעורר רגיל",
                subtitle = "שעה קבועה — למשל 06:30 כל בוקר",
                onClick = { onChoose(AlarmType.FIXED) },
            )
            TypeCard(
                icon = Icons.Filled.WbTwilight,
                title = "שעון לפי זמן הלכתי",
                subtitle = "למשל 30 דק' לפני הנץ — מתעדכן כל יום לפי המיקום",
                onClick = { onChoose(AlarmType.ZMAN) },
            )
            TypeCard(
                emoji = "🕯️",
                title = "התראת כניסת שבת",
                subtitle = "כל שישי, 4 דק' לפני השקיעה — מסך נרות וצליל מיוחד",
                onClick = onChooseShabbat,
            )
        }
    }
}

@Composable
private fun TypeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    emoji: String? = null,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                icon != null ->
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                emoji != null ->
                    Text(emoji, style = MaterialTheme.typography.headlineSmall)
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
