package com.zmanimclock.app.feature.alarms.presentation

import androidx.compose.foundation.background
import java.time.LocalDate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.clip
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.alarms.data.AlarmType
import com.zmanimclock.app.feature.alarms.data.DismissChallenge
import com.zmanimclock.app.ui.WheatEar
import com.zmanimclock.app.ui.theme.AppIcons
import com.zmanimclock.app.ui.theme.Ext
import com.zmanimclock.app.ui.theme.ZmanListTimeStyle

private val DAY_LETTERS = listOf("א", "ב", "ג", "ד", "ה", "ו", "ש")

/**
 * Sefirat HaOmer's own accent — matches [com.zmanimclock.app.ui.OmerGold],
 * the wheat-gold used everywhere else this feature appears (Settings, the
 * ring screen). Deliberately its own hue, distinct from Shabbat's brighter
 * gold and the ordinary zman amber.
 */
private val OmerAccent = com.zmanimclock.app.ui.OmerGold

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
            AlarmTimeline(items, onToggle, onDelete, onSkipNext, onEditAlarm)
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

/**
 * The alarms as a timeline (design ב): grouped by the day each one will next
 * ring — היום, מחר, then the weekday — in ring order, joined by a thin line
 * with a dot in each alarm's own colour. Alarms that are switched off sit in
 * a folded section at the bottom.
 *
 * Deleting is a swipe on the card (or a long press), always followed by a
 * confirmation — the old always-visible trash icon sat right next to the
 * on/off control and was one stray tap away from losing an alarm.
 */
@Composable
private fun AlarmTimeline(
    items: List<AlarmListItem>,
    onToggle: (AlarmEntity, Boolean) -> Unit,
    onDelete: (AlarmEntity) -> Unit,
    onSkipNext: (AlarmEntity) -> Unit,
    onEditAlarm: (Long) -> Unit,
) {
    val today = LocalDate.now()
    val scheduled = items
        .filter { it.alarm.isActive && it.nextFireDate != null }
        .sortedWith(compareBy({ it.nextFireEpochMs ?: Long.MAX_VALUE }, { it.alarm.id }))
    val byDay = scheduled.groupBy { it.nextFireDate!! }
    // On, but with nothing to ring in the lookahead (an omer alert out of season).
    val idle = items.filter { it.alarm.isActive && it.nextFireDate == null }
    val off = items.filter { !it.alarm.isActive }.sortedBy { it.alarm.id }

    var showOff by rememberSaveable { mutableStateOf(scheduled.isEmpty() && idle.isEmpty()) }
    var deleting by remember { mutableStateOf<AlarmEntity?>(null) }

    @Composable
    fun entry(item: AlarmListItem, first: Boolean, last: Boolean) {
        TimelineEntry(
            item = item,
            first = first,
            last = last,
            onToggle = onToggle,
            onSkipNext = onSkipNext,
            onEdit = { onEditAlarm(item.alarm.id) },
            onDeleteRequest = { deleting = item.alarm },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // bottom inset clears the FAB so the last card stays tappable
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 96.dp),
    ) {
        byDay.forEach { (date, group) ->
            item(key = "day_$date") { DayHeader(dayHeading(date, today)) }
            group.forEachIndexed { i, item ->
                item(key = item.alarm.id) { entry(item, first = i == 0, last = i == group.lastIndex) }
            }
        }
        if (idle.isNotEmpty()) {
            item(key = "idle") { DayHeader("ללא צלצול בקרוב") }
            idle.forEachIndexed { i, item ->
                item(key = item.alarm.id) { entry(item, first = i == 0, last = i == idle.lastIndex) }
            }
        }
        if (off.isNotEmpty()) {
            item(key = "off") { OffHeader(count = off.size, expanded = showOff, onClick = { showOff = !showOff }) }
            if (showOff) {
                off.forEachIndexed { i, item ->
                    item(key = item.alarm.id) { entry(item, first = i == 0, last = i == off.lastIndex) }
                }
            }
        }
    }

    deleting?.let { alarm ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text("למחוק את השעון?") },
            text = { Text("\"${alarm.label.ifBlank { defaultAlarmLabel(alarm) }}\" יימחק לצמיתות.") },
            confirmButton = {
                TextButton(onClick = { onDelete(alarm); deleting = null }) {
                    Text("מחיקה", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("ביטול") } },
        )
    }
}

/** "היום · יום חמישי", "מחר · יום שישי", or "יום ראשון · 11.10". */
private fun dayHeading(date: LocalDate, today: LocalDate): String {
    val weekday = when (date.dayOfWeek) {
        java.time.DayOfWeek.SUNDAY -> "יום ראשון"
        java.time.DayOfWeek.MONDAY -> "יום שני"
        java.time.DayOfWeek.TUESDAY -> "יום שלישי"
        java.time.DayOfWeek.WEDNESDAY -> "יום רביעי"
        java.time.DayOfWeek.THURSDAY -> "יום חמישי"
        java.time.DayOfWeek.FRIDAY -> "יום שישי"
        java.time.DayOfWeek.SATURDAY -> "שבת"
    }
    return when (date) {
        today -> "היום · $weekday"
        today.plusDays(1) -> "מחר · $weekday"
        else -> "$weekday · ${date.dayOfMonth}.${date.monthValue}"
    }
}

@Composable
private fun DayHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, top = 12.dp, bottom = 2.dp),
    )
}

/** "כבויים · 2" with a chevron — the switched-off alarms fold away. */
@Composable
private fun OffHeader(count: Int, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = 12.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "כבויים · $count",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "הסתרה" else "הצגה",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The alarm's own colour: a clock time, a zman, Shabbat entry, the omer. */
@Composable
private fun kindColor(alarm: AlarmEntity): Color = when {
    alarm.shabbatMode -> Ext.colors.accentGold
    alarm.omerMode -> OmerAccent
    alarm.type == AlarmType.ZMAN -> Ext.colors.zmanAccent
    else -> MaterialTheme.colorScheme.primary
}

/** One row of the timeline: the line and its dot, then the swipeable card. */
@Composable
private fun TimelineEntry(
    item: AlarmListItem,
    first: Boolean,
    last: Boolean,
    onToggle: (AlarmEntity, Boolean) -> Unit,
    onSkipNext: (AlarmEntity) -> Unit,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val active = item.alarm.isActive
    val dot = kindColor(item.alarm)
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Column(
            modifier = Modifier
                .width(16.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.width(2.dp).height(18.dp).background(if (first) Color.Transparent else line))
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (active) dot else dot.copy(alpha = 0.3f)),
            )
            Box(Modifier.width(2.dp).weight(1f).background(if (last) Color.Transparent else line))
        }
        Spacer(Modifier.width(6.dp))
        SwipeToDelete(
            onRequest = onDeleteRequest,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 3.dp),
        ) {
            AlarmTimelineCard(item, onToggle, onSkipNext, onClick = onEdit, onLongPress = onDeleteRequest)
        }
    }
}

/** A swipe either way shows the delete colour; letting go asks first and the card springs back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onRequest: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) onRequest()
            false // never dismiss here — the dialog decides
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val cs = MaterialTheme.colorScheme
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cs.errorContainer)
                    .padding(horizontal = 18.dp),
                contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "מחיקה", tint = cs.onErrorContainer)
            }
        },
    ) { content() }
}

/**
 * The card itself: time (with the time left beside it), switch, then the
 * name and repeat days on one line, and "דלג על הבא" under them. Shabbat
 * entry gets a gold wash. Smaller type than the old cards — the day heading
 * now carries "when", so the card only has to say "what".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmTimelineCard(
    item: AlarmListItem,
    onToggle: (AlarmEntity, Boolean) -> Unit,
    onSkipNext: (AlarmEntity) -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val alarm = item.alarm
    val cs = MaterialTheme.colorScheme
    val active = alarm.isActive
    val isZman = alarm.type == AlarmType.ZMAN
    val gold = Ext.colors.accentGold
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val shabbat = alarm.shabbatMode && active
    val accent = kindColor(alarm)

    // Opaque: the swipe-to-delete layer sits right under the card, and a
    // see-through wash would let its red and trash icon show through.
    val container = if (shabbat) gold.copy(alpha = if (dark) 0.18f else 0.14f).compositeOver(cs.surface) else cs.surface
    val border = if (shabbat) gold.copy(alpha = 0.45f) else cs.outlineVariant.copy(alpha = 0.6f)
    val timeColor = when {
        !active -> cs.onSurfaceVariant.copy(alpha = 0.7f)
        shabbat && !dark -> SkipInk
        else -> cs.primary
    }
    val textColor = if (shabbat && !dark) SkipInk else cs.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                alarm.omerMode -> {
                    WheatEar(modifier = Modifier.size(16.dp), tint = accent)
                    Spacer(Modifier.width(5.dp))
                }
                isZman -> {
                    Icon(
                        imageVector = if (alarm.shabbatMode) AppIcons.Candle else Icons.Filled.WbTwilight,
                        contentDescription = null,
                        tint = if (active) accent else accent.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                }
            }
            Text(
                text = if (isZman) (item.nextFireTime ?: "--:--") else "%02d:%02d".format(alarm.hour, alarm.minute),
                style = ZmanListTimeStyle.copy(fontSize = 22.sp, lineHeight = 26.sp),
                color = timeColor,
            )
            // The heading says which day; the card adds how long until then.
            item.nextFireLabel?.substringAfter(" · ", "")?.takeIf { active && it.isNotEmpty() }?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (shabbat && !dark) SkipInk else cs.tertiary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            Switch(
                checked = active,
                onCheckedChange = { onToggle(alarm, it) },
                modifier = Modifier.scale(0.78f),
                colors = SwitchDefaults.colors(checkedTrackColor = cs.primary),
            )
        }
        Text(
            text = "${alarm.label.ifBlank { defaultAlarmLabel(alarm) }} · ${daysText(alarm)}",
            style = MaterialTheme.typography.bodySmall,
            color = if (active) textColor else textColor.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // "דלג על הבא": only for a repeating alarm — a one-time alarm has no
        // "after" to keep, so skipping it would just be turning it off. The
        // alarm itself stays on for every later ring.
        if (active && !alarm.isOneTime) {
            Spacer(Modifier.height(6.dp))
            val skipped = item.skippedLabel
            if (skipped == null) {
                SkipNextChip(onClick = { onSkipNext(alarm) })
            } else {
                SkippingRow(skipped = skipped, onUndo = { onSkipNext(alarm) })
            }
        }
    }
}

/** The quiet outlined chip that skips only the next ring. */
@Composable
private fun SkipNextChip(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.SkipNext, contentDescription = null, tint = cs.primary, modifier = Modifier.size(16.dp).mirrorInRtl())
        Spacer(Modifier.width(4.dp))
        Text("דלג על הבא", style = MaterialTheme.typography.labelMedium, color = cs.primary)
    }
}

/** While a skip is set: which ring is skipped, and a way back. */
@Composable
private fun SkippingRow(skipped: String, onUndo: () -> Unit) {
    val gold = Ext.colors.accentGold
    // On a dark surface the wash is dark too, so the gold itself is the ink there.
    val ink = if (androidx.compose.foundation.isSystemInDarkTheme()) gold else SkipInk
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(gold.copy(alpha = 0.16f))
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.SkipNext, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp).mirrorInRtl())
        Spacer(Modifier.width(4.dp))
        Text(
            text = "מדלג על $skipped",
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onUndo) {
            Text("ביטול הדילוג", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ink)
        }
    }
}

/** "Next" points the way the text runs: leftwards in Hebrew. SkipNext has no auto-mirrored twin. */
@Composable
private fun Modifier.mirrorInRtl(): Modifier =
    if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) {
        this.graphicsLayer { scaleX = -1f }
    } else this

/** Dark gold ink for text on the gold skip wash — the gold itself is too light to read on it. */
private val SkipInk = androidx.compose.ui.graphics.Color(0xFF6B4E00)

private fun daysText(alarm: AlarmEntity): String {
    val base = when {
        // Not really "every day" — AlarmTimeCalculator.isDayAllowed only
        // lets 49 of them through, and daysOfWeek staying ALL_DAYS is what
        // makes that possible rather than something to describe literally.
        alarm.omerMode -> "ימי הספירה"
        alarm.daysOfWeek == 0 -> "חד-פעמי"
        alarm.daysOfWeek == AlarmEntity.ALL_DAYS -> "כל יום"
        alarm.daysOfWeek == AlarmEntity.SUNDAY_TO_FRIDAY -> "א'-ו'"
        alarm.daysOfWeek == AlarmEntity.SUNDAY_TO_THURSDAY -> "א'-ה'"
        alarm.daysOfWeek == AlarmEntity.FRIDAY_ONLY -> "כל שישי"
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
                iconTint = Ext.colors.zmanAccent,
                iconBg = Ext.colors.zmanAccent.copy(alpha = 0.16f),
                title = "שעון לפי זמן הלכתי",
                subtitle = "למשל 30 דק' לפני הנץ — מתעדכן כל יום לפי המיקום",
                onClick = { onChoose(AlarmType.ZMAN) },
            )
            TypeCard(
                icon = AppIcons.Candle,
                iconTint = MaterialTheme.colorScheme.tertiary,
                iconBg = MaterialTheme.colorScheme.tertiaryContainer,
                title = "התראת כניסת שבת",
                subtitle = "כל שישי, $SHABBAT_ENTRY_OFFSET_MINUTES דק' לפני השקיעה — מסך נרות וצליל מיוחד",
                onClick = onChooseShabbat,
            )
            // Sefirat HaOmer is NOT offered here: it is a season-only alert
            // controlled from Settings (and offered once by the first-night
            // prompt), not something hand-built like a wake-up. An existing
            // omer alarm still renders in the list with its own wheat styling.
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
