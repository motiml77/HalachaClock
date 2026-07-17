package com.zmanimclock.app.feature.onboarding

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * First-launch permissions wizard. Three grants the alarm engine needs to be
 * bullet-proof, each with a live granted/missing state (re-checked on every
 * resume, since two of them route through system settings):
 *  1. Notifications (Android 13+)
 *  2. Exact alarms (Android 12+)
 *  3. Battery-optimization exemption (background reliability)
 * Boot persistence needs no user action — stated as reassurance.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Bump to re-evaluate grant states after returning from system settings
    var refreshTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsGranted = remember(refreshTick) { hasNotificationPermission(context) }
    val exactAlarmsGranted = remember(refreshTick) { hasExactAlarms(context) }
    val batteryExempt = remember(refreshTick) { isBatteryExempt(context) }
    val fullScreenGranted = remember(refreshTick) { hasFullScreenIntent(context) }

    var askedNotifications by remember { mutableIntStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { askedNotifications++; refreshTick++ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Icon(
            Icons.Filled.Alarm,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text("ברוכים הבאים לשעון זמנים", style = MaterialTheme.typography.headlineMedium)
        Text(
            "כדי שההתראות והשעונים יעבדו תמיד — בזמן, גם כשהמסך כבוי — " +
                "נאשר שלוש הרשאות:",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        PermissionCard(
            icon = Icons.Filled.Notifications,
            title = "התראות",
            description = "הצגת התראות הזמנים והשעון המעורר",
            granted = notificationsGranted,
            onGrant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )

        PermissionCard(
            icon = Icons.Filled.Alarm,
            title = "אזעקות מדויקות",
            description = "צלצול בשנייה הנכונה — לא באיחור",
            granted = exactAlarmsGranted,
            onGrant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                }
            },
        )

        PermissionCard(
            icon = Icons.Filled.BatteryChargingFull,
            title = "פעולה ברקע",
            description = "פטור מחיסכון בסוללה — כדי שהמערכת לא תעצור את השעון",
            granted = batteryExempt,
            onGrant = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            },
        )

        // Android 14+: full-screen-intent can be revoked — only shown when needed
        if (Build.VERSION.SDK_INT >= 34 && !fullScreenGranted) {
            PermissionCard(
                icon = Icons.Filled.Alarm,
                title = "מסך צלצול מלא",
                description = "פתיחת מסך ההתראה מעל מסך הנעילה בזמן הצלצול",
                granted = false,
                onGrant = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                },
            )
        }

        Card {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.RestartAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "אחרי כיבוי והדלקה של הטלפון — הכל חוזר לעבוד לבד. " +
                        "אין צורך באישור נוסף.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        val allGranted =
            notificationsGranted && exactAlarmsGranted && batteryExempt && fullScreenGranted
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (allGranted) "הכל מאושר — נתחיל!" else "המשך בכל זאת",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (granted) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "מאושר",
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                OutlinedButton(onClick = onGrant) { Text("אשר") }
            }
        }
    }
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

private fun hasExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true

private fun isBatteryExempt(context: Context): Boolean =
    context.getSystemService<PowerManager>()
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

private fun hasFullScreenIntent(context: Context): Boolean =
    Build.VERSION.SDK_INT < 34 ||
        context.getSystemService<android.app.NotificationManager>()
            ?.canUseFullScreenIntent() == true
