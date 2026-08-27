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
import androidx.compose.material.icons.filled.NoFood
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
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
    val tzeitGuard by viewModel.tzeitGuard.collectAsStateWithLifecycle()
    var guardDialogFor by remember { mutableStateOf<ZmanimViewModel.ZmanRow?>(null) }

    // Recompute the moment the screen comes back into view. The ViewModel
    // survives backgrounding and its minute ticker cannot run while the
    // process is frozen, so without this the user returns to whatever was on
    // screen when they left — which is exactly the "stuck on an old zman"
    // report. Also covers the device having slept across a zman boundary.
    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResumed()
        onPauseOrDispose { }
    }

    ZmanimContent(
        state = state,
        alertedKinds = alertedKinds,
        guardArmed = tzeitGuard != null,
        onBellClick = { kind -> onCreateZmanAlarm(kind.name) },
        onGuardClick = { row -> guardDialogFor = row },
    )

    guardDialogFor?.let { row ->
        TzeitGuardDialog(
            zmanTime = row.time,
            armedAt = tzeitGuard?.let { "%02d:%02d".format(it.hour, it.minute) },
            onArm = { h, m -> viewModel.armTzeitGuard(h, m); guardDialogFor = null },
            onCancelGuard = { viewModel.cancelTzeitGuard(); guardDialogFor = null },
            onDismiss = { guardDialogFor = null },
        )
    }
}

@Composable
fun ZmanimContent(
    state: ZmanimViewModel.UiState,
    alertedKinds: Set<String>,
    guardArmed: Boolean = false,
    onBellClick: (ZmanKind) -> Unit,
    onGuardClick: (ZmanimViewModel.ZmanRow) -> Unit = {},
) {
    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NextHero(state)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.rows, key = { it.kind.name }) { row ->
                ZmanRow(
                    row = row,
                    hasAlert = row.kind.name in alertedKinds,
                    // The badge belongs to ONE row: the stricter tzeit, which
                    // is the one a maariv safeguard hangs off.
                    showGuardBadge = row.kind == ZmanKind.TZEIT_LECHUMRA,
                    guardArmed = guardArmed,
                    onBellClick = { onBellClick(row.kind) },
                    onGuardClick = { onGuardClick(row) },
                )
            }
        }
        state.fastBanner?.let { FastBannerCard(it) }
    }
}

/** Fast-day notice pinned under the list — entry/exit times of the fast. */
@Composable
private fun FastBannerCard(banner: ZmanimViewModel.FastBanner) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(cs.tertiaryContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.NoFood,
            contentDescription = null,
            tint = cs.onTertiaryContainer,
            modifier = Modifier.size(26.dp),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = banner.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onTertiaryContainer,
            )
            Text(
                text = banner.line,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onTertiaryContainer,
            )
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
/** §6.1 — one zman row with next / reminder-active / past variants. */
@Composable
private fun ZmanRow(
    row: ZmanimViewModel.ZmanRow,
    hasAlert: Boolean,
    showGuardBadge: Boolean = false,
    guardArmed: Boolean = false,
    onBellClick: () -> Unit,
    onGuardClick: () -> Unit = {},
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
                    // Ringing bell when an alert is armed; outline invites a tap
                    imageVector = when {
                        hasAlert -> Icons.Filled.NotificationsActive
                        row.isNext -> Icons.Filled.Notifications
                        else -> Icons.Outlined.Notifications
                    },
                    contentDescription = if (hasAlert) "התראה פעילה" else "הוסף התראה",
                    tint = bellTint,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (showGuardBadge) {
                // The name text hugs its own box's start edge (right, in RTL),
                // so a single weight(1f) box — as every other row uses — piles
                // all the slack on the far side of the text, right next to the
                // badge, and leaves the badge glued to the time instead. Here
                // the name is measured at its own width and the slack is
                // pulled out into two equal spacers, so the badge sits in the
                // middle of the gap rather than at whichever end absorbed it.
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (row.isNext) FontWeight.Bold else FontWeight.SemiBold,
                    color = nameColor,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                TzeitGuardBadge(armed = guardArmed, onClick = onGuardClick)
                Spacer(Modifier.weight(1f))
            } else {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (row.isNext) FontWeight.Bold else FontWeight.SemiBold,
                    color = nameColor,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                )
            }
            Text(
                text = row.time,
                style = ZmanListTimeStyle,
                color = timeColor,
            )
        }
        HorizontalDivider(thickness = 2.dp, color = cs.outlineVariant)
    }
}
