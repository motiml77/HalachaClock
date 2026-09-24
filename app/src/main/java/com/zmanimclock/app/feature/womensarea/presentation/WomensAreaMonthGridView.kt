package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmanimclock.app.feature.calendar.model.CalendarDayMeta
import com.zmanimclock.app.feature.calendar.model.MonthGrid
import com.zmanimclock.app.feature.womensarea.model.PrishaDay
import com.zmanimclock.app.feature.womensarea.model.VesetKind
import com.zmanimclock.app.feature.womensarea.model.WomensAreaLabels.hebrewName
import com.zmanimclock.app.feature.womensarea.model.WomensAreaMarker
import com.zmanimclock.app.ui.WomensAreaCleanGreen
import com.zmanimclock.app.ui.WomensAreaCountBlue
import com.zmanimclock.app.ui.WomensAreaPrishaRed
import com.zmanimclock.app.ui.WomensAreaTevilaBlue
import com.zmanimclock.app.ui.WomensAreaVesetMarker
import java.time.LocalDate

// Taller than the Calendar tab's 58dp: a cell here carries the day count, the
// Hebrew and civil numerals, AND a separation day's name plus its onah.
// Still a fixed 6 rows, for the same reason as the Calendar tab: a Hebrew
// month is 29 or 30 days on any weekday, and a constant height keeps the grid
// from resizing under a swipe.
private val CELL_HEIGHT = 80.dp

/**
 * The swipeable Hebrew-month grid for the Women's Area, over the REAL
 * [MonthGrid] from MonthGridBuilder — one page per Hebrew month, one cell per
 * Hebrew day, so counting cells IS counting Hebrew dates.
 *
 * Only the displayed month's own days are drawn: the neighbouring months'
 * filler cells stay blank, so no day appears twice across two pages and the
 * count reads straight off the grid.
 */
@Composable
fun WomensAreaMonthPager(
    pagerState: PagerState,
    gridAt: (Int) -> MonthGrid,
    markersAt: (LocalDate) -> WomensAreaMarker?,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
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
                        if (!meta.isInDisplayedMonth) {
                            Spacer(Modifier.weight(1f).height(CELL_HEIGHT))
                        } else {
                            WomensAreaDayCell(
                                meta = meta,
                                marker = markersAt(meta.date),
                                isToday = meta.date == today,
                                onClick = { onDayClick(meta.date) },
                                modifier = Modifier.weight(1f),
                            )
                        }
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val prisha = marker?.prisha.orEmpty()
    val isPrisha = prisha.isNotEmpty()
    val isVesetDay = marker?.isVesetDay == true
    val isHefsek = marker?.isHefsekDay == true
    // The 7th clean day: the tevila is after ITS tzeit only.
    val isTevila = marker?.isTevilaDay == true
    val cleanDayNumber = marker?.cleanDayNumber

    val background = when {
        isPrisha -> WomensAreaPrishaRed.copy(alpha = 0.10f)
        isVesetDay -> WomensAreaVesetMarker.copy(alpha = 0.28f)
        isTevila -> WomensAreaTevilaBlue.copy(alpha = 0.14f)
        cleanDayNumber != null -> WomensAreaCleanGreen.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    // One frame per cell, in priority order: a separation day's red frame
    // outranks everything; then the clean days' green (the 7th, the tevila
    // day, keeps its green frame and gets a star), and last today's.
    val frame: Color? = when {
        isPrisha -> WomensAreaPrishaRed
        cleanDayNumber != null -> WomensAreaCleanGreen
        isToday -> cs.primary
        else -> null
    }

    Box(
        modifier = modifier
            .height(CELL_HEIGHT)
            .padding(1.dp)
            .clip(shape)
            .background(background)
            .then(frame?.let { Modifier.border(2.dp, it, shape) } ?: Modifier)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top line: the count from the veset (start side), the clean-day
            // number (end side). Fixed height so the numerals never shift.
            Row(Modifier.fillMaxWidth().height(15.dp), verticalAlignment = Alignment.CenterVertically) {
                marker?.countDayNumber?.let { CountChip(it, WomensAreaCountBlue, RoundedCornerShape(5.dp)) }
                Spacer(Modifier.weight(1f))
                if (isTevila) {
                    Text("★", fontSize = 12.sp, lineHeight = 13.sp, color = WomensAreaTevilaBlue)
                }
                cleanDayNumber?.let { CountChip(it, WomensAreaCleanGreen, CircleShape) }
            }
            // Hebrew and Gregorian numerals in SEPARATE Text composables —
            // interpolated into one string, bidi reorders them inconsistently
            // (same reasoning as the Calendar tab's own day cell).
            Text(
                text = meta.hebrewDayLabel,
                fontSize = 16.sp,
                lineHeight = 18.sp,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isToday) cs.primary else cs.onSurface,
            )
            Text(text = meta.gregorianDayLabel, fontSize = 9.sp, lineHeight = 10.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            when {
                isPrisha -> PrishaLabel(prisha)
                isVesetDay -> CellCaption(title = "ראייה", onah = marker?.vesetOnah?.hebrewName, color = cs.onSurface)
                isTevila -> CellCaption(title = "טבילה", onah = "אחר צאה״כ בלבד", color = WomensAreaTevilaBlue)
                isHefsek -> CellCaption(title = "הפסק טהרה", onah = null, color = WomensAreaCleanGreen)
            }
        }
    }
}

@Composable
private fun CountChip(number: Int, color: Color, shape: Shape) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 15.dp, minHeight = 15.dp)
            .clip(shape)
            .background(color)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/**
 * The separation day's name and its onah. A single kind gets its full name
 * (wrapping to two lines if needed); two kinds on one day — e.g. day 30 that
 * is also the same date next month — get short forms side by side. The card
 * under the calendar spells every one out in full.
 */
@Composable
private fun PrishaLabel(prisha: List<PrishaDay>) {
    val title = if (prisha.size == 1) {
        prisha.single().kind.hebrewName
    } else {
        prisha.joinToString(" + ") { it.kind.shortName }
    }
    CellCaption(
        title = title,
        onah = prisha.first().onah?.hebrewName ?: "?",
        color = WomensAreaPrishaRed,
    )
}

@Composable
private fun CellCaption(title: String, onah: String?, color: Color) {
    Text(
        text = title,
        fontSize = 8.sp,
        lineHeight = 9.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    if (onah != null) {
        Text(text = onah, fontSize = 8.sp, lineHeight = 9.sp, color = color, textAlign = TextAlign.Center)
    }
}

private val VesetKind.shortName: String
    get() = when (this) {
        VesetKind.ONAH_BEINONIT -> "ע״ב"
        VesetKind.HAFLAGA -> "הפלגה"
        VesetKind.YOM_HACHODESH -> "יוה״ח"
    }
