package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmanimclock.app.feature.calendar.model.CalendarDayMeta
import com.zmanimclock.app.feature.calendar.model.MonthGrid
import com.zmanimclock.app.ui.WomensAreaCleanDayMarker
import com.zmanimclock.app.ui.WomensAreaVesetMarker
import java.time.LocalDate

// Same fixed height as the main Calendar tab's grid, for the identical
// "always 6 rows" reasoning: a Hebrew month is 29 or 30 days and can start on
// any weekday, so reserving 6 rows stops the grid resizing under a swipe.
private val CELL_HEIGHT = 58.dp

/**
 * The swipeable month grid for the Women's Area — built on the REAL
 * [MonthGrid]/[CalendarDayMeta] from [com.zmanimclock.app.feature.calendar.model.MonthGridBuilder]
 * (same dates/Hebrew labels the main Calendar tab uses, not duplicated), with
 * this feature's own veset/clean-day markers layered on top per cell. The
 * main tab's own [com.zmanimclock.app.feature.calendar.presentation.MonthPager]
 * isn't reusable here — it internally renders its own private, holiday-styled
 * day cell with no seam to substitute different colouring — so this is a
 * parallel pager over the same shared grid data, not a parallel grid.
 */
@Composable
fun WomensAreaMonthPager(
    pagerState: PagerState,
    gridAt: (Int) -> MonthGrid,
    markersAt: (LocalDate) -> WomensAreaMarker?,
    today: LocalDate,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth().height(CELL_HEIGHT * 6),
    ) { page ->
        val grid = gridAt(page)
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            grid.weeks.forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { meta ->
                        WomensAreaDayCell(
                            meta = meta,
                            marker = markersAt(meta.date),
                            isToday = meta.date == today,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WomensAreaDayCell(
    meta: CalendarDayMeta,
    marker: WomensAreaMarker?,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val hasVeset = marker?.vesetKinds?.isNotEmpty() == true
    val cleanDayNumber = marker?.cleanDayNumber

    val background = when {
        cleanDayNumber != null -> WomensAreaCleanDayMarker.copy(alpha = 0.18f)
        hasVeset -> WomensAreaVesetMarker.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    val hebrewColor = when {
        !meta.isInDisplayedMonth -> cs.onSurfaceVariant.copy(alpha = 0.38f)
        else -> cs.onSurface
    }
    val gregorianColor = cs.onSurfaceVariant.copy(alpha = if (meta.isInDisplayedMonth) 1f else 0.38f)

    Box(
        modifier = modifier
            .heightIn(min = CELL_HEIGHT)
            .height(CELL_HEIGHT)
            .padding(1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .then(
                if (isToday) Modifier.border(2.dp, cs.primary, RoundedCornerShape(10.dp)) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Hebrew and Gregorian numerals stay in SEPARATE Text composables
            // — interpolated into one string, the bidi algorithm reorders
            // them inconsistently from month to month (same reasoning as the
            // main Calendar tab's own day cell).
            Text(
                text = meta.hebrewDayLabel,
                fontSize = 16.sp,
                lineHeight = 18.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                color = hebrewColor,
            )
            Text(text = meta.gregorianDayLabel, fontSize = 10.sp, lineHeight = 11.sp, color = gregorianColor)
        }
        if (cleanDayNumber != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(WomensAreaCleanDayMarker),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cleanDayNumber.toString(),
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        } else if (hasVeset) {
            // One dot per veset kind that lands on this day (up to 3) — a
            // shared colour for all three kinds for now; easy to give each
            // its own colour later if that turns out to read better.
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp),
            ) {
                repeat(marker?.vesetKinds?.size ?: 0) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 1.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(WomensAreaVesetMarker),
                    )
                }
            }
        }
    }
}
