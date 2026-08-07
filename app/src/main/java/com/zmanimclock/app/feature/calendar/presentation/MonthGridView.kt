package com.zmanimclock.app.feature.calendar.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmanimclock.app.feature.calendar.model.CalendarDayMeta
import com.zmanimclock.app.feature.calendar.model.MonthEvent
import com.zmanimclock.app.feature.calendar.model.MonthGrid
import com.zmanimclock.app.ui.theme.Ext
import java.time.LocalDate

/** ראשון … שבת. Declared Sunday-first; RTL puts ראשון on the right by itself. */
private val WEEKDAY_INITIALS = listOf("א", "ב", "ג", "ד", "ה", "ו", "ש")

// Three lines have to fit: Hebrew numeral, Gregorian numeral, and — in the
// Shabbat column — the parsha. At 52dp the parsha was silently clipped: the
// data was there, the pixels were not.
private val CELL_HEIGHT = 58.dp

/**
 * Header: Hebrew month and year large, Gregorian span small underneath.
 *
 * Arrow direction is chosen by the glyph, not by an auto-mirroring icon: in a
 * Hebrew calendar forward-in-time is leftward, and `Icons.AutoMirrored` under a
 * forced-RTL layout flips them back the wrong way.
 */
@Composable
fun MonthHeader(
    grid: MonthGrid,
    showTodayButton: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onTitleClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Declaration order matters and is the whole bug this fixes: under RTL
        // the FIRST child of a Row is placed on the RIGHT. Backward-in-time
        // belongs on the right (› pointing right), forward on the left (‹).
        StepArrow(forward = false, contentDescription = "החודש הקודם", onClick = onPrevious)
        Column(
            modifier = Modifier.weight(1f).clickable(onClick = onTitleClick),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${grid.hebrewMonthLabel}  ${grid.hebrewYearLabel}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
            )
            Text(
                text = grid.gregorianSpanLabel,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        StepArrow(forward = true, contentDescription = "החודש הבא", onClick = onNext)
        if (showTodayButton) {
            TextButton(onClick = onToday) { Text("היום") }
        }
    }
}

/**
 * A month/day step arrow.
 *
 * A bare chevron on a pale background does not read as a control — it looks
 * like decoration, and on a screen whose main interaction IS stepping through
 * dates that is the wrong thing to under-sell. Filled tonal circle, primary
 * glyph, full 48dp target.
 */
@Composable
fun StepArrow(
    forward: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(cs.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            // Chosen by the direction the glyph physically points: in a Hebrew
            // calendar forward-in-time is leftward. Icons.AutoMirrored would
            // flip these back under the app's forced RTL.
            imageVector = if (forward) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight,
            contentDescription = contentDescription,
            tint = cs.onPrimaryContainer,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
fun WeekdayRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        WEEKDAY_INITIALS.forEachIndexed { i, label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = if (i == 6) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (i == 6) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/**
 * The swipeable month grid.
 *
 * Height is fixed at six rows even for a five-row month: a Hebrew month is 29
 * or 30 days and can start on any weekday, so it always needs 5 or 6 rows, and
 * reserving the sixth stops the grid resizing under the user's finger.
 */
@Composable
fun MonthPager(
    pagerState: PagerState,
    gridAt: (Int) -> MonthGrid,
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth().height(CELL_HEIGHT * 6),
    ) { page ->
        MonthGridView(
            grid = gridAt(page),
            today = today,
            selected = selected,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun MonthGridView(
    grid: MonthGrid,
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        grid.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { meta ->
                    DayCell(
                        meta = meta,
                        isToday = meta.date == today,
                        isSelected = meta.date == selected,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(meta.date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    meta: CalendarDayMeta,
    isToday: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = Ext.colors

    // Shabbat tints the whole column; yom tov and chol hamoed fill the cell.
    val background = when {
        isSelected -> cs.primary
        meta.isYomTovAssurBemelacha -> cs.tertiaryContainer
        meta.isCholHamoed -> cs.tertiaryContainer.copy(alpha = 0.4f)
        meta.isShabbat -> cs.primaryContainer.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    val hebrewColor = when {
        isSelected -> cs.onPrimary
        !meta.isInDisplayedMonth -> cs.onSurfaceVariant.copy(alpha = 0.38f)
        meta.isRoshChodesh -> ext.accentGold
        else -> cs.onSurface
    }
    val gregorianColor =
        if (isSelected) cs.onPrimary.copy(alpha = 0.75f)
        else cs.onSurfaceVariant.copy(alpha = if (meta.isInDisplayedMonth) 1f else 0.38f)

    Box(
        modifier = modifier
            .heightIn(min = CELL_HEIGHT)
            .height(CELL_HEIGHT)
            .padding(1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(2.dp, cs.primary, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Hebrew and Latin numerals stay in SEPARATE Text composables.
            // Interpolated into one string, the bidi algorithm reorders them
            // inconsistently from month to month.
            // Explicit lineHeight on each line: the default leading is
            // generous enough that three stacked Texts overflow the cell and
            // the last one vanishes.
            Text(
                text = meta.hebrewDayLabel,
                fontSize = 16.sp,
                lineHeight = 18.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = hebrewColor,
            )
            Text(
                text = meta.gregorianDayLabel,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                color = gregorianColor,
            )
            meta.parshaName?.takeIf { meta.isShabbat && meta.isInDisplayedMonth }?.let {
                Text(
                    text = it,
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) cs.onPrimary.copy(alpha = 0.9f) else cs.primary,
                )
            }
        }
        // A fast is a condition that holds all day, so it reads as an
        // underline rather than a dot (which reads as "an event happens").
        if (meta.fast != null && meta.isInDisplayedMonth) {
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .height(3.dp)
                    .size(width = 18.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ext.deadline),
            )
        }
        // Modern Israeli holidays are marked softly — they are not assur
        // bemelacha and must not look like Yom Tov.
        if (meta.isModernHoliday && meta.isInDisplayedMonth && meta.fast == null) {
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(cs.primary),
            )
        }
    }
}

/**
 * The month's notable days as chips.
 *
 * Not a duplicate of the grid: the grid answers "what date is X", this answers
 * "what happens this month" — and it gives holiday names the legibility a
 * 52dp cell cannot.
 */
@Composable
fun MonthEventRibbon(
    events: List<MonthEvent>,
    onSelect: (LocalDate) -> Unit,
) {
    if (events.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val ext = Ext.colors
    // LazyRow rather than horizontalScroll: it lays items out RTL-first and
    // takes contentPadding, so the row starts inset from the screen edge
    // instead of a chip appearing sliced off by it.
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(events, key = { it.date.toString() }) { event ->
            val tint = when (event.kind) {
                MonthEvent.Kind.FAST -> ext.deadlineContainer
                MonthEvent.Kind.YOM_TOV -> cs.tertiaryContainer
                MonthEvent.Kind.CHOL_HAMOED -> cs.tertiaryContainer.copy(alpha = 0.5f)
                MonthEvent.Kind.CHANUKAH -> cs.tertiaryContainer
                MonthEvent.Kind.MODERN -> cs.primaryContainer
                MonthEvent.Kind.ROSH_CHODESH -> cs.surfaceVariant
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(tint)
                    .clickable { onSelect(event.date) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    color = cs.onSurface,
                )
                Text(
                    text = " · ${event.hebrewDayLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}
