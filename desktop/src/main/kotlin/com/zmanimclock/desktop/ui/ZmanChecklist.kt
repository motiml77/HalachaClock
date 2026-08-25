package com.zmanimclock.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.desktop.Ext
import java.awt.Cursor

/**
 * The zman multi-select, as a grouped CHECKLIST instead of a wall of chips.
 *
 * The chip grid put twenty-one halachic names side by side with nothing but
 * wrapping order to organise them — the owner's verdict was blunt and right:
 * "יש הרבה כפתורים... זה לא יפה". A checklist fixes both of its failures at
 * once: grouping by time of day gives the eye a structure that matches how
 * anyone thinks about a day, and a checkbox states on/off in a way a chip's
 * background tint never quite does.
 *
 * The groups are declared as an EXHAUSTIVE `when` over [ZmanKind], not a
 * hand-written list — so adding a kind to the enum without placing it here is
 * a compile error, not a silently missing row. (The Android widget picker
 * learned this the hard way: its hand-written list needed a test to keep it
 * complete.)
 *
 * Selection semantics are the caller's, unchanged from the chips: the set
 * holds `ZmanKind.name` strings, and an empty set means whatever it meant
 * before ("everything" for the headline filter, "nothing" for reminders).
 */
private enum class ZmanGroup(val title: String) {
    NIGHT_MORNING("לילה ובוקר"),
    PRAYER("זמני תפילה"),
    AFTERNOON("צהריים ואחר הצהריים"),
    EVENING("שקיעה וצאת הכוכבים"),
    SHABBAT("שבת וחג"),
}

private fun groupOf(kind: ZmanKind): ZmanGroup = when (kind) {
    ZmanKind.CHATZOT_LAYLA,
    ZmanKind.ALOT_HASHACHAR,
    ZmanKind.MISHEYAKIR,
    ZmanKind.HANETZ,
    ZmanKind.HANETZ_MISHOR,
    -> ZmanGroup.NIGHT_MORNING

    ZmanKind.SOF_ZMAN_SHMA_MGA_72_ZMANIYOT,
    ZmanKind.SOF_ZMAN_SHMA_MGA_16_1_DEG,
    ZmanKind.SOF_ZMAN_SHMA_GRA,
    ZmanKind.SOF_ZMAN_TFILA_MGA_72_ZMANIYOT,
    ZmanKind.SOF_ZMAN_TFILA_MGA_16_1_DEG,
    ZmanKind.SOF_ZMAN_TFILA_GRA,
    -> ZmanGroup.PRAYER

    ZmanKind.CHATZOT,
    ZmanKind.MINCHA_GEDOLA,
    ZmanKind.MINCHA_KETANA,
    ZmanKind.PLAG_HAMINCHA_GRA,
    ZmanKind.PLAG_HAMINCHA,
    -> ZmanGroup.AFTERNOON

    ZmanKind.SHKIA,
    ZmanKind.TZEIT_HAKOCHAVIM,
    ZmanKind.TZEIT_LECHUMRA,
    ZmanKind.TZEIT_RABBEINU_TAM,
    -> ZmanGroup.EVENING

    ZmanKind.CANDLE_LIGHTING,
    ZmanKind.TZEIT_SHABBAT,
    -> ZmanGroup.SHABBAT
}

/** Enum order inside each group — which is already chronological. */
private val GROUPED: List<Pair<ZmanGroup, List<ZmanKind>>> =
    ZmanGroup.entries.map { g -> g to ZmanKind.entries.filter { groupOf(it) == g } }

@Composable
fun ZmanChecklist(
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        GROUPED.forEachIndexed { i, (group, kinds) ->
            if (i > 0) {
                HorizontalDivider(
                    Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
            Text(
                group.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                // Gold, quietly: the group headers are the skeleton of the
                // list, and the accent marks structure rather than shouting
                // from every row.
                color = Ext.colors.accentGold,
                modifier = Modifier.padding(top = 2.dp, bottom = 1.dp),
            )
            kinds.forEach { kind ->
                ChecklistRow(
                    label = kind.hebrewName,
                    checked = kind.name in selected,
                ) {
                    val next = selected.toMutableSet()
                    if (!next.add(kind.name)) next.remove(kind.name)
                    onChange(next)
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ext = Ext.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    // THE WHOLE ROW is the button, not the 18dp box inside it. A checklist
    // whose only live pixels are the checkbox makes the user aim; this one
    // toggles from anywhere on the line, and the hover wash says so.
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    checked -> ext.accentGold.copy(alpha = if (hovered) 0.16f else 0.10f)
                    hovered -> cs.surfaceVariant.copy(alpha = 0.7f)
                    else -> Color.Transparent
                },
            )
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .clickable(onClick = onToggle)
            .padding(start = 2.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            // null: the ROW handles the click. A second handler here would
            // double-toggle when the pointer lands exactly on the box.
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = ext.accentGold,
                checkmarkColor = Color(0xFF12203A),
                uncheckedColor = cs.outline,
            ),
            modifier = Modifier.padding(vertical = 3.dp).size(26.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurface,
            fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
