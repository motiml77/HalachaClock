package com.zmanimclock.app.feature.alarms.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.alarms.data.AlarmType
import com.zmanimclock.app.feature.alarms.data.DismissChallenge
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.ui.theme.Ext

private val DAY_LETTERS = listOf("א", "ב", "ג", "ד", "ה", "ו", "ש")

/**
 * טאב המעורר — style 1D (§6.6, §7.2–7.4): AlarmCards on the app canvas,
 * type-chooser bottom sheet, FAB.
 */
@Composable
fun AlarmsScreen(
    onCreateAlarm: (AlarmType) -> Unit,
    onCreateShabbatAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    viewModel: AlarmsViewModel = hiltViewModel(),
) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    var showTypeChooser by remember { mutableStateOf(false) }

    AlarmsContent(
        items = alarms,
        onToggle = viewModel::toggleAlarm,
        onDelete = viewModel::deleteAlarm,
        onAddClick = { showTypeChooser = true },
        onEditAlarm = onEditAlarm,
    )

    if (showTypeChooser) {
        AlarmTypeChooserSheet(
            onChoose = { type ->
                showTypeChooser = false
                onCreateAlarm(type)
            },
            onChooseShabbat = {
                showTypeChooser = false
                onCreateShabbatAlarm()
            },
            onDismiss = { showTypeChooser = false },
        )
    }
}

@Composable
fun AlarmsContent(
    items: List<AlarmListItem>,
    onToggle: (AlarmEntity, Boolean) -> Unit,
    onDelete: (AlarmEntity) -> Unit,
    onAddClick: () -> Unit,
    onEditAlarm: (Long) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Alarm,
                    contentDescription = null,
                    modifier = Modifier.size(76.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Text("אין שעונים מעוררים", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "הוסף שעון רגיל או שעון לפי זמן הלכתי",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items, key = { it.alarm.id }) { item ->
                    AlarmCard(item, onToggle, onDelete, onClick = { onEditAlarm(item.alarm.id) })
                }
            }
        }

        FloatingActionButton(
            onClick = onAddClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(26.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "הוסף שעון מעורר")
        }
    }
}

/** §6.6 — alarm card: tinted icon container, big time / anchor + live label. */
@Composable
private fun AlarmCard(
    item: AlarmListItem,
    onToggle: (AlarmEntity, Boolean) -> Unit,
    onDelete: (AlarmEntity) -> Unit,
    onClick: () -> Unit,
) {
    val alarm = item.alarm
    val cs = MaterialTheme.colorScheme
    val ext = Ext.colors
    val isZman = alarm.type == AlarmType.ZMAN

    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // icon container
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        if (isZman) cs.tertiaryContainer else cs.primaryContainer,
                        RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (alarm.shabbatMode) {
                    Text("🕯️", style = MaterialTheme.typography.titleLarge)
                } else {
                    Icon(
                        imageVector = if (isZman) Icons.Filled.WbTwilight else Icons.Filled.Alarm,
                        contentDescription = null,
                        tint = if (isZman) cs.onTertiaryContainer else cs.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                if (isZman) {
                    Text(
                        text = zmanAnchorText(alarm),
                        style = MaterialTheme.typography.titleLarge,
                        color = cs.onSurface,
                    )
                } else {
                    Text(
                        text = "%02d:%02d".format(alarm.hour, alarm.minute),
                        style = MaterialTheme.typography.displaySmall,
                        color = cs.primary,
                    )
                }
                // "מחר: 05:15 · בעוד 9 ש' ו-33 דק'" — on EVERY active alarm
                item.nextFireLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        // gold family: #7A5E00 on light, #F5C518 on dark
                        color = cs.tertiary,
                    )
                }
                Text(
                    text = daysText(alarm),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }

            IconButton(onClick = { onDelete(alarm) }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "מחק",
                    tint = cs.onSurfaceVariant,
                )
            }
            Switch(
                checked = alarm.isActive,
                onCheckedChange = { onToggle(alarm, it) },
            )
        }
    }
}

private fun zmanAnchorText(alarm: AlarmEntity): String {
    val name = ZmanKind.fromNameOrNull(alarm.zmanId)?.hebrewName ?: alarm.zmanId
    return if (alarm.offsetMinutes == 0) {
        name
    } else {
        "${alarm.offsetMinutes} דק' ${if (alarm.offsetBefore) "לפני" else "אחרי"} $name"
    }
}

private fun daysText(alarm: AlarmEntity): String {
    val base = when (alarm.daysOfWeek) {
        0 -> "חד-פעמי"
        AlarmEntity.ALL_DAYS -> "כל יום"
        AlarmEntity.SUNDAY_TO_FRIDAY -> "א'-ו'"
        AlarmEntity.SUNDAY_TO_THURSDAY -> "א'-ה'"
        AlarmEntity.FRIDAY_ONLY -> "כל שישי"
        else -> DAY_LETTERS.filterIndexed { i, _ -> (alarm.daysOfWeek shr i) and 1 == 1 }
            .joinToString(" ")
    }
    return buildString {
        append(base)
        if (alarm.skipShabbat && alarm.daysOfWeek == AlarmEntity.ALL_DAYS) append(" · ללא שבת")
        if (alarm.skipYomTov) append(" · ללא יו\"ט")
        if (alarm.dismissChallenge != DismissChallenge.NONE) append(" · תרגיל לכיבוי")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmTypeChooserSheet(
    onChoose: (AlarmType) -> Unit,
    onChooseShabbat: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("שעון מעורר חדש", style = MaterialTheme.typography.headlineSmall)

            TypeCard(
                icon = Icons.Filled.Alarm,
                iconTint = MaterialTheme.colorScheme.primary,
                iconBg = MaterialTheme.colorScheme.primaryContainer,
                title = "שעון מעורר רגיל",
                subtitle = "שעה קבועה — למשל 06:30 כל בוקר",
                highlighted = true,
                onClick = { onChoose(AlarmType.FIXED) },
            )
            TypeCard(
                icon = Icons.Filled.WbTwilight,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                iconBg = MaterialTheme.colorScheme.tertiaryContainer,
                title = "שעון לפי זמן הלכתי",
                subtitle = "למשל 30 דק' לפני הנץ — מתעדכן כל יום לפי המיקום",
                onClick = { onChoose(AlarmType.ZMAN) },
            )
            TypeCard(
                emoji = "🕯️",
                title = "התראת כניסת שבת",
                subtitle = "כל שישי, 4 דק' לפני השקיעה — מסך נרות וצליל מיוחד",
                onClick = onChooseShabbat,
            )
        }
    }
}

@Composable
private fun TypeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    iconBg: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    emoji: String? = null,
    title: String,
    subtitle: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            if (highlighted) cs.primary else cs.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(iconBg, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    icon != null -> Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
                    emoji != null -> Text(emoji, style = MaterialTheme.typography.headlineSmall)
                }
            }
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}
