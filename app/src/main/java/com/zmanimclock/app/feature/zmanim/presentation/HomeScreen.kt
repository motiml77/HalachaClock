package com.zmanimclock.app.feature.zmanim.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.ui.theme.Ext
import com.zmanimclock.app.ui.theme.ZmanListTimeStyle

/**
 * מסך הזמנים — style 1D (Claude Design §7.1): NextHero header + flat
 * ZmanRow list. Stateless content; per-row bell arms a daily zman alarm.
 */
@Composable
fun HomeScreen(
    onCreateZmanAlarm: (String) -> Unit,
    viewModel: ZmanimViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val alertedKinds by viewModel.alertedKinds.collectAsStateWithLifecycle()

    ZmanimContent(
        state = state,
        alertedKinds = alertedKinds,
        onBellClick = { kind -> onCreateZmanAlarm(kind.name) },
    )
}

@Composable
fun ZmanimContent(
    state: ZmanimViewModel.UiState,
    alertedKinds: Set<String>,
    onBellClick: (ZmanKind) -> Unit,
) {
    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NextHero(state)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.rows, key = { it.kind.name }) { row ->
                ZmanRow(
                    row = row,
                    hasAlert = row.kind.name in alertedKinds,
                    onBellClick = { onBellClick(row.kind) },
                )
            }
        }
    }
}

/** §6.2 — the primary hero header: date + sunrise tag + next-zman block. */
@Composable
private fun NextHero(state: ZmanimViewModel.UiState) {
    val ext = Ext.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(ext.heroTop, ext.heroBottom)))
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SunriseTag(visible = state.basedOnVisibleSunrise)
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = state.hebrewDate,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                )
                Text(
                    text = "${state.gregorianDate} · ${state.locationName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }

        if (state.nextName != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .background(ext.heroInner, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "הזמן הבא — ${state.nextName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = ext.heroLabel,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = state.nextTime.orEmpty(),
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White,
                    )
                    state.countdown?.let {
                        Text(
                            text = "בעוד $it",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ext.accentGold,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** §6.3 — sunrise-source tag: gold pill (visible) / muted outline (mishor). */
@Composable
private fun SunriseTag(visible: Boolean) {
    val ext = Ext.colors
    if (visible) {
        Row(
            modifier = Modifier
                .background(ext.accentGold, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.WbSunny,
                contentDescription = null,
                tint = ext.onAccentGold,
                modifier = Modifier.size(15.dp),
            )
            Text(
                "הנץ הנראה",
                style = MaterialTheme.typography.labelMedium,
                color = ext.onAccentGold,
            )
        }
    } else {
        Text(
            "מישור",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/** §6.1 — one zman row with next / reminder-active / past variants. */
@Composable
private fun ZmanRow(
    row: ZmanimViewModel.ZmanRow,
    hasAlert: Boolean,
    onBellClick: () -> Unit,
) {
    val ext = Ext.colors
    val cs = MaterialTheme.colorScheme

    val rowBg = when {
        row.isNext -> ext.nextRow
        hasAlert -> ext.reminderTint
        else -> Color.Transparent
    }
    val nameColor = when {
        row.isNext -> cs.primary
        row.isPast -> cs.onSurfaceVariant.copy(alpha = 0.6f)
        else -> cs.onSurface
    }
    val timeColor = when {
        row.isPast -> cs.onSurfaceVariant.copy(alpha = 0.6f)
        else -> cs.primary
    }
    val bellTint = when {
        hasAlert -> ext.accentGold
        row.isNext -> cs.primary
        else -> cs.onSurfaceVariant
    }

    Column(modifier = Modifier.background(rowBg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBellClick) {
                Icon(
                    imageVector = if (hasAlert || row.isNext) {
                        Icons.Filled.Notifications
                    } else {
                        Icons.Outlined.Notifications
                    },
                    contentDescription = if (hasAlert) "התראה פעילה" else "הוסף התראה",
                    tint = bellTint,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (row.isNext) FontWeight.Bold else FontWeight.SemiBold,
                color = nameColor,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
            Text(
                text = row.time,
                style = ZmanListTimeStyle,
                color = timeColor,
            )
        }
        HorizontalDivider(thickness = 2.dp, color = cs.outlineVariant)
    }
}
