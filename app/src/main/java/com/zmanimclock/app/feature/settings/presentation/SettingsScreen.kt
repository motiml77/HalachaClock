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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zmanimclock.app.scheduling.ZmanimForegroundService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCityPicker: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsState()
    val context = LocalContext.current

    val nusachLabel = when (prefs.nusach) {
        "ashkenazi" -> "אשכנז"
        "sephardi" -> "ספרד"
        else -> "ספרד"
    }
    val shitaLabel = when (prefs.primaryShita) {
        "gra" -> "גר\"א"
        "mga" -> "מגן אברהם"
        "both" -> "שתיהם"
        else -> "שתיהם"
    }
    val darkModeLabel = when (prefs.darkMode) {
        "light" -> "בהיר"
        "dark" -> "כהה"
        "system" -> "לפי המערכת"
        else -> "לפי המערכת"
    }
    val timeFormatLabel = if (prefs.use24HourFormat) "24 שעות" else "12 שעות"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("הגדרות") },
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
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Location Section
            SettingsSectionHeader("מיקום", Icons.Default.LocationOn)

            SettingsClickableRow(
                title = "בחר מיקום",
                subtitle = prefs.cityNameHebrew,
                onClick = onNavigateToCityPicker,
            )

            SettingsToggleRow(
                title = "מיקום אוטומטי (GPS)",
                subtitle = "עדכן מיקום אוטומטית",
                checked = prefs.useGps,
                onCheckedChange = { viewModel.setUseGps(it) },
            )

            SettingsToggleRow(
                title = "שימוש בגובה",
                subtitle = "חישוב זריחה/שקיעה לפי גובה המקום",
                checked = prefs.useElevation,
                onCheckedChange = { viewModel.setUseElevation(it) },
            )

            Spacer(Modifier.height(16.dp))

            // Halachic Opinions
            SettingsSectionHeader("שיטות הלכתיות", Icons.Default.Tune)

            SettingsClickableRow(
                title = "נוסח",
                subtitle = nusachLabel,
                onClick = { /* TODO: nusach picker dialog */ },
            )

            SettingsClickableRow(
                title = "שיטה ראשית",
                subtitle = shitaLabel,
                onClick = { /* TODO: shita picker dialog */ },
            )

            SettingsClickableRow(
                title = "זמנים מוצגים",
                subtitle = "בחר אילו זמנים להציג",
                onClick = { /* TODO */ },
            )

            SettingsClickableRow(
                title = "זמן הדלקת נרות",
                subtitle = "${prefs.candleLightingMinutes} דקות לפני שקיעה",
                onClick = { /* TODO: candle lighting picker dialog */ },
            )

            Spacer(Modifier.height(16.dp))

            // Notifications
            SettingsSectionHeader("התראות", Icons.Default.Notifications)

            SettingsToggleRow(
                title = "התראה קבועה בשורת המצב",
                subtitle = "הצג את הזמן הקרוב בשורת המצב",
                checked = prefs.persistentNotification,
                onCheckedChange = { enabled ->
                    viewModel.setPersistentNotification(enabled)
                    if (enabled) {
                        ZmanimForegroundService.startIfEnabled(context)
                    } else {
                        ZmanimForegroundService.stop(context)
                    }
                },
            )

            SettingsClickableRow(
                title = "הודעות זמנים",
                subtitle = "בחר אילו זמנים יציגו הודעה בהגיעם",
                onClick = { /* TODO: open zman announcement picker */ },
            )

            Spacer(Modifier.height(16.dp))

            // Display
            SettingsSectionHeader("תצוגה", Icons.Default.DarkMode)

            SettingsClickableRow(
                title = "מצב תצוגה",
                subtitle = darkModeLabel,
                onClick = { /* TODO: dark mode picker dialog */ },
            )

            SettingsClickableRow(
                title = "פורמט שעה",
                subtitle = timeFormatLabel,
                onClick = { /* TODO: time format picker dialog */ },
            )

            Spacer(Modifier.height(16.dp))

            // About
            SettingsSectionHeader("אודות", Icons.Default.Schedule)

            SettingsClickableRow(
                title = "גרסה",
                subtitle = "1.0.0",
                onClick = { },
            )

            SettingsClickableRow(
                title = "אודות החישובים",
                subtitle = "מבוסס על שיטות הגר\"א, מג\"א, ר' עובדיה יוסף ועוד",
                onClick = { /* TODO */ },
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SettingsClickableRow(
    title: String,
    subtitle: String,
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
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
