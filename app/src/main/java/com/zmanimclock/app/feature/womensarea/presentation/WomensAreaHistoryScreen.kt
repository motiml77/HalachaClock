package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.zmanimclock.app.feature.womensarea.data.WomensAreaEntryEntity
import com.zmanimclock.app.feature.womensarea.data.WomensAreaEntryType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M.yyyy")

/** Every entry, either kind, newest first — with edit/delete, since manually-entered dates will have mistakes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WomensAreaHistoryScreen(
    onBack: () -> Unit,
    viewModel: WomensAreaViewModel = hiltViewModel(),
) {
    val entries by viewModel.allEntries.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<WomensAreaEntryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("היסטוריית רשומות") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("אין עדיין רשומות", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    HistoryRow(
                        entry = entry,
                        onEdit = { editing = entry },
                        onDelete = { viewModel.deleteEntry(entry) },
                    )
                }
            }
        }
    }

    editing?.let { entry ->
        WomensAreaDateEntryDialog(
            title = entry.type.label,
            initialDate = LocalDate.ofEpochDay(entry.epochDay),
            onConfirm = { date ->
                viewModel.updateEntryDate(entry, date)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun HistoryRow(entry: WomensAreaEntryEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.type.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    DATE_FORMAT.format(LocalDate.ofEpochDay(entry.epochDay)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "עריכה", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "מחיקה", modifier = Modifier.size(18.dp))
            }
        }
    }
}

private val WomensAreaEntryType.label: String
    get() = when (this) {
        WomensAreaEntryType.PERIOD_START -> "תחילת ראייה"
        WomensAreaEntryType.FIRST_CLEAN_DAY -> "יום ראשון לנקיים"
    }
