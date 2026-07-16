package com.zmanimclock.app.feature.zmanim.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Placeholder zmanim list. Purely presentational: the stateless
 * [ZmanimContent] is the seam where the Claude Design layout will land.
 */
@Composable
fun HomeScreen(viewModel: ZmanimViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ZmanimContent(state)
}

@Composable
fun ZmanimContent(state: ZmanimViewModel.UiState) {
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
                text = state.locationName +
                    if (state.basedOnVisibleSunrise) " · הנץ הנראה" else " · מישור",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp, vertical = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.rows, key = { it.kind.name }) { row ->
                ZmanRowCard(row)
            }
        }
    }
}

@Composable
private fun ZmanRowCard(row: ZmanimViewModel.ZmanRow) {
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (row.isNext) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = row.time,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (row.isNext) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
