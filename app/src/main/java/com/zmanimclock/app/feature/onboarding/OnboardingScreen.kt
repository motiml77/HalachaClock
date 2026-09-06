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
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zmanimclock.app.R
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * The permissions screen — shown once at first launch, and reachable from
 * Settings forever after.
 *
 * That second entry point is not a nicety. Everything here routes through
 * system dialogs and settings pages, so a user who taps "המשך בכל זאת" on
 * first run — or who denies a system prompt, or later revokes a grant from
 * Android's own settings — used to have no way back to this screen at all.
 * The alarm engine then quietly under-performed with nothing in the app to
 * explain why or to fix it.
 *
 * Grants, each with a live granted/missing state re-checked on every resume
 * (most of them leave the app to be answered):
 *  1. Notifications (Android 13+)
 *  2. Exact alarms (Android 12+)
 *  3. Battery-optimization exemption (background reliability)
 *  4. Full-screen intent (Android 14+)
 *  5. Overlay — the ringing screen over other apps
 * Boot persistence needs no user action — stated as reassurance.
 *
 * @param isFirstRun true for the launch wizard, false when opened from
 *   Settings; changes only the framing and the closing button, never which
 *   permissions are offered.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, isFirstRun: Boolean = true) {
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
    val overlayGranted = remember(refreshTick) { Settings.canDrawOverlays(context) }

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
        Text(
            // stringResource, not a literal: the app's name now lives in ONE
            // place (values/values-iw strings.xml) and this follows it rather
            // than carrying a copy that could drift from the real name.
            if (isFirstRun) "ברוכים הבאים ל${stringResource(R.string.app_name)}" else "הרשאות",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            if (isFirstRun) {
                "כדי שההתראות והשעונים יעבדו תמיד — בזמן, גם כשהמסך כבוי — " +
                    "נאשר כמה הרשאות:"
            } else {
                "אפשר לאשר כל אחת מהן בכל שלב. מה שלא מאושר מסומן כאן, " +
                    "ובלעדיו השעון עלול לא לצלצל בזמן."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        PermissionCard(
            icon = Icons.Filled.NotificationsActive,
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
            icon = Icons.Filled.AlarmOn,
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
                // The direct-request variant (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                // with a package: URI) needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, which
                // Play policy restricts to a named allow-list an alarm clock isn't on — an
                // adversarial audit flagged it as a real submission-rejection risk, and the
                // app's own alarms don't need it anyway: they arm via setAlarmClock()/
                // setExactAndAllowWhileIdle(), which Android documents as Doze-exempt
                // without this permission. This settings-list variant needs none.
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            },
        )

        // Draw-over-apps: the ringing screen can take over even while the
        // phone is unlocked and in use (without it — heads-up notification)
        PermissionCard(
            icon = Icons.Filled.Fullscreen,
            title = "מסך צלצול מעל הכל",
            description = "מסך ההתראה ייפתח גם באמצע שימוש בטלפון",
            granted = overlayGranted,
            onGrant = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            },
        )

        // Android 14+: full-screen-intent can be revoked — only shown when needed
        if (Build.VERSION.SDK_INT >= 34 && !fullScreenGranted) {
            PermissionCard(
                icon = Icons.Filled.Fullscreen,
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
        val allGranted = notificationsGranted && exactAlarmsGranted &&
            batteryExempt && fullScreenGranted && overlayGranted
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(
                when {
                    !isFirstRun -> "סיום"
                    allGranted -> "הכל מאושר — נתחיל!"
                    // Deliberately not a dead end: Settings keeps a permanent
                    // way back to this screen.
                    else -> "המשך בכל זאת"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        // Breathing room under the closing button. Harmless on first run,
        // and from Settings it keeps the button clear of the bottom edge
        // instead of ending flush against it.
        Spacer(Modifier.height(32.dp))
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

internal fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

internal fun hasExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true

internal fun isBatteryExempt(context: Context): Boolean =
    context.getSystemService<PowerManager>()
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

internal fun hasFullScreenIntent(context: Context): Boolean =
    Build.VERSION.SDK_INT < 34 ||
        context.getSystemService<android.app.NotificationManager>()
            ?.canUseFullScreenIntent() == true

/**
 * How many of the five grants are in place right now.
 *
 * Read by Settings so a missing permission is VISIBLE rather than something
 * the user has to go looking for. Computed from the same functions the wizard
 * itself uses, so the two can never disagree about what "granted" means.
 */
data class PermissionSummary(val granted: Int, val total: Int) {
    val allGranted: Boolean get() = granted == total
    val missing: Int get() = total - granted
}

fun permissionSummary(context: Context): PermissionSummary {
    val checks = listOf(
        hasNotificationPermission(context),
        hasExactAlarms(context),
        isBatteryExempt(context),
        hasFullScreenIntent(context),
        Settings.canDrawOverlays(context),
    )
    return PermissionSummary(granted = checks.count { it }, total = checks.size)
}
