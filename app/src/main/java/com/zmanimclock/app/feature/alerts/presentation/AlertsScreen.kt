package com.zmanimclock.app.feature.alerts.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * Placeholder alerts management. [AlertsContent] is stateless — the design
 * seam for the future Claude Design screens.
 */
@Composable
fun AlertsScreen(viewModel: AlertsViewModel = hiltViewModel()) {
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    AlertsContent(
        alerts = alerts,
        onToggle = viewModel::toggleAlert,
        onDelete = viewModel::deleteAlert,
        onAddClick = { showAddDialog = true },
    )

    if (showAddDialog) {
        AddAlertDialog(
            onConfirm = { kind, fullScreen ->
                viewModel.addAlert(kind, offsetMinutes = 0, offsetBefore = true, fullScreen = fullScreen)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
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
                Text("אין התראות", style = MaterialTheme.typography.titleMedium)
                Text(
                    "הוסף התראה עם הכפתור למטה",
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = kind?.hebrewName ?: alert.zmanId,
                    style = MaterialTheme.typography.bodyLarge,
                )
                val desc = buildString {
                    if (alert.offsetMinutes > 0) {
                        append("${alert.offsetMinutes} דק' ")
                        append(if (alert.offsetBefore) "לפני" else "אחרי")
                        append(" · ")
                    }
                    append(if (alert.isFullScreenAlarm) "שעון מעורר" else "התראה")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAlertDialog(
    onConfirm: (ZmanKind, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(ZmanKind.HANETZ) }
    var fullScreen by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("התראה חדשה") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("בחר זמן:", style = MaterialTheme.typography.bodyMedium)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(ZmanKind.entries) { kind ->
                        FilterChip(
                            selected = kind == selected,
                            onClick = { selected = kind },
                            label = { Text(kind.hebrewName) },
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("שעון מעורר (מסך מלא)")
                    Switch(checked = fullScreen, onCheckedChange = { fullScreen = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected, fullScreen) }) { Text("הוסף") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ביטול") }
        },
    )
}
