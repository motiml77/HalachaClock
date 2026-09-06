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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.settings.data.UserPreferences
import com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine
import com.zmanimclock.app.feature.widget.ZMAN_GROUPS
import com.zmanimclock.app.ui.components.ZmanChecklistLogic
import com.zmanimclock.app.ui.components.ZmanChecklistRow
import com.zmanimclock.app.ui.components.ZmanGroupedChecklist

/**
 * Settings: location (city picker entry), halachic prefs, display prefs,
 * and permission health-check cards. [SettingsContent] is the design seam.
 */
@Composable
fun SettingsScreen(
    onOpenCityPicker: () -> Unit,
    onOpenPermissions: () -> Unit = {},
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

    // These were computed once during composition, so a card stayed on screen
    // after the user granted the permission (the grant happens in system
    // Settings, i.e. while this screen is stopped). Re-check on every resume.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val permissionTickState = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableIntStateOf(0)
    }
    val permissionTick = permissionTickState.intValue
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissionTickState.intValue++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val alarmManager = context.getSystemService<AlarmManager>()
    val exactAlarmsOk = androidx.compose.runtime.remember(permissionTick) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager?.canScheduleExactAlarms() == true
    }
    val fullScreenOk = androidx.compose.runtime.remember(permissionTick) {
        Build.VERSION.SDK_INT < 34 ||
            context.getSystemService<android.app.NotificationManager>()
                ?.canUseFullScreenIntent() == true
    }
    val overlayOk = androidx.compose.runtime.remember(permissionTick) {
        Settings.canDrawOverlays(context)
    }

    SettingsContent(
        prefs = prefs,
        exactAlarmsOk = exactAlarmsOk,
        fullScreenOk = fullScreenOk,
        overlayOk = overlayOk,
        onRequestOverlay = {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}"),
                )
            )
        },
        onCityClick = onOpenCityPicker,
        onOpenPermissions = onOpenPermissions,
        onCandleMinutesChange = viewModel::setCandleLightingMinutes,
        onTzeitShabbatMinutesChange = viewModel::setTzeitShabbatMinutes,
        onNextZmanFilterChange = viewModel::setNextZmanFilter,
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
        onOpenPrivacyPolicy = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_POLICY_URL))
            )
        },
    )
}

/** Also the URL declared to Google Play under App content → Privacy policy. */
const val PRIVACY_POLICY_URL = "https://github.com/motiml77/HalachaClock/blob/main/PRIVACY.md"

@Composable
fun SettingsContent(
    prefs: UserPreferences,
    exactAlarmsOk: Boolean,
    fullScreenOk: Boolean = true,
    overlayOk: Boolean = true,
    onRequestOverlay: () -> Unit = {},
    onCityClick: () -> Unit,
    onOpenPermissions: () -> Unit = {},
    onCandleMinutesChange: (Int) -> Unit,
    onTzeitShabbatMinutesChange: (Int) -> Unit = {},
    onNextZmanFilterChange: (Set<String>) -> Unit = {},
    onPersistentNotificationChange: (Boolean) -> Unit,
    onRingTest: () -> Unit = {},
    onRequestExactAlarms: () -> Unit,
    onRequestFullScreen: () -> Unit = {},
    onOpenAutostart: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
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

        // Draw-over-apps health check: without it the ringing screen can't
        // take over while the phone is unlocked and in use
        if (!overlayOk) {
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
                        Text("מסך צלצול מעל הכל", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "אשר \"הצגה מעל אפליקציות\" — כדי שמסך ההתראה ייפתח " +
                                "גם כשהטלפון בשימוש.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = onRequestOverlay) { Text("אישור") }
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

        // צאת שבת — a MINHAG, not a ruling, so it is a setting with the
        // sources laid out. The luach we follow prints 30; much of Israel
        // keeps 40. See the explanation dialog.
        TzeitShabbatCard(
            minutes = prefs.tzeitShabbatMinutes,
            onChange = onTzeitShabbatMinutesChange,
        )

        // Which zmanim may be the headline "הזמן הבא"
        NextZmanFilterCard(
            selected = prefs.nextZmanFilter,
            onChange = onNextZmanFilterChange,
        )

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

        // Permissions — always reachable, and loud when something is missing.
        PermissionsCard(onOpen = onOpenPermissions)

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

        // Privacy policy — required to be reachable from inside the app for
        // the Play Store listing, not only linked from the store page itself.
        Card(modifier = Modifier.clickable(onClick = onOpenPrivacyPolicy)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("מדיניות פרטיות", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * צאת שבת picker.
 *
 * This is the one zman in the app the user is asked to choose, because the
 * practice genuinely differs and the difference is a flat offset rather than
 * a calculation: measured against the Ohr HaChaim luach across 5 cities and
 * 5 dates, our default 40 sits exactly 10 minutes after its 30, with a
 * spread of 4 seconds. Everything else the engine publishes matches that
 * luach to within seconds, so it would be wrong to bury this one silently.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TzeitShabbatCard(minutes: Int, onChange: (Int) -> Unit) {
    var showInfo by remember { mutableStateOf(false) }

    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("צאת שבת", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$minutes דקות אחרי השקיעה",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = { showInfo = true }) { Text("מה ההבדל?") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MaranZmanimEngine.TZEIT_SHABBAT_OPTIONS.forEach { (value, label) ->
                    FilterChip(
                        selected = minutes.toLong() == value,
                        onClick = { onChange(value.toInt()) },
                        label = { Text(label.substringBefore(" —")) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                when (minutes) {
                    30 -> "כשיטת לוח אור החיים"
                    40 -> "כמנהג הרווח בארץ"
                    72 -> "כשיטת רבנו תם"
                    else -> "הגדרה אישית"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("הבנתי") } },
            title = { Text("צאת שבת — למה יש כמה זמנים?") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "כל שאר הזמנים באפליקציה מחושבים לפי לוח אור החיים " +
                            "(זמני יוסף), ותואמים לו עד כדי שניות בודדות. " +
                            "צאת שבת הוא היוצא מן הכלל: כאן ההבדל בין הלוחות " +
                            "אינו בחישוב אלא בהכרעה — כמה דקות להוסיף אחרי השקיעה.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("השיטות:", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "• 30 דקות — לוח אור החיים / זמני יוסף, " +
                            "לפי הכרעת מרן הראשון לציון.\n\n" +
                            "• 40 דקות — המנהג הרווח בהרבה קהילות בארץ, " +
                            "והברירת מחדל כאן.\n\n" +
                            "• 42 דקות — נהוג בחלק מקהילות ירושלים.\n\n" +
                            "• 72 דקות — שיטת רבנו תם, למחמירים.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "ההפרש הוא קבוע — בדיוק אותו מספר דקות בכל עיר ובכל " +
                            "עונה — ולכן זו שאלה של מנהג בלבד. " +
                            "יש לנהוג כמנהג המקום וכהוראת רב.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            },
        )
    }
}

/**
 * Which zmanim may be the headline "הזמן הבא".
 *
 * The app tracks ~19 zmanim, but most people watch only a handful. Without
 * this, the headline walks through every one of them in order — a user who
 * only cares about ק"ש and שקיעה still gets עלות, משיכיר and הנץ announced
 * first. Narrowing the list makes the headline jump straight to the next
 * zman that person actually wants: with ק"ש selected, 06:30 already reads
 * "הזמן הבא: ק״ש" even though several zmanim fall in between.
 *
 * Nothing is hidden from the main list — this only governs the single
 * headline (and the same one in the status notification and the widget).
 * Selecting none means "all", which is the default.
 */
@Composable
private fun NextZmanFilterCard(selected: Set<String>, onChange: (Set<String>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("מה יוצג כ\"הזמן הבא\"", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (selected.isEmpty()) {
                            "כל הזמנים — לפי הסדר"
                        } else {
                            "${selected.size} זמנים נבחרו"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "סגור" else "בחר")
                }
            }

            if (!expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "אפשר לצמצם כדי שהכותרת תקפוץ ישר לזמן שחשוב לך, " +
                        "בלי לעבור דרך כל הזמנים שבדרך.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                // Master row first: checked while the filter is empty, which
                // has always meant "all zmanim". Ticking any specific zman
                // narrows the filter (and visibly unticks this row); ticking
                // this row clears the filter back to all. The empty-set
                // meaning is unchanged from the old chip UI.
                ZmanChecklistRow(
                    label = "כל הזמנים",
                    checked = selected.isEmpty(),
                    onToggle = { onChange(emptySet()) },
                    bold = true,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ZmanGroupedChecklist(
                    groups = ZMAN_GROUPS,
                    isChecked = { it.name in selected },
                    onToggle = { kind ->
                        onChange(ZmanChecklistLogic.toggleUncapped(selected, kind.name))
                    },
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "בלי בחירה — כל הזמנים נחשבים.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

/**
 * A permanent way back into the permissions wizard, and a live count of what
 * is still missing.
 *
 * Every grant the alarm engine needs is answered OUTSIDE the app — a system
 * dialog or a settings page — so a user who declined on first launch, or who
 * revoked one later from Android's own settings, previously had no route back
 * and no indication anything was wrong. The alarm would simply be less
 * reliable, silently.
 *
 * The count is re-read on every resume, because the user answers these
 * somewhere else and comes back.
 */
@Composable
private fun PermissionsCard(onOpen: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val summary = remember(tick) {
        com.zmanimclock.app.feature.onboarding.permissionSummary(context)
    }
    val cs = MaterialTheme.colorScheme

    // RED ONLY FOR A MISSING *REQUIRED* PERMISSION. The recommended one
    // (battery) is a reliability improvement the alarm does not need in order
    // to ring, and painting the whole card as a fault over it told the owner
    // the clock was broken when it was not.
    val alarming = summary.missingRequired.isNotEmpty()
    Card(
        // A missing required permission is a real reliability problem, so it
        // is coloured like one instead of sitting quietly in a list.
        colors = CardDefaults.cardColors(
            containerColor = if (alarming) cs.errorContainer else cs.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (alarming) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (alarming) cs.onErrorContainer else cs.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "הרשאות",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (alarming) cs.onErrorContainer else cs.onSurface,
                    )
                    Text(
                        // NAMES THE MISSING ONES, always. A bare count ("one
                        // of five missing") is what the owner actually met:
                        // nothing on screen said which, and the permissions
                        // screen shows a tick beside four of five with no
                        // summary of its own, so the odd one out is easy to
                        // scroll straight past. The names here are the card
                        // titles on that screen, verbatim, so they can be
                        // found by eye.
                        when {
                            summary.allGranted -> "כל ${summary.total} ההרשאות מאושרות"
                            alarming ->
                                "חסר: ${summary.missingRequired.joinToString(", ")} — " +
                                    "השעון עלול לא לצלצל בזמן"
                            else ->
                                "מומלץ להוסיף: ${summary.missingRecommended.joinToString(", ")} — " +
                                    "השעון יצלצל גם בלי זה"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (alarming) cs.onErrorContainer else cs.secondary,
                    )
                }
            }
            OutlinedButton(onClick = onOpen, modifier = Modifier.padding(top = 10.dp)) {
                Text(if (summary.allGranted) "בדוק הרשאות" else "אשר עכשיו")
                // (label unchanged: the button opens the same screen either way)
            }
        }
    }
}
