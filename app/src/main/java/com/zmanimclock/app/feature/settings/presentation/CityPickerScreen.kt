package com.zmanimclock.app.feature.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zmanimclock.app.location.model.CityInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityPickerScreen(
    onNavigateBack: () -> Unit,
    viewModel: CityPickerViewModel = hiltViewModel(),
) {
    val prefs by viewModel.currentPrefs.collectAsState()
    val israeliCities = viewModel.israeliCities
    val worldwideCities = viewModel.worldwideCities

    var searchQuery by remember { mutableStateOf("") }

    val filteredIsraeli = if (searchQuery.isBlank()) israeliCities
    else viewModel.search(searchQuery).filter { it.country == "IL" }

    val filteredWorldwide = if (searchQuery.isBlank()) worldwideCities
    else viewModel.search(searchQuery).filter { it.country != "IL" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("בחר עיר") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Search bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("חפש עיר...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                ),
                shape = MaterialTheme.shapes.medium,
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Israeli cities section
                if (filteredIsraeli.isNotEmpty()) {
                    item {
                        SectionHeader("ישראל")
                    }
                    items(filteredIsraeli, key = { it.id }) { city ->
                        CityRow(
                            city = city,
                            isSelected = city.id == prefs.cityId,
                            onClick = {
                                viewModel.selectCity(city)
                                onNavigateBack()
                            },
                        )
                    }
                }

                // Worldwide cities section
                if (filteredWorldwide.isNotEmpty()) {
                    item {
                        SectionHeader("עולם")
                    }
                    items(filteredWorldwide, key = { it.id }) { city ->
                        CityRow(
                            city = city,
                            isSelected = city.id == prefs.cityId,
                            onClick = {
                                viewModel.selectCity(city)
                                onNavigateBack()
                            },
                        )
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun CityRow(
    city: CityInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(city.nameHebrew, style = MaterialTheme.typography.bodyLarge)
            if (city.region.isNotBlank()) {
                Text(
                    city.region,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        if (isSelected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.Check,
                contentDescription = "נבחר",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
