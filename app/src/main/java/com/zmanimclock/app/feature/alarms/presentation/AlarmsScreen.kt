package com.zmanimclock.app.feature.alarms.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.alarms.data.AlarmType
import com.zmanimclock.app.feature.alarms.data.DismissChallenge
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.ui.theme.AppIcons
import com.zmanimclock.app.ui.theme.Ext
import com.zmanimclock.app.ui.theme.ZmanListTimeStyle

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
        onSkipNext = viewModel::toggleSkipNext,
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
    onSkipNext: (AlarmEntity) -> Unit,
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
            // Grouped by WHEN the next ring falls, sorted by time inside each
            // group so it's clear what's coming and in what order.
            val groups = listOf(
                FireBucket.TODAY to "היום",
                FireBucket.TOMORROW to "מחר",
                FireBucket.LATER to "שבוע הבא",
                FireBucket.OFF to "כבויים",
            )
            val byBucket = items.groupBy { it.bucket }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // bottom inset clears the FAB so the last card stays tappable
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { (bucket, title) ->
                    val group = byBucket[bucket].orEmpty().sortedWith(
                        compareBy({ it.nextFireEpochMs ?: Long.MAX_VALUE }, { it.alarm.id })
                    )
                    if (group.isNotEmpty()) {
                        item(key = "header_$bucket") { SectionHeader(title, group.size) }
                        items(group, key = { it.alarm.id }) { item ->
                            AlarmCard(item, onToggle, onDelete, onSkipNext, onClick = { onEditAlarm(item.alarm.id) })
                        }
                    }
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

/** Category header ("שעונים מעוררים · 3"). */
@Composable
private fun SectionHeader(title: String, count: Int) {
    Text(
        text = "$title · $count",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
    )
}

/**
 * Compact alarm card (~76dp → 6+ fit without scrolling).
 * Inactive alarms stay listed but dimmed; the round ✓ next to the alarm
 * re-activates (or deactivates) it. Trash deletes permanently.
 */
@Composable
private fun AlarmCard(
    item: AlarmListItem,
    onToggle: (AlarmEntity, Boolean) -> Unit,
    onDelete: (AlarmEntity) -> Unit,
    onSkipNext: (AlarmEntity) -> Unit,
    onClick: () -> Unit,
) {
    val alarm = item.alarm
    val cs = MaterialTheme.colorScheme
    val isZman = alarm.type == AlarmType.ZMAN
    val active = alarm.isActive

    // OFF state: quiet, not buried. A grey fill plus heavy dimming made the
    // card read as a smudge — the surface colour fought the text and the row
    // became hard to scan. Instead an off alarm keeps the SAME surface as an
    // active one and is marked by three light touches: no elevation, a
    // hairline outline, and a drained accent stripe. The text only steps down
    // one level of emphasis, so the name and time stay properly readable.
    val contentAlpha = if (active) 1f else 0.72f

    // Accent stripe identifies the alarm's kind at a glance, in our palette:
    // navy = wake-up clock, gold = halachic zman, candle-gold = Shabbat entry.
    val accent = when {
        alarm.shabbatMode -> Ext.colors.accentGold
        isZman -> cs.tertiary
        else -> cs.primary
    }
    // Keep a HINT of the kind colour when off — a grey stripe would throw away
    // the one cue that says what this alarm is.
    val stripeColor = if (active) accent else accent.copy(alpha = 0.28f)
    val timeColor = if (active) cs.primary else cs.onSurfaceVariant

    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (active) 1.dp else 0.dp),
        border = if (active) null else androidx.compose.foundation.BorderStroke(
            1.dp, cs.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Full-height colour stripe on the leading (right, RTL) edge
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(stripeColor),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
                    .alpha(contentAlpha),
            ) {
                // Name first — the clearest identifier
                Text(
                    text = alarm.label.ifBlank { defaultAlarmLabel(alarm) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                // Time, large — zman alarms show the computed next fire time
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isZman) {
                        Icon(
                            imageVector = if (alarm.shabbatMode) AppIcons.Candle
                            else Icons.Filled.WbTwilight,
                            contentDescription = null,
                            tint = stripeColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (isZman) (item.nextFireTime ?: "--:--")
                        else "%02d:%02d".format(alarm.hour, alarm.minute),
                        style = ZmanListTimeStyle.copy(fontSize = 26.sp, lineHeight = 28.sp),
                        color = timeColor,
                    )
                }
                // Detail lines: schedule, then the countdown
                Text(
                    text = daysText(alarm),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                )
                item.nextFireLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (alarm.isActive && !alarm.isOneTime) {
                    val skipping = alarm.skipUntilEpochMs > System.currentTimeMillis()
                    Text(
                        text = if (skipping) "מדלג על הבאה — בטל" else "דלג על הבאה",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (skipping) Ext.colors.accentGold else cs.primary,
                        modifier = Modifier.clickable { onSkipNext(alarm) },
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // The ✓: active = filled check; inactive = empty circle to tap
                IconButton(
                    onClick = { onToggle(alarm, !alarm.isActive) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = if (alarm.isActive) Icons.Filled.CheckCircle
                        else Icons.Outlined.Circle,
                        contentDescription = if (alarm.isActive) "פעיל — הקש לכיבוי" else "כבוי — הקש להפעלה",
                        tint = if (alarm.isActive) cs.primary else cs.outline,
                        modifier = Modifier.size(26.dp),
                    )
                }
                IconButton(onClick = { onDelete(alarm) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "מחק לצמיתות",
                        tint = cs.onSurfaceVariant.copy(alpha = if (active) 1f else 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
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
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "שעון מעורר חדש",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 2.dp),
            )

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
                icon = AppIcons.Candle,
                iconTint = MaterialTheme.colorScheme.tertiary,
                iconBg = MaterialTheme.colorScheme.tertiaryContainer,
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
    title: String,
    subtitle: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    // The highlighted option is TONAL, not outlined. A 2dp primary border
    // reads as keyboard focus — "this one is selected" — on a sheet where
    // nothing is selected yet; a light primary fill reads as "the usual
    // choice", which is what it is. Its icon tile inverts to stay visible
    // against that fill.
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) cs.primaryContainer else cs.surface,
        ),
        border = if (highlighted) null else androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (highlighted) cs.primary else iconBg,
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                icon?.let {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = if (highlighted) cs.onPrimary else iconTint,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 11.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}
