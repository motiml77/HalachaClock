package com.zmanimclock.app.feature.settings.presentation

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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

    // Enabling the persistent status line is pointless without notification
    // permission — request it, then turn the pref on only once granted.
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.setPersistentNotification(true) }

    fun onPersistentToggle(enabled: Boolean) {
        val needsPerm = enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPerm) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setPersistentNotification(enabled)
        }
    }

    val alarmManager = context.getSystemService<AlarmManager>()
    val exactAlarmsOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        alarmManager?.canScheduleExactAlarms() == true
    val fullScreenOk = Build.VERSION.SDK_INT < 34 ||
        context.getSystemService<android.app.NotificationManager>()
            ?.canUseFullScreenIntent() == true

    SettingsContent(
        prefs = prefs,
        exactAlarmsOk = exactAlarmsOk,
        fullScreenOk = fullScreenOk,
        onCityClick = onOpenCityPicker,
        onCandleMinutesChange = viewModel::setCandleLightingMinutes,
        onPersistentNotificationChange = ::onPersistentToggle,
        onRingTest = viewModel::ringInAMinute,
        onRequestExactAlarms = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        },
        onRequestFullScreen = {
            if (Build.VERSION.SDK_INT >= 34) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        android.net.Uri.parse("package:${context.packageName}"),
                    )
                )
            }
        },
        onOpenAutostart = { com.zmanimclock.app.util.OemHelper.openAutostartSettings(context) },
    )
}

@Composable
fun SettingsContent(
    prefs: UserPreferences,
    exactAlarmsOk: Boolean,
    fullScreenOk: Boolean = true,
    onCityClick: () -> Unit,
    onCandleMinutesChange: (Int) -> Unit,
    onPersistentNotificationChange: (Boolean) -> Unit,
    onRingTest: () -> Unit = {},
    onRequestExactAlarms: () -> Unit,
    onRequestFullScreen: () -> Unit = {},
    onOpenAutostart: () -> Unit = {},
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
                        Icons.Filled.AlarmOn,
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

        // Full-screen intent health check (Android 14+)
        if (!fullScreenOk) {
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
                        Icons.Filled.Fullscreen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    ) {
                        Text("נדרשת הרשאת מסך צלצול מלא", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "בלעדיה מסך ההתראה לא ייפתח אוטומטית בזמן הצלצול.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = onRequestFullScreen) { Text("אישור") }
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

        // Persistent status notification
        Card {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("שורת מצב קבועה", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "הזמן הבא והשעון הבא — תמיד בהתראות",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Switch(
                    checked = prefs.persistentNotification,
                    onCheckedChange = onPersistentNotificationChange,
                )
            }
        }

        // Reliability self-test + OEM guidance (A6)
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("בדיקת אמינות", style = MaterialTheme.typography.titleMedium)
                Text(
                    "ודא שהצלצול עובד אצלך: כבה את המסך אחרי ההקשה.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onRingTest,
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Icon(
                        Icons.Filled.AlarmOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("צלצל בעוד דקה")
                }

                if (com.zmanimclock.app.util.OemHelper.isAggressiveOem()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        "במכשירי ${com.zmanimclock.app.util.OemHelper.oemName()} יש לאשר " +
                            "\"הפעלה אוטומטית\" כדי שהשעון לא ייעצר על ידי המערכת.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onOpenAutostart) {
                        Text("פתח הגדרות יצרן")
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // About the calculation
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("שיטת החישוב", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "הזמנים מחושבים לפי שיטת מרן — לוח אור החיים / " +
                        "חזון יוסף (הרב יצחק יוסף): שעות זמניות לפי היום " +
                        "המישורי (זריחה–שקיעה בגובה פני הים), וחצות בנקודת " +
                        "מעבר השמש. הנץ הנראה מטבלאות חי מוצג לתפילת ותיקין " +
                        "ומשמש לשעונים המכוונים להנץ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
