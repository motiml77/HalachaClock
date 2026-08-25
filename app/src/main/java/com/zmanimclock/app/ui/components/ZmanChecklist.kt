package com.zmanimclock.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zmanimclock.app.feature.zmanim.model.ZmanKind

private val Gold = Color(0xFFF5C518)
private val Navy = Color(0xFF123A8B)

/**
 * The brand accent for a checked box: gold fill, navy checkmark — the same
 * pair the widget itself is drawn in. Used by every zman checklist so the
 * screens read as one app.
 */
@Composable
fun zmanChecklistCheckboxColors(): CheckboxColors = CheckboxDefaults.colors(
    checkedColor = Gold,
    checkmarkColor = Navy,
)

/**
 * A grouped checklist of zmanim — the replacement for the old walls of
 * FilterChips.
 *
 * Chips put all ~19 halachic names in one undifferentiated cloud; several
 * differ only by a shita suffix, so the user hunted rather than picked. Here
 * each group from the caller's bucketing gets a distinct header, and each
 * zman is one full-width row: checkbox (leading — the right edge under this
 * app's forced RTL), the Hebrew name, and an optional subdued trailing value
 * (the widget picker passes its illustrative time).
 *
 * The whole row is the tap target, minimum 44dp tall. Rows the caller
 * disables (the widget's MAX_ZMANIM cap) keep rendering but dim and stop
 * responding — the same signal the old UI gave, on a bigger target.
 */
@Composable
fun ZmanGroupedChecklist(
    groups: List<Pair<String, List<ZmanKind>>>,
    isChecked: (ZmanKind) -> Boolean,
    onToggle: (ZmanKind) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (ZmanKind) -> Boolean = { true },
    trailing: ((ZmanKind) -> String?)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        groups.forEachIndexed { index, (title, kinds) ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    top = if (index == 0) 4.dp else 10.dp,
                    bottom = 2.dp,
                    start = 12.dp,
                    end = 12.dp,
                ),
            )
            kinds.forEach { kind ->
                ZmanChecklistRow(
                    label = kind.hebrewName,
                    checked = isChecked(kind),
                    enabled = enabled(kind),
                    onToggle = { onToggle(kind) },
                    trailingText = trailing?.invoke(kind),
                )
            }
        }
    }
}

/**
 * One checklist row. Public so screens can add rows that are not a ZmanKind —
 * the settings filter's master "הכל" row sits above the groups but must look
 * identical to them.
 */
@Composable
fun ZmanChecklistRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingText: String? = null,
    bold: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            colors = zmanChecklistCheckboxColors(),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        if (trailingText != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
