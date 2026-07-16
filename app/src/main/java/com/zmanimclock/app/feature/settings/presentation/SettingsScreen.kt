package com.zmanimclock.app.feature.settings.presentation

import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.settings.data.UserPreferences

/**
 * Settings: location (city picker entry), halachic prefs, display prefs,
 * and permission health-check cards. [SettingsContent] is the design seam.
 */
@Composable
fun SettingsScreen(
    onOpenCityPicker: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val alarmManager = context.getSystemService<AlarmManager>()
    val exactAlarmsOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        alarmManager?.canScheduleExactAlarms() == true

    SettingsContent(
        prefs = prefs,
        exactAlarmsOk = exactAlarmsOk,
        onCityClick = onOpenCityPicker,
        onCandleMinutesChange = viewModel::setCandleLightingMinutes,
        onRequestExactAlarms = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        },
    )
}

@Composable
fun SettingsContent(
    prefs: UserPreferences,
    exactAlarmsOk: Boolean,
    onCityClick: () -> Unit,
    onCandleMinutesChange: (Int) -> Unit,
    onRequestExactAlarms: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Permission health check
        if (!exactAlarmsOk) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    ) {
                        Text("נדרשת הרשאת אזעקות מדויקות", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "בלעדיה ההתראות עלולות לאחר. הקש כדי לאשר.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = onRequestExactAlarms) { Text("אישור") }
                }
            }
        }

        // Location
        Card(modifier = Modifier.clickable(onClick = onCityClick)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text("מיקום", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = prefs.cityNameHebrew,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "הקש לבחירת יישוב (415 יישובים)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        // Candle lighting
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
                    Text(
                        "ירושלים נוהגת 40 דק'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
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

        // About the calculation
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("שיטת החישוב", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "הזמנים מחושבים לפי שיטת מרן — לוח אור החיים / " +
                        "חזון יוסף (הרב יצחק יוסף), על בסיס הנץ הנראה מטבלאות חי.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
