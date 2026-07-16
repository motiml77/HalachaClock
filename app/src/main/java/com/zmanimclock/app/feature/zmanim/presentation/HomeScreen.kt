package com.zmanimclock.app.feature.zmanim.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.zmanimclock.app.feature.zmanim.model.ZmanKind

/**
 * The daily zmanim list. Each row carries a bell: tap to create a daily
 * zman-anchored alarm for that zman (opens the alarm editor pre-selected).
 * A filled bell marks zmanim that already have an active alarm.
 * [ZmanimContent] is stateless — the Claude Design seam.
 */
@Composable
fun HomeScreen(
    onCreateZmanAlarm: (String) -> Unit,
    viewModel: ZmanimViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val alertedKinds by viewModel.alertedKinds.collectAsStateWithLifecycle()

    ZmanimContent(
        state = state,
        alertedKinds = alertedKinds,
        onBellClick = { kind -> onCreateZmanAlarm(kind.name) },
    )
}

@Composable
fun ZmanimContent(
    state: ZmanimViewModel.UiState,
    alertedKinds: Set<String>,
    onBellClick: (ZmanKind) -> Unit,
) {
    if (state.loading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.hebrewDate, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${state.gregorianDate} · ${state.locationName}" +
                    if (state.basedOnVisibleSunrise) " · הנץ הנראה" else " · מישור",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            state.nextZmanCountdown?.let { countdown ->
                Text(
                    text = countdown,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.rows, key = { it.kind.name }) { row ->
                ZmanRowCard(
                    row = row,
                    hasAlert = row.kind.name in alertedKinds,
                    onBellClick = { onBellClick(row.kind) },
                )
            }
        }
    }
}

@Composable
private fun ZmanRowCard(
    row: ZmanimViewModel.ZmanRow,
    hasAlert: Boolean,
    onBellClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (row.isNext) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBellClick) {
                Icon(
                    imageVector = if (hasAlert) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                    contentDescription = if (hasAlert) "התראה פעילה — הוסף עוד" else "הוסף התראה",
                    tint = if (hasAlert) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (row.isNext) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = row.time,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (row.isNext) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
