package com.zmanimclock.app.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.settings.data.UserPreferences

/**
 * Placeholder settings. City picker and the rest of the preferences arrive
 * with the design phase; [SettingsContent] is the stateless seam.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    SettingsContent(
        prefs = prefs,
        onCandleMinutesChange = viewModel::setCandleLightingMinutes,
    )
}

@Composable
fun SettingsContent(
    prefs: UserPreferences,
    onCandleMinutesChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("מיקום", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = prefs.cityNameHebrew,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "בחירת עיר תגיע עם מסכי העיצוב",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        Card {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("הדלקת נרות", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${prefs.candleLightingMinutes} דקות לפני שקיעה",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        onCandleMinutesChange((prefs.candleLightingMinutes - 10).coerceAtLeast(10))
                    }) { Text("-") }
                    OutlinedButton(onClick = {
                        onCandleMinutesChange((prefs.candleLightingMinutes + 10).coerceAtMost(40))
                    }) { Text("+") }
                }
            }
        }
    }
}
