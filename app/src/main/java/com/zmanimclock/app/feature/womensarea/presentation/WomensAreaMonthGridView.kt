package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmanimclock.app.feature.calendar.model.CalendarDayMeta
import com.zmanimclock.app.feature.calendar.model.MonthGrid
import com.zmanimclock.app.feature.womensarea.model.Onah
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
// Hebrew and civil numerals, a separation day's name and its יום/לילה, and
// the clean-day number at the bottom.
// Still a fixed 6 rows, for the same reason as the Calendar tab: a Hebrew
// month is 29 or 30 days on any weekday, and a constant height keeps the grid
// from resizing under a swipe.
private val CELL_HEIGHT = 88.dp

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
            // Top line: the count from the veset (start side) and the tevila
            // star (end side). Fixed height so the numerals never shift.
            Row(Modifier.fillMaxWidth().height(15.dp), verticalAlignment = Alignment.CenterVertically) {
                // A helper, not the point of the day — so smaller than the
                // clean-day number and the Hebrew date.
                marker?.countDayNumber?.let {
                    CountChip(it, WomensAreaCountBlue, RoundedCornerShape(4.dp), size = 12.dp, fontSize = 7.5f)
                }
                Spacer(Modifier.weight(1f))
                if (isTevila) {
                    Text("★", fontSize = 12.sp, lineHeight = 13.sp, color = WomensAreaTevilaBlue)
                }
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
            // Bottom: what the day is, then the clean-day number — kept apart
            // from the blue count at the top so the two never read as one.
            when {
                isPrisha -> {
                    CellTitle(prishaName(prisha), WomensAreaPrishaRed, maxLines = if (cleanDayNumber != null) 1 else 2)
                    OnahPill(prisha.first().onah, WomensAreaPrishaRed)
                }
                isVesetDay -> {
                    CellTitle("ראייה", cs.onSurface)
                    OnahPill(marker?.vesetOnah, cs.onSurface)
                }
                isTevila -> {
                    CellTitle("טבילה", WomensAreaTevilaBlue)
                    CellTitle("אחר צאה״כ", WomensAreaTevilaBlue, bold = false)
                }
                isHefsek -> CellTitle("הפסק טהרה", WomensAreaCleanGreen, maxLines = 2)
            }
            cleanDayNumber?.let {
                Spacer(Modifier.height(2.dp))
                CountChip(it, WomensAreaCleanGreen, CircleShape)
            }
        }
    }
}

@Composable
private fun CountChip(number: Int, color: Color, shape: Shape, size: Dp = 15.dp, fontSize: Float = 9f) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = size, minHeight = size)
            .clip(shape)
            .background(color)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            fontSize = fontSize.sp,
            lineHeight = (fontSize + 1).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/**
 * The separation day's name in the cell. A single kind gets its full name
 * (wrapping to two lines if needed); two kinds on one day — e.g. day 30 that
 * is also the same date next month — get short forms side by side. The card
 * under the calendar spells every one out in full.
 */
private fun prishaName(prisha: List<PrishaDay>): String =
    if (prisha.size == 1) prisha.single().kind.hebrewName else prisha.joinToString(" + ") { it.kind.shortName }

@Composable
private fun CellTitle(text: String, color: Color, maxLines: Int = 1, bold: Boolean = true) {
    Text(
        text = text,
        fontSize = 8.sp,
        lineHeight = 9.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * "☀ יום" / "☾ לילה" — the onah the veset was entered with, on the veset day
 * and on every separation day it produces. Outlined and with its own icon so
 * it reads at a glance, not as one more line of 8sp text.
 */
@Composable
private fun OnahPill(onah: Onah?, color: Color) {
    Row(
        modifier = Modifier
            .padding(top = 1.dp)
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .padding(horizontal = 3.dp, vertical = 0.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        when (onah) {
            Onah.DAY -> Icon(Icons.Filled.WbSunny, contentDescription = null, tint = color, modifier = Modifier.size(8.dp))
            Onah.NIGHT -> Icon(Icons.Filled.NightsStay, contentDescription = null, tint = color, modifier = Modifier.size(8.dp))
            null -> Unit
        }
        Text(
            text = when (onah) {
                Onah.DAY -> "יום"
                Onah.NIGHT -> "לילה"
                null -> "לא צוין"
            },
            fontSize = 8.5.sp,
            lineHeight = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

private val VesetKind.shortName: String
    get() = when (this) {
        VesetKind.ONAH_BEINONIT -> "ע״ב"
        VesetKind.HAFLAGA -> "הפלגה"
        VesetKind.YOM_HACHODESH -> "יוה״ח"
    }
