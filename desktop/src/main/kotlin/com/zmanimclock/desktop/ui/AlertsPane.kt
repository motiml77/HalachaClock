package com.zmanimclock.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmanimclock.app.feature.zmanim.format.asZmanTime
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.desktop.Ext
import com.zmanimclock.desktop.ZmanNumberFamily
import com.zmanimclock.desktop.data.DesktopZmanimService
import com.zmanimclock.desktop.data.ZmanAlert
import java.awt.Cursor
import java.time.Duration
import java.time.LocalDate

/**
 * התראות — the desktop's alarm list, built entirely out of halachic zmanim.
 *
 * Every row is a [ZmanAlert]: a name the user chose, a zman, and an offset in
 * minutes before or after it. There is deliberately no wall-clock entry
 * anywhere in this screen — see [ZmanAlert] for why that absence is the point
 * rather than a missing feature.
 *
 * ORDERED BY WHEN THEY WILL ACTUALLY FIRE, not by when they were created and
 * not alphabetically. The list is a picture of the user's day in the order the
 * day happens, so "what is next" is the top of the list rather than something
 * to be worked out row by row. Alerts whose zman does not exist today —
 * הדלקת נרות on a Tuesday, the visible netz with no terrain data — sort to the
 * end and say so, because silently hiding them would look like data loss.
 */
@Composable
fun AlertsPane(
    service: DesktopZmanimService,
    /** Non-null when arriving from a bell in the zmanim list. */
    prefillKind: ZmanKind?,
    onPrefillConsumed: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val zone = service.zone
    val today = remember(zone) { LocalDate.now(zone) }

    var editing by remember { mutableStateOf<ZmanAlert?>(null) }
    var isNew by remember { mutableStateOf(false) }

    // A bell press on another tab lands here. Consumed immediately so that
    // returning to this tab later does not reopen the editor.
    LaunchedEffect(prefillKind) {
        prefillKind?.let { kind ->
            editing = ZmanAlert(
                id = ZmanAlert.newId(service.prefs.alerts),
                name = kind.shortName,
                kind = kind,
            )
            isNew = true
            onPrefillConsumed()
        }
    }

    val rows = remember(service.prefs.alerts, today, service.prefs.cityId) {
        service.alertsInFiringOrder(today)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "התראות",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = cs.onBackground,
                    modifier = Modifier.weight(1f),
                )
                RoundIconButton(
                    icon = Icons.Filled.Add,
                    description = "הוסף התראה",
                    accent = true,
                ) {
                    editing = ZmanAlert(
                        id = ZmanAlert.newId(service.prefs.alerts),
                        name = "",
                        kind = ZmanKind.SHKIA,
                    )
                    isNew = true
                }
            }

            Surface(
                Modifier.fillMaxSize().padding(bottom = 10.dp),
                shape = RoundedCornerShape(16.dp),
                color = cs.surface,
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
            ) {
                if (rows.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(vertical = 6.dp)) {
                        items(rows, key = { it.alert.id }) { row ->
                            AlertRow(
                                row = row,
                                onToggle = {
                                    service.putAlert(row.alert.copy(enabled = !row.alert.enabled))
                                },
                                onDelete = { service.removeAlert(row.alert.id) },
                                onEdit = { editing = row.alert; isNew = false },
                            )
                        }
                    }
                }
            }
        }

        editing?.let { draft ->
            AlertEditor(
                draft = draft,
                isNew = isNew,
                service = service,
                onCancel = { editing = null },
                onSave = { service.putAlert(it); editing = null },
            )
        }
    }
}

@Composable
private fun EmptyState() {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.NotificationsNone,
            contentDescription = null,
            tint = cs.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "אין עדיין התראות",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "אפשר להוסיף כאן ב־+, או ללחוץ על הפעמון שליד כל זמן ברשימת הזמנים.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- one row --

@Composable
private fun AlertRow(
    row: AlertRowData,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = Ext.colors

    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onEdit)
            .padding(horizontal = 8.dp, vertical = 7.dp)
            // A DISABLED ALERT FADES rather than disappearing or greying to a
            // different colour: it is still the user's alert, still in its
            // place in the day, just not armed. Transparency says "off" while
            // keeping every word of it readable, which a grey wash does not.
            .alpha(if (row.alert.enabled) 1f else 0.42f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // First child, so under RTL it lands on the RIGHT — the reading edge.
        Box(Modifier.width(46.dp), contentAlignment = Alignment.Center) {
            if (row.firesAt != null) {
                Text(
                    row.firesAt,
                    fontFamily = ZmanNumberFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (row.alert.enabled) ext.accentGold else cs.onSurfaceVariant,
                )
            } else {
                Text(
                    "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(Modifier.weight(1f)) {
            Text(
                row.alert.name.ifBlank { row.alert.kind.shortName },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
                maxLines = 1,
            )
            Text(
                buildString {
                    append(row.alert.description)
                    // Only when it NARROWS things. "כל יום" on every row is
                    // noise that makes the rows that do differ harder to spot.
                    row.alert.daysDescription?.let { append(" · ").append(it) }
                    if (row.firesAt == null) append(" · לא מתקיים היום")
                },
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
            )
        }

        // The bell IS the switch. A Material Switch beside a bell icon would be
        // two controls saying one thing; a bell that is struck through when off
        // says it once, in the same vocabulary the zmanim list uses to offer
        // the alert in the first place.
        RoundIconButton(
            icon = if (row.alert.enabled) Icons.Filled.Notifications
            else Icons.Outlined.NotificationsOff,
            description = if (row.alert.enabled) "כבה התראה" else "הפעל התראה",
            tint = if (row.alert.enabled) ext.accentGold else cs.onSurfaceVariant,
            onClick = onToggle,
        )
        RoundIconButton(
            icon = Icons.Outlined.DeleteOutline,
            description = "מחק התראה",
            danger = true,
            onClick = onDelete,
        )
    }
}

// ----------------------------------------------------------- the editor ----

/**
 * Create/edit, as a sheet over the list rather than a separate window.
 *
 * A second OS window for a three-field form would inherit its own placement,
 * its own always-on-top question and its own focus handling, all for something
 * that belongs visually inside the panel it was launched from. The scrim also
 * does the work of saying "finish this first" without disabling anything.
 */
@Composable
private fun AlertEditor(
    draft: ZmanAlert,
    isNew: Boolean,
    service: DesktopZmanimService,
    onCancel: () -> Unit,
    onSave: (ZmanAlert) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val zone = service.zone
    val today = remember(zone) { LocalDate.now(zone) }

    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var kind by remember(draft.id) { mutableStateOf(draft.kind) }
    var offset by remember(draft.id) { mutableStateOf(draft.offsetMinutes) }
    var days by remember(draft.id) { mutableStateOf(draft.days) }

    val zmanim = remember(today, service.prefs.cityId) { service.zmanimForPicker(today) }
    val zmanAt = zmanim.firstOrNull { it.first == kind }?.second

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            // Swallows clicks so the list behind cannot be operated through
            // the scrim; clicking it also cancels, as a sheet should.
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                // Clicks inside must not reach the scrim's cancel.
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(16.dp),
            color = cs.surface,
            border = BorderStroke(1.dp, cs.outlineVariant),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    if (isNew) "התראה חדשה" else "עריכת התראה",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = cs.primary,
                )

                Spacer(Modifier.height(10.dp))
                FieldLabel("שם ההתראה")
                NameField(name) { name = it }

                Spacer(Modifier.height(10.dp))
                FieldLabel("הזמן שעליו מבוססת ההתראה")
                ZmanPicker(
                    zmanim = zmanim,
                    selected = kind,
                    zone = zone,
                    onSelect = { kind = it },
                )

                Spacer(Modifier.height(10.dp))
                FieldLabel("מתי")
                OffsetPicker(offset) { offset = it }

                Spacer(Modifier.height(10.dp))
                FieldLabel("באילו ימים")
                DayPicker(days) { days = it }

                Spacer(Modifier.height(8.dp))
                // The whole point of the form, resolved: the exact clock time
                // this alert would fire TODAY. Everything above is a rule; this
                // is the rule applied, and it updates as the fields change.
                Text(
                    zmanAt?.plus(Duration.ofMinutes(offset.toLong()))?.let { at ->
                        val fireDay = at.atZone(zone).dayOfWeek
                        if (days and ZmanAlert.bitFor(fireDay) != 0) "היום: " + at.asZmanTime(zone)
                        else "לא היום — " + at.asZmanTime(zone) + " בימים שנבחרו"
                    } ?: "הזמן הזה אינו מתקיים היום",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ext.colors.accentGold,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("שמירה", accent = true) {
                        onSave(
                            draft.copy(
                                name = name.trim().ifBlank { kind.shortName },
                                kind = kind,
                                offsetMinutes = offset,
                                days = days,
                            ),
                        )
                    }
                    PillButton("ביטול", accent = false, onClick = onCancel)
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                "לדוגמה: לצאת לתפילה",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= 40) onChange(it.replace("\n", "")) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
            cursorBrush = SolidColor(cs.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Today's zmanim, as chips. The list a person actually recognises. */
@Composable
private fun ZmanPicker(
    zmanim: List<Pair<ZmanKind, java.time.Instant>>,
    selected: ZmanKind,
    zone: java.time.ZoneId,
    onSelect: (ZmanKind) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    // Scrolled to whatever is already chosen. Opening the editor from the
    // bell beside שקיעה and finding the picker parked on עלות השחר makes the
    // prefill look like it did not happen — the user then hunts for a row the
    // app had already selected for them.
    val listState = rememberLazyListState()
    LaunchedEffect(selected, zmanim) {
        val index = zmanim.indexOfFirst { it.first == selected }
        if (index >= 0) listState.scrollToItem(index)
    }

    Surface(
        Modifier.fillMaxWidth().height(132.dp),
        shape = RoundedCornerShape(10.dp),
        color = cs.surfaceVariant.copy(alpha = 0.4f),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
            state = listState,
        ) {
            items(zmanim, key = { it.first.name }) { (k, at) ->
                val isSelected = k == selected
                Row(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) cs.primaryContainer else Color.Transparent)
                        .clickable { onSelect(k) }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        k.hebrewName,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) cs.onPrimaryContainer else cs.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        at.asZmanTime(zone),
                        fontFamily = ZmanNumberFamily,
                        fontSize = 12.sp,
                        color = if (isSelected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Before / exactly / after, and by how much.
 *
 * A signed number is the model, but "-20" is not how anyone thinks about this,
 * so the control is a direction and a magnitude and the sign is assembled from
 * the two. Zero collapses both to "בדיוק בזמן", which is a real and common
 * choice rather than a degenerate case of "0 minutes before".
 */
@Composable
private fun OffsetPicker(offset: Int, onChange: (Int) -> Unit) {
    val magnitude = kotlin.math.abs(offset)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoiceChip("לפני", offset < 0) { onChange(-(magnitude.takeIf { it > 0 } ?: 10)) }
            ChoiceChip("בדיוק בזמן", offset == 0) { onChange(0) }
            ChoiceChip("אחרי", offset > 0) { onChange(magnitude.takeIf { it > 0 } ?: 10) }
        }
        if (offset != 0) {
            val sign = if (offset < 0) -1 else 1
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 10, 15, 20, 30, 45, 60).forEach { m ->
                    ChoiceChip("$m", magnitude == m) { onChange(sign * m) }
                }
            }
        }
    }
}

/**
 * The seven days, as toggles, plus a "כל יום" shortcut.
 *
 * THE LAST DAY CANNOT BE CLEARED. The Android build shipped a bug where an
 * empty day mask was read as "unset" and therefore rang every day — the exact
 * opposite of what the user had just asked for, and silent. Here the model
 * says an empty mask fires never, and this control refuses to produce one:
 * unticking the last remaining day does nothing. An alert that fires on no
 * days is not a state anyone means to be in; "off" is what the bell is for.
 */
@Composable
private fun DayPicker(days: Int, onChange: (Int) -> Unit) {
    val labels = listOf("א", "ב", "ג", "ד", "ה", "ו", "ש")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            // Declared Sunday-first; RTL puts א on the right by itself.
            labels.forEachIndexed { i, label ->
                val bit = 1 shl i
                val on = days and bit != 0
                DayChip(label, on) {
                    val next = days xor bit
                    if (next != 0) onChange(next)
                }
            }
        }
        if (days != ZmanAlert.EVERY_DAY) {
            ChoiceChip("כל יום", false) { onChange(ZmanAlert.EVERY_DAY) }
        }
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(28.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) cs.primaryContainer else cs.surfaceVariant.copy(alpha = 0.55f))
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) cs.primaryContainer else cs.surfaceVariant.copy(alpha = 0.55f))
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
    )
}

@Composable
private fun PillButton(label: String, accent: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ext = Ext.colors
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (accent) ext.accentGold else cs.surfaceVariant)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (accent) ext.onAccentGold else cs.onSurface,
    )
}

/**
 * The app's one icon button: a circle that fills on hover.
 *
 * Shared rather than repeated so every icon control in the app — add, toggle,
 * delete, the bells in the zmanim list — has the same size, the same hit area
 * and the same hover feedback. Three near-identical buttons drawn three ways
 * is how a screen starts to look assembled rather than designed.
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String,
    accent: Boolean = false,
    danger: Boolean = false,
    tint: Color? = null,
    size: androidx.compose.ui.unit.Dp = 28.dp,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = Ext.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val background = when {
        accent -> if (hovered) ext.accentGold else ext.accentGold.copy(alpha = 0.85f)
        hovered && danger -> ext.deadline.copy(alpha = 0.22f)
        hovered -> cs.surfaceVariant
        else -> Color.Transparent
    }
    val foreground = when {
        accent -> ext.onAccentGold
        danger -> if (hovered) ext.deadline else cs.onSurfaceVariant
        else -> tint ?: cs.onSurfaceVariant
    }

    Box(
        Modifier.size(size)
            .clip(RoundedCornerShape(50))
            .background(background)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = foreground,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

/** One alert plus the clock time it resolves to today, or null if it has none. */
data class AlertRowData(val alert: ZmanAlert, val firesAt: String?)
