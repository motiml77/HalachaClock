package com.zmanimclock.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmanimclock.desktop.Ext
import com.zmanimclock.desktop.ZmanNumberFamily
import com.zmanimclock.desktop.data.DayView
import com.zmanimclock.desktop.data.DesktopZmanimService
import com.zmanimclock.desktop.data.ZmanRow
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Today's zmanim — the app's front page, and the sibling of the Android home screen. */
@Composable
fun ZmanimPane(service: DesktopZmanimService) {
    var now by remember0 { mutableStateOf(Instant.now()) }

    // Ticks on the wall clock rather than on a fixed delay, so the countdown
    // lands on the second boundary and self-corrects after the machine sleeps
    // instead of drifting further out the longer the app stays open.
    LaunchedEffect(Unit) {
        while (true) {
            val n = Instant.now()
            now = n
            delay(1_000L - (n.toEpochMilli() % 1_000L))
        }
    }

    val view = service.view(LocalDate.now(service.zone), now)

    Column(Modifier.fillMaxSize()) {
        DayHero(view, service, now)
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            items(view.rows, key = { it.kind.name }) { ZmanListRow(it) }
        }
    }
}

@Composable
internal fun DayHero(view: DayView, service: DesktopZmanimService, now: Instant) {
    val ext = Ext.colors
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(ext.heroTop, ext.heroBottom)))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    view.hebrewDate,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ext.heroText,
                )
                Text(
                    "${view.gregorianDate} · ${view.weekdayName} · ${view.cityName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.heroLabel,
                )
            }
        }

        if (view.headlineLabel != null && view.headlineTime != null) {
            // Sized to its contents, NOT to the window.
            //
            // This card used to take weight(1f) and stretch the whole width, so
            // a label and a time — five words between them — sat inside a box
            // most of which was empty, and on any day that is not today, where
            // there is no countdown, the entire left half was blank. A card
            // that hugs what it holds says the same thing without the hole.
            Row(
                // Pushed to the far side from the date above it. Under RTL the
                // date starts at the right, so the card sits left and the two
                // blocks bracket the hero instead of stacking against one edge.
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(ext.heroInner)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column {
                    Text(
                        view.headlineLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.heroLabel,
                    )
                    Text(
                        view.headlineTime,
                        fontFamily = ZmanNumberFamily,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = ext.heroText,
                    )
                }
                if (view.isToday) {
                    service.nextZman(now)?.let { (_, instant) ->
                        Text(
                            "בעוד ${countdown(now, instant)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = ext.accentGold,
                        )
                    }
                }
            }
            }
        }

        if (view.notes.isNotEmpty()) {
            Text(
                view.notes.joinToString("  ·  "),
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = ext.accentGold,
            )
        }
    }
}

/**
 * How wide the name+time pair is allowed to grow.
 *
 * The pair used to stretch to the full window: the name took `weight(1f)` and
 * shoved the time against the opposite edge, so on a desktop-width window the
 * two ends of a single row sat hundreds of pixels apart with nothing between
 * them. A zman and its time have to be readable as ONE line — the eye should
 * not have to travel to pair them up — and that dead space was also the only
 * reason the window had to be as wide as it was.
 *
 * Capping the pair rather than the row keeps the highlight and the divider
 * spanning the full width, which is what makes the list read as a list.
 */
private val ROW_CONTENT_MAX = 260.dp

@Composable
internal fun ZmanListRow(row: ZmanRow, compact: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val ext = Ext.colors
    Column {
        Row(
            Modifier.fillMaxWidth()
                .background(if (row.isNext) ext.nextRow else cs.surface)
                .padding(horizontal = 14.dp, vertical = if (compact) 2.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            // Centred rather than pushed to the reading edge: whatever width
            // the user drags the window to, the leftover space splits evenly
            // instead of piling up on one side as a single dead margin.
            horizontalArrangement = Arrangement.Center,
        ) {
            Row(
                Modifier.widthIn(max = ROW_CONTENT_MAX),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.name,
                    Modifier.weight(1f),
                    style = if (compact) MaterialTheme.typography.bodySmall
                    else MaterialTheme.typography.bodyMedium,
                    color = if (row.isPast) cs.onSurfaceVariant else cs.onSurface,
                )
                Text(
                    row.time,
                    fontFamily = ZmanNumberFamily,
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = if (row.isNext) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.End,
                    color = if (row.isPast) cs.onSurfaceVariant else cs.onSurface,
                    modifier = Modifier.width(if (compact) 42.dp else 50.dp),
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = cs.outlineVariant)
    }
}

/** "1:06:32" / "6:32" — hours only when there are any. */
internal fun countdown(now: Instant, target: Instant): String {
    val d = Duration.between(now, target)
    if (d.isNegative || d.isZero) return "0:00"
    val h = d.toHours()
    val m = d.toMinutes() % 60
    val s = d.seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Alias so the import list stays honest about what Compose's remember is. */
@Composable
private inline fun <T> remember0(crossinline calculation: () -> T): T =
    androidx.compose.runtime.remember { calculation() }
