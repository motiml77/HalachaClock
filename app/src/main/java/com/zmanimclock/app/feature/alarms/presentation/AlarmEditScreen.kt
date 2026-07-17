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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.alarms.data.AlarmType
import com.zmanimclock.app.feature.alarms.data.DismissChallenge
import com.zmanimclock.app.feature.zmanim.model.ZmanKind

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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    when {
                        alarm.shabbatMode -> "התראת כניסת שבת"
                        alarm.type == AlarmType.ZMAN -> "שעון לפי זמן הלכתי"
                        else -> "שעון מעורר"
                    }
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (alarm.shabbatMode) {
                Card {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("🕯️ התראת כניסת שבת", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "תופיע בכל יום שישי, ${alarm.offsetMinutes} דק' לפני שקיעת " +
                                "המקום שלך, עם מסך נרות מיוחד. בחר לה צליל ועוצמה משלה.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            // === Anchor ===
            when (alarm.type) {
                AlarmType.FIXED -> FixedAnchorSection(alarm, viewModel::update)
                AlarmType.ZMAN -> ZmanAnchorSection(alarm, zmanPreview, viewModel::update)
            }

            // === Repeat days ===
            SectionTitle("באילו ימים?")
            DaysSelector(alarm, viewModel::update)

            // === Sound & volume ===
            SectionTitle("צליל ורטט")
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Ring mode: sound+vibrate / sound only / vibrate only
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

                    if (alarm.soundEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
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
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(
                                    if (alarm.shabbatMode) "צליל מיוחד לכניסת שבת" else "צליל",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = alarm.soundUri?.let { soundTitle(context, it) } ?: "ברירת מחדל",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }

                        Text("עוצמה: ${alarm.volumePercent}%", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = alarm.volumePercent.toFloat(),
                            onValueChange = { v ->
                                viewModel.update { it.copy(volumePercent = v.toInt().coerceIn(10, 100)) }
                            },
                            valueRange = 10f..100f,
                        )
                    }

                    Text("משך התראה", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 5, 10).forEach { minutes ->
                            FilterChip(
                                selected = alarm.ringDurationMinutes == minutes,
                                onClick = { viewModel.update { it.copy(ringDurationMinutes = minutes) } },
                                label = { Text("$minutes דק'") },
                            )
                        }
                    }
                }
            }

            // === Dismissal ===
            SectionTitle("כיבוי ההתראה")
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SwitchRow(
                        "תרגיל חשבון לכיבוי",
                        alarm.dismissChallenge != DismissChallenge.NONE,
                    ) { on ->
                        viewModel.update {
                            it.copy(dismissChallenge = if (on) DismissChallenge.MATH_EASY else DismissChallenge.NONE)
                        }
                    }
                    if (alarm.dismissChallenge != DismissChallenge.NONE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Text("נודניק", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15).forEach { minutes ->
                            FilterChip(
                                selected = alarm.snoozeMinutes == minutes,
                                onClick = { viewModel.update { it.copy(snoozeMinutes = minutes) } },
                                label = { Text("$minutes דק'") },
                            )
                        }
                    }

                    // B3: snooze limit (anti-snooze)
                    Text("מספר נודניקים מותר", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(-1 to "ללא הגבלה", 3 to "3", 1 to "1", 0 to "בלי נודניק").forEach { (n, label) ->
                            FilterChip(
                                selected = alarm.maxSnoozes == n,
                                onClick = { viewModel.update { it.copy(maxSnoozes = n) } },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            // === Wake-up check (B1) ===
            SectionTitle("בדיקת ערות")
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SwitchRow("בדוק שהתעוררתי אחרי אישור", alarm.wakeCheckMinutes > 0) { on ->
                        viewModel.update { it.copy(wakeCheckMinutes = if (on) 5 else 0) }
                    }
                    if (alarm.wakeCheckMinutes > 0) {
                        Text(
                            "אחרי אישור השעון — נשאל שוב בעוד:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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

            // === Name + save ===
            OutlinedTextField(
                value = alarm.label,
                onValueChange = { v -> viewModel.update { it.copy(label = v) } },
                label = { Text("שם השעון") },
                placeholder = { Text(defaultAlarmLabel(alarm)) },
                supportingText = { Text("ריק = השם המוצע") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { viewModel.save(onBack) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("שמירה", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FixedAnchorSection(alarm: AlarmEntity, update: ((AlarmEntity) -> AlarmEntity) -> Unit) {
    SectionTitle("באיזו שעה לקום?")
    val timeState = rememberTimePickerState(
        initialHour = alarm.hour,
        initialMinute = alarm.minute,
        is24Hour = true,
    )
    LaunchedEffect(timeState.hour, timeState.minute) {
        update { it.copy(hour = timeState.hour, minute = timeState.minute) }
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TimePicker(state = timeState)
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
    var menuOpen by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableStateOf("") }

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
        listOf(0, 15, 30, 45, 60).forEach { minutes ->
            FilterChip(
                selected = alarm.offsetMinutes == minutes && customMinutes.isBlank(),
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
        Card {
            Text(
                text = "⏰ $it",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            )
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
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            update { it.copy(daysOfWeek = it.daysOfWeek xor (1 shl index)) }
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(letter, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
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
        }
        SwitchRow("דלג בשבת", alarm.skipShabbat) { v -> update { it.copy(skipShabbat = v) } }
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

private fun soundTitle(context: android.content.Context, uriString: String): String =
    runCatching {
        RingtoneManager.getRingtone(context, Uri.parse(uriString))?.getTitle(context)
    }.getOrNull() ?: "צליל מותאם"
