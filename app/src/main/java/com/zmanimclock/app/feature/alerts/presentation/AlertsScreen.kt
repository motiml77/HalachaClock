package com.zmanimclock.app.feature.alerts.presentation

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.alerts.data.local.AlertEntity
import com.zmanimclock.app.feature.zmanim.model.ZmanKind

/**
 * Alerts management. [AlertsContent] and [AddAlertSheet] are stateless —
 * the design seam for the future Claude Design screens.
 */
@Composable
fun AlertsScreen(viewModel: AlertsViewModel = hiltViewModel()) {
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }

    AlertsContent(
        alerts = alerts,
        onToggle = viewModel::toggleAlert,
        onDelete = viewModel::deleteAlert,
        onAddClick = { showAddSheet = true },
    )

    if (showAddSheet) {
        AddAlertSheet(
            onConfirm = { draft ->
                viewModel.addAlert(draft)
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false },
        )
    }
}

@Composable
fun AlertsContent(
    alerts: List<AlertEntity>,
    onToggle: (AlertEntity, Boolean) -> Unit,
    onDelete: (AlertEntity) -> Unit,
    onAddClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (alerts.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.height(8.dp))
                Text("אין התראות", style = MaterialTheme.typography.titleMedium)
                Text(
                    "הוסף התראה לזמן הלכתי עם הכפתור למטה",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(alerts, key = { it.id }) { alert ->
                    AlertCard(alert, onToggle, onDelete)
                }
            }
        }

        FloatingActionButton(
            onClick = onAddClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "הוסף התראה")
        }
    }
}

@Composable
private fun AlertCard(
    alert: AlertEntity,
    onToggle: (AlertEntity, Boolean) -> Unit,
    onDelete: (AlertEntity) -> Unit,
) {
    val kind = ZmanKind.fromNameOrNull(alert.zmanId)
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (alert.isFullScreenAlarm) Icons.Filled.Alarm else Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(0.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = kind?.hebrewName ?: alert.zmanId,
                    style = MaterialTheme.typography.bodyLarge,
                )
                val desc = buildString {
                    if (alert.offsetMinutes > 0) {
                        append("${alert.offsetMinutes} דק' ")
                        append(if (alert.offsetBefore) "לפני" else "אחרי")
                    } else {
                        append("בזמן עצמו")
                    }
                    append(" · ")
                    append(if (alert.isFullScreenAlarm) "שעון מעורר" else "התראה")
                    if (alert.skipShabbat) append(" · ללא שבת")
                    if (alert.skipYomTov) append(" · ללא יו\"ט")
                }
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            IconButton(onClick = { onDelete(alert) }) {
                Icon(Icons.Filled.Delete, contentDescription = "מחק")
            }
            Switch(
                checked = alert.isActive,
                onCheckedChange = { onToggle(alert, it) },
            )
        }
    }
}

/** Everything needed to create an alert — mirrors AlertEntity's options. */
data class AlertDraft(
    val kind: ZmanKind = ZmanKind.HANETZ,
    val offsetMinutes: Int = 0,
    val offsetBefore: Boolean = true,
    val fullScreenAlarm: Boolean = true,
    val vibrate: Boolean = true,
    val skipShabbat: Boolean = false,
    val skipYomTov: Boolean = false,
    val snoozeMinutes: Int = 5,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAlertSheet(
    onConfirm: (AlertDraft) -> Unit,
    onDismiss: () -> Unit,
    initialKind: ZmanKind = ZmanKind.HANETZ,
) {
    var draft by remember { mutableStateOf(AlertDraft(kind = initialKind)) }
    var zmanMenuOpen by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("התראה חדשה", style = MaterialTheme.typography.titleLarge)

            // Zman selection
            ExposedDropdownMenuBox(
                expanded = zmanMenuOpen,
                onExpandedChange = { zmanMenuOpen = it },
            ) {
                OutlinedTextField(
                    value = draft.kind.hebrewName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("זמן") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(zmanMenuOpen) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = zmanMenuOpen,
                    onDismissRequest = { zmanMenuOpen = false },
                ) {
                    ZmanKind.entries.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(kind.hebrewName) },
                            onClick = {
                                draft = draft.copy(kind = kind)
                                zmanMenuOpen = false
                            },
                        )
                    }
                }
            }

            // Offset — how many minutes before/after the zman (user-chosen)
            Text("כמה דקות לפני הזמן?", style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(0, 5, 10, 15, 30, 45).forEach { minutes ->
                    FilterChip(
                        selected = draft.offsetMinutes == minutes && customMinutes.isBlank(),
                        onClick = {
                            customMinutes = ""
                            draft = draft.copy(offsetMinutes = minutes)
                        },
                        label = { Text(if (minutes == 0) "בזמן" else "$minutes'") },
                    )
                }
            }
            OutlinedTextField(
                value = customMinutes,
                onValueChange = { value ->
                    if (value.length <= 3 && value.all(Char::isDigit)) {
                        customMinutes = value
                        value.toIntOrNull()?.let { draft = draft.copy(offsetMinutes = it) }
                    }
                },
                label = { Text("או מספר דקות אחר") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (draft.offsetMinutes > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.offsetBefore,
                        onClick = { draft = draft.copy(offsetBefore = true) },
                        label = { Text("לפני הזמן") },
                    )
                    FilterChip(
                        selected = !draft.offsetBefore,
                        onClick = { draft = draft.copy(offsetBefore = false) },
                        label = { Text("אחרי הזמן") },
                    )
                }
            }

            // Type + options
            Text("סוג", style = MaterialTheme.typography.titleSmall)
            SheetSwitchRow("שעון מעורר (מסך מלא + צלצול)", draft.fullScreenAlarm) {
                draft = draft.copy(fullScreenAlarm = it)
            }
            SheetSwitchRow("רטט", draft.vibrate) { draft = draft.copy(vibrate = it) }
            SheetSwitchRow("דלג בשבת", draft.skipShabbat) { draft = draft.copy(skipShabbat = it) }
            SheetSwitchRow("דלג ביום טוב", draft.skipYomTov) { draft = draft.copy(skipYomTov = it) }

            if (draft.fullScreenAlarm) {
                Text("נודניק", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15).forEach { minutes ->
                        FilterChip(
                            selected = draft.snoozeMinutes == minutes,
                            onClick = { draft = draft.copy(snoozeMinutes = minutes) },
                            label = { Text("$minutes דק'") },
                        )
                    }
                }
            }

            Button(
                onClick = { onConfirm(draft) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("הוסף התראה")
            }
        }
    }
}

@Composable
private fun SheetSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
