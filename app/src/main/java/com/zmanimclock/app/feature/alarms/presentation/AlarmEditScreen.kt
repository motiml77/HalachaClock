package com.zmanimclock.app.feature.alarms.presentation

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.alarms.data.AlarmType
import com.zmanimclock.app.feature.alarms.data.DismissChallenge
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.ui.theme.Ext
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.animateColorAsState

private val DAY_LETTERS = listOf("א", "ב", "ג", "ד", "ה", "ו", "ש")

/**
 * Create/edit one alarm — fixed-time or zman-anchored. Everything the user
 * asked for lives here: exact time OR zman+offset with live preview, repeat
 * days with quick presets, sound picker, per-alarm volume, ring duration,
 * math dismiss-challenge, snooze, vibration, label.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    type: AlarmType,
    alarmId: Long?,
    preselectedZman: String?,
    shabbatPreset: Boolean = false,
    onBack: () -> Unit,
    viewModel: AlarmEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.initialize(type, alarmId, preselectedZman, shabbatPreset) }
    val alarm by viewModel.alarm.collectAsStateWithLifecycle()
    val zmanPreview by viewModel.zmanPreview.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.update { it.copy(soundUri = uri?.toString()) }
        }
    }

    val isEditing = alarmId != null && alarmId >= 0
    // From a zman-list bell the anchor is fixed to that zman — no toggle shown.
    val showAnchorToggle = !alarm.shabbatMode && preselectedZman == null

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf(
        EditorTab("בסיסי", Icons.Filled.Alarm),
        EditorTab("צליל", Icons.AutoMirrored.Filled.VolumeUp),
        EditorTab("כיבוי", Icons.Filled.AlarmOff),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Header sits above the tabs; the tab content scrolls under it
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
            }
            Text(
                text = when {
                    alarm.shabbatMode -> "התראת כניסת שבת"
                    isEditing -> "עריכת שעון"
                    else -> "שעון חדש"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        // Three tabs, grouped by the question each answers:
        //  בסיסי — WHEN does it ring (name, anchor, days)
        //  צליל  — HOW does it ring (mode, tone, volume, duration, preview)
        //  כיבוי — how does it STOP (snooze, math challenge, wake check)
        PillTabs(tabs = tabs, selected = selectedTab, onSelect = { selectedTab = it })

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (selectedTab) {
                // ===================== בסיסי =====================
                0 -> {
                    if (alarm.shabbatMode) {
                        SettingsGroup {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    com.zmanimclock.app.ui.theme.AppIcons.Candle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "כל יום שישי, ${alarm.offsetMinutes} דק' לפני שקיעת המקום שלך — " +
                                        "מסך נרות וצליל משלה.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Compact and rounded, at the owner's request — the old
                    // outlined box with a floating label AND a supporting line
                    // stood ~110dp tall for a field most people leave empty.
                    // No label: the placeholder IS the suggested name, so an
                    // empty field already reads as "this is what it will be
                    // called", which is what the supporting line used to say.
                    TextField(
                        value = alarm.label,
                        onValueChange = { v -> viewModel.update { it.copy(label = v) } },
                        placeholder = {
                            Text(
                                defaultAlarmLabel(alarm),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = "שם השעון",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    )

                    if (showAnchorToggle) {
                        Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                            AnchorTypeSelector(alarm.type, viewModel::setType)
                        }
                    }

                    if (!alarm.shabbatMode) {
                        SettingsGroup {
                            Column(modifier = Modifier.padding(14.dp)) {
                                when (alarm.type) {
                                    AlarmType.FIXED -> FixedAnchorSection(alarm, viewModel::update)
                                    AlarmType.ZMAN -> ZmanAnchorSection(alarm, zmanPreview, viewModel::update)
                                }
                            }
                        }

                        SettingsGroup {
                            Column(modifier = Modifier.padding(14.dp)) {
                                SectionTitle("באילו ימים?")
                                Spacer(Modifier.height(10.dp))
                                DaysSelector(alarm, viewModel::update)
                            }
                        }
                    }
                }

                // ===================== צליל =====================
                1 -> {
                    SettingsGroup {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("אופן ההתראה", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = alarm.soundEnabled && alarm.vibrate,
                                    onClick = { viewModel.update { it.copy(soundEnabled = true, vibrate = true) } },
                                    label = { Text("צלצול ורטט") },
                                )
                                FilterChip(
                                    selected = alarm.soundEnabled && !alarm.vibrate,
                                    onClick = { viewModel.update { it.copy(soundEnabled = true, vibrate = false) } },
                                    label = { Text("צלצול בלבד") },
                                )
                                FilterChip(
                                    selected = !alarm.soundEnabled,
                                    onClick = { viewModel.update { it.copy(soundEnabled = false, vibrate = true) } },
                                    label = { Text("רטט בלבד") },
                                )
                            }
                        }

                        if (alarm.soundEnabled) {
                            GroupDivider()
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("צליל", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(10.dp))
                                QuickRingtonePicker(
                                    selectedUri = alarm.soundUri,
                                    onSelect = { uri -> viewModel.update { it.copy(soundUri = uri) } },
                                )
                            }

                            GroupDivider()
                            SettingRow(
                                title = "עוד צלילים…",
                                subtitle = alarm.soundUri?.let { soundTitle(context, it) } ?: "ברירת מחדל",
                                leading = {
                                    Icon(
                                        Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                onClick = {
                                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "בחר צליל")
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                        putExtra(
                                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                            alarm.soundUri?.let(Uri::parse),
                                        )
                                    }
                                    ringtoneLauncher.launch(intent)
                                },
                            )

                            GroupDivider()
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "עוצמה: ${alarm.volumePercent}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (alarm.volumePercent > 100) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "מוגבר",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Ext.colors.accentGold,
                                        )
                                    }
                                }
                                // 100% is the device maximum — everything above
                                // it is real amplification of the signal, so the
                                // scale deliberately stops at 120.
                                Slider(
                                    value = alarm.volumePercent.toFloat(),
                                    onValueChange = { v ->
                                        viewModel.update {
                                            it.copy(volumePercent = v.toInt().coerceIn(10, 120))
                                        }
                                    },
                                    valueRange = 10f..120f,
                                )
                                if (alarm.volumePercent > 100) {
                                    Text(
                                        "מעל 100% הצליל מוגבר מעבר למקסימום של המכשיר. " +
                                            "בחלק מהמכשירים ההגברה אינה נתמכת — ואז השעון " +
                                            "יצלצל בעוצמה מלאה רגילה.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            GroupDivider()
                            SwitchSettingRow(
                                title = "עלייה הדרגתית",
                                subtitle = if (alarm.gradualVolume) {
                                    "מתחיל חלש ומתחזק עד לעוצמה שנבחרה"
                                } else {
                                    "מצלצל מיד בעוצמה המלאה שנבחרה"
                                },
                                checked = alarm.gradualVolume,
                                onChange = { on ->
                                    viewModel.update { it.copy(gradualVolume = on) }
                                },
                            )
                        }

                        GroupDivider()
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                "משך הצלצול: ${formatRingDuration(alarm.ringDurationSeconds)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Slider(
                                // 10s … 3 min, in 5-second steps
                                value = alarm.ringDurationSeconds.toFloat(),
                                onValueChange = { v ->
                                    val snapped = (Math.round(v / 5f) * 5).coerceIn(10, 180)
                                    viewModel.update { it.copy(ringDurationSeconds = snapped) }
                                },
                                // No `steps`: it drew 33 tick dots along the
                                // track. Snapping to 5s lives in onValueChange
                                // above, so the dots were decoration only.
                                valueRange = 10f..180f,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.previewAlarm() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("תצוגה מקדימה לשעון")
                    }
                    Text(
                        "הדגמה חיה של מסך הצלצול — עם הצליל, העוצמה והרטט שבחרת. " +
                            "כדי לעצור, הקש \"אישור\" במסך שייפתח.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                // ===================== כיבוי =====================
                else -> {
                    SettingsGroup {
                        SwitchSettingRow(
                            title = "נודניק",
                            subtitle = if (alarm.maxSnoozes == 0) "כבוי" else "אפשר לדחות את הצלצול",
                            checked = alarm.maxSnoozes != 0,
                        ) { on -> viewModel.update { it.copy(maxSnoozes = if (on) -1 else 0) } }

                        if (alarm.maxSnoozes != 0) {
                            GroupDivider()
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("כל כמה דקות", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(5, 10, 15).forEach { minutes ->
                                        FilterChip(
                                            selected = alarm.snoozeMinutes == minutes,
                                            onClick = { viewModel.update { it.copy(snoozeMinutes = minutes) } },
                                            label = { Text("$minutes דק'") },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Text("מספר פעמים מותר", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(-1 to "ללא הגבלה", 3 to "3", 1 to "1").forEach { (n, label) ->
                                        FilterChip(
                                            selected = alarm.maxSnoozes == n,
                                            onClick = { viewModel.update { it.copy(maxSnoozes = n) } },
                                            label = { Text(label) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsGroup {
                        SwitchSettingRow(
                            title = "תרגיל חשבון לכיבוי",
                            subtitle = "כדי לכבות צריך לפתור תרגיל",
                            checked = alarm.dismissChallenge != DismissChallenge.NONE,
                        ) { on ->
                            viewModel.update {
                                it.copy(dismissChallenge = if (on) DismissChallenge.MATH_EASY else DismissChallenge.NONE)
                            }
                        }
                        if (alarm.dismissChallenge != DismissChallenge.NONE) {
                            GroupDivider()
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(
                                    DismissChallenge.MATH_EASY to "קל",
                                    DismissChallenge.MATH_MEDIUM to "בינוני",
                                    DismissChallenge.MATH_HARD to "קשה",
                                ).forEach { (level, label) ->
                                    FilterChip(
                                        selected = alarm.dismissChallenge == level,
                                        onClick = { viewModel.update { it.copy(dismissChallenge = level) } },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    }

                    SettingsGroup {
                        SwitchSettingRow(
                            title = "בדיקת ערות",
                            subtitle = "נשאל שוב אחרי שכיבית, לוודא שקמת",
                            checked = alarm.wakeCheckMinutes > 0,
                        ) { on -> viewModel.update { it.copy(wakeCheckMinutes = if (on) 5 else 0) } }

                        if (alarm.wakeCheckMinutes > 0) {
                            GroupDivider()
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("נשאל שוב בעוד", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(3, 5, 10).forEach { minutes ->
                                        FilterChip(
                                            selected = alarm.wakeCheckMinutes == minutes,
                                            onClick = { viewModel.update { it.copy(wakeCheckMinutes = minutes) } },
                                            label = { Text("$minutes דק'") },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Save stays reachable from every tab
        Surface(
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 8.dp,
        ) {
            Button(
                onClick = { viewModel.save(onBack) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text("שמירה", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** A rounded surface holding rows that are separated by [GroupDivider]. */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Column(content = content)
    }
}

/** Hairline between rows of a group — inset, so it reads as a separator. */
@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

/** Title (+ optional subtitle) row, optionally tappable, with a leading icon. */
@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { it(); Spacer(Modifier.width(12.dp)) }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** [SettingRow] whose trailing control is a switch; the whole row toggles. */
@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        onClick = { onChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

/**
 * The heart of the "smart anchor" UX: a two-way toggle letting the user pick
 * whether the alarm rings at a fixed clock time or is pinned to a halachic
 * zman that shifts every day with the date and location.
 */
@Composable
private fun AnchorTypeSelector(current: AlarmType, onSelect: (AlarmType) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = current == AlarmType.FIXED,
            onClick = { onSelect(AlarmType.FIXED) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {
                Icon(Icons.Filled.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            label = { Text("שעה קבועה") },
        )
        SegmentedButton(
            selected = current == AlarmType.ZMAN,
            onClick = { onSelect(AlarmType.ZMAN) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {
                Icon(Icons.Filled.WbTwilight, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            label = { Text("לפי זמן הלכתי") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FixedAnchorSection(alarm: AlarmEntity, update: ((AlarmEntity) -> AlarmEntity) -> Unit) {
    SectionTitle("באיזו שעה לקום?")
    // rememberTimePickerState is keyless, so it latches the FIRST composition's
    // values. The alarm loads asynchronously, so when EDITING an existing alarm
    // the picker used to keep showing the placeholder 06:00. Key it to the
    // loaded alarm's ROW ID ONLY — never to hour/minute. Those two values are
    // written BACK into `alarm` by the LaunchedEffect right below, which makes
    // them a moving target: keying on them meant every drag of the dial wrote
    // a new value, which changed the key, which discarded and rebuilt the
    // TimePickerState from scratch — freshly-built state always opens in Hour
    // mode, so the picker snapped back to the hour ring after every tap and a
    // continuous drag got redirected into the hour partway through. Keying on
    // alarm.id alone still re-initialises the moment the async load replaces
    // the id=0 placeholder with the real row, which is the only case this
    // needs to handle, and never again after that for the SAME alarm.
    val timeState = key(alarm.id) {
        rememberTimePickerState(
            initialHour = alarm.hour,
            initialMinute = alarm.minute,
            is24Hour = true,
        )
    }
    LaunchedEffect(timeState.hour, timeState.minute) {
        update { it.copy(hour = timeState.hour, minute = timeState.minute) }
    }
    // Same Material dial, just less of it. At full size it filled ~60% of
    // the screen and — measured on the emulator — swallowed the scroll
    // gesture: a swipe meant to reach "באילו ימים?" turned 06:00 into 00:00
    // instead. Shrinking it is the owner's call over replacing it; the
    // margins it gives back are where scrolling now works.
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TimePicker(state = timeState, modifier = Modifier.scaledDown(DIAL_SCALE))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZmanAnchorSection(
    alarm: AlarmEntity,
    preview: String?,
    update: ((AlarmEntity) -> AlarmEntity) -> Unit,
) {
    SectionTitle("לפי איזה זמן?")
    Text(
        "הצלצול נקבע ביחס לזמן ההלכתי ומתעדכן אוטומטית בכל יום לפי התאריך והמיקום שלך.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
    )
    var menuOpen by remember { mutableStateOf(false) }
    // Seeded from the alarm (a custom offset used to render as an empty box)
    // and kept across rotation / tab switches.
    val presets = listOf(0, 15, 30, 45, 60)
    var customMinutes by rememberSaveable(alarm.id) {
        mutableStateOf(alarm.offsetMinutes.takeIf { it !in presets }?.toString() ?: "")
    }

    ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
        OutlinedTextField(
            value = ZmanKind.fromNameOrNull(alarm.zmanId)?.hebrewName ?: alarm.zmanId,
            onValueChange = {},
            readOnly = true,
            label = { Text("זמן הלכתי") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuOpen) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ZmanKind.entries.forEach { kind ->
                DropdownMenuItem(
                    text = { Text(kind.hebrewName) },
                    onClick = {
                        update { it.copy(zmanId = kind.name) }
                        menuOpen = false
                    },
                )
            }
        }
    }

    Text("כמה דקות לפני?", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { minutes ->
            FilterChip(
                selected = alarm.offsetMinutes == minutes,
                onClick = {
                    customMinutes = ""
                    update { it.copy(offsetMinutes = minutes, offsetBefore = true) }
                },
                label = { Text(if (minutes == 0) "בזמן" else "$minutes'") },
            )
        }
    }
    OutlinedTextField(
        value = customMinutes,
        onValueChange = { v ->
            if (v.length <= 3 && v.all(Char::isDigit)) {
                customMinutes = v
                v.toIntOrNull()?.let { m -> update { it.copy(offsetMinutes = m, offsetBefore = true) } }
            }
        },
        label = { Text("או מספר דקות אחר") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    preview?.let {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(14.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DaysSelector(alarm: AlarmEntity, update: ((AlarmEntity) -> AlarmEntity) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DAY_LETTERS.forEachIndexed { index, letter ->
                val selected = (alarm.daysOfWeek shr index) and 1 == 1
                Surface(
                    shape = CircleShape,
                    // Tonal when unselected. `surface` on a `surface` card drew
                    // nothing at all, so an unticked day was a bare letter
                    // floating in white — indistinguishable from a label.
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            update { a ->
                                val next = a.daysOfWeek xor (1 shl index)
                                // Clearing the LAST day used to write 0, and 0
                                // is the one-time marker — which isEnabledOn
                                // answers TRUE for on every weekday. So a user
                                // who unticked everything, meaning "never",
                                // silently got an alarm that rang on whatever
                                // day came next: exactly the "it rings on days
                                // I did not choose" complaint. These circles
                                // now only ever describe a REPEATING schedule;
                                // one-time is the explicit chip below.
                                if (next == 0) a else a.copy(daysOfWeek = next)
                            }
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(letter, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        // Says what the current selection actually means. Without it "no days
        // ticked" and "rings once" looked identical on screen.
        Text(
            if (alarm.isOneTime) {
                "חד-פעמי — יצלצל פעם אחת בלבד, בהזדמנות הקרובה, ואז ייכבה."
            } else {
                "יחזור על עצמו בימים המסומנים."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = alarm.daysOfWeek == AlarmEntity.ALL_DAYS,
                onClick = { update { it.copy(daysOfWeek = AlarmEntity.ALL_DAYS) } },
                label = { Text("כל יום") },
            )
            FilterChip(
                selected = alarm.daysOfWeek == AlarmEntity.SUNDAY_TO_FRIDAY,
                onClick = { update { it.copy(daysOfWeek = AlarmEntity.SUNDAY_TO_FRIDAY) } },
                label = { Text("א'-ו'") },
            )
            FilterChip(
                selected = alarm.daysOfWeek == AlarmEntity.SUNDAY_TO_THURSDAY,
                onClick = { update { it.copy(daysOfWeek = AlarmEntity.SUNDAY_TO_THURSDAY) } },
                label = { Text("א'-ה'") },
            )
            // One-time is now a deliberate choice rather than something a user
            // falls into by clearing the day circles.
            FilterChip(
                selected = alarm.isOneTime,
                onClick = { update { it.copy(daysOfWeek = 0) } },
                label = { Text("חד-פעמי") },
            )
        }
        SwitchRow("דלג ביום טוב", alarm.skipYomTov) { v -> update { it.copy(skipYomTov = v) } }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Six quick ringtone choices from the device's built-in ALARM sounds —
 * tap = select + short audible preview (auto-stops after a few seconds).
 * "עוד צלילים…" below opens the full system picker for everything else.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun QuickRingtonePicker(
    selectedUri: String?,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val tones = remember {
        runCatching {
            val rm = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
            // use{}: the cursor used to leak on every visit to the צליל tab
            rm.cursor.use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < 6) {
                        add(
                            cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) to
                                rm.getRingtoneUri(cursor.position).toString()
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
    if (tones.isEmpty()) return

    var playing by remember { mutableStateOf<android.media.Ringtone?>(null) }
    var playingUri by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { runCatching { playing?.stop() } }
    }

    fun stop() {
        runCatching { playing?.stop() }
        playing = null
        playingUri = null
    }

    /** Preview [uri] for a few seconds, replacing whatever was playing. */
    fun preview(uri: String) {
        stop()
        scope.launch {
            val r = RingtoneManager.getRingtone(context, Uri.parse(uri))
            playing = r
            playingUri = uri
            runCatching { r?.play() }
            kotlinx.coroutines.delay(PREVIEW_MILLIS)
            if (playing === r) stop()
        }
    }

    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tones.forEach { (title, uri) ->
            val isPlaying = playingUri == uri
            FilterChip(
                selected = selectedUri == uri,
                // The chip body still selects — and previews, so a single tap
                // does the obvious thing, which is what Android's own ringtone
                // picker does.
                onClick = {
                    onSelect(uri)
                    preview(uri)
                },
                leadingIcon = {
                    // The play control is separate on purpose: auditioning a
                    // sound and committing to it are different intents. Without
                    // this the only way to hear a ringtone was to pick it, so
                    // comparing three of them meant changing the alarm three
                    // times. Tapping here previews and leaves the selection
                    // alone; tapping again stops it.
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "עצור השמעה" else "השמע את $title",
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { if (isPlaying) stop() else preview(uri) },
                    )
                },
                label = { Text(title, maxLines = 1) },
            )
        }
    }
}

/** Long enough to judge a ringtone, short enough not to become the alarm. */
private const val PREVIEW_MILLIS = 5_000L

/** The dial is drawn at this fraction of Material's default size — see FixedAnchorSection. */
private const val DIAL_SCALE = 0.84f

/** One editor tab: its title and the small icon beside it. */
private data class EditorTab(val title: String, val icon: ImageVector)

/**
 * The editor's three tabs as a rounded pill bar — a tonal track with the
 * selected tab drawn as a filled pill that fades between positions.
 *
 * Replaces Material's TabRow, whose underline-and-divider look the owner
 * asked to lose while keeping the tabs themselves. Colours animate rather
 * than a sliding indicator: three equal-width pills fading is visually
 * indistinguishable from a slide at this size and needs no measuring.
 */
@Composable
private fun PillTabs(tabs: List<EditorTab>, selected: Int, onSelect: (Int) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selected
                val background by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    label = "tabBackground",
                )
                val foreground by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tabForeground",
                )
                Surface(
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(50),
                    color = background,
                    contentColor = foreground,
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tab.title, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

/**
 * Draw a composable at [scale] of its natural size AND take up only that
 * much room in the layout.
 *
 * `Modifier.scale` alone shrinks the pixels but leaves the original footprint
 * behind, so the dial would have floated in a void the size it used to be.
 * Measuring at full size and reporting the scaled size gives the surrounding
 * column the space back. `placeWithLayer` applies the transform to hit
 * testing too, so taps still land on the numbers they appear on.
 */
private fun Modifier.scaledDown(scale: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val width = (placeable.width * scale).roundToInt()
    val height = (placeable.height * scale).roundToInt()
    layout(width, height) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

/** "40 שניות" / "דקה" / "1:30 דקות" / "3 דקות". */
private fun formatRingDuration(seconds: Int): String = when {
    seconds < 60 -> "$seconds שניות"
    seconds == 60 -> "דקה"
    seconds % 60 == 0 -> "${seconds / 60} דקות"
    else -> "%d:%02d דקות".format(seconds / 60, seconds % 60)
}

private fun soundTitle(context: android.content.Context, uriString: String): String =
    runCatching {
        RingtoneManager.getRingtone(context, Uri.parse(uriString))?.getTitle(context)
    }.getOrNull() ?: "צליל מותאם"
