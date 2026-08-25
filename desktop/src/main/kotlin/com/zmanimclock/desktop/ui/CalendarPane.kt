package com.zmanimclock.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmanimclock.app.feature.calendar.model.CalendarDayMeta
import com.zmanimclock.app.feature.calendar.model.HebrewMonthSequence
import com.zmanimclock.app.feature.calendar.model.MonthEvent
import com.zmanimclock.app.feature.calendar.model.MonthGrid
import com.zmanimclock.desktop.Ext
import com.zmanimclock.desktop.data.DayView
import com.zmanimclock.desktop.data.DesktopZmanimService
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate

/**
 * The לוח שנה tab: a Hebrew month grid beside the day it selects.
 *
 * THE ONE STRUCTURAL DIFFERENCE FROM ANDROID
 * On a phone the grid has to collapse to a single week row, because a month
 * grid and seventeen zman rows cannot share a narrow screen. A desktop window
 * is wide, so the two panes sit SIDE BY SIDE and the entire collapse /
 * nested-scroll / sticky-header mechanism is simply absent. Not simplified —
 * absent. Nothing below reimplements it in another form.
 *
 * Not one line of calendar ARITHMETIC differs: MonthGridBuilder,
 * HebrewMonthSequence and asZmanTime all come from :zmanim-engine, the same
 * module the Android app compiles against.
 */
@Composable
fun CalendarPane(service: DesktopZmanimService) {
    var now by remember { mutableStateOf(Instant.now()) }

    // Wall-clock tick, the same as ZmanimPane: the delay is recomputed from
    // the actual clock each iteration, so the countdown lands on the second
    // boundary and self-corrects after the machine sleeps rather than drifting
    // further out the longer the window stays open.
    LaunchedEffect(Unit) {
        while (true) {
            val n = Instant.now()
            now = n
            delay(1_000L - (n.toEpochMilli() % 1_000L))
        }
    }

    val today = LocalDate.now(service.zone)
    var selected by remember { mutableStateOf(today) }
    var monthIndex by remember {
        mutableStateOf(HebrewMonthSequence.clamp(HebrewMonthSequence.indexOf(today)))
    }

    // Selecting a day in another month — via a filler cell, a ribbon chip or
    // the day arrows — makes the grid follow. Guarded on inequality so the two
    // cannot drive each other in a loop.
    LaunchedEffect(selected) {
        val i = HebrewMonthSequence.indexOf(selected)
        if (i >= 0 && i != monthIndex) monthIndex = i
    }

    val grid = remember(monthIndex) { service.monthGrid(monthIndex) }
    val todayMonthIndex = remember(today) { HebrewMonthSequence.indexOf(today) }
    val view = service.view(selected, now)

    fun stepMonth(by: Int) {
        monthIndex = HebrewMonthSequence.clamp(monthIndex + by)
    }

    Row(Modifier.fillMaxSize()) {
        // DECLARATION ORDER IS THE LAYOUT. Under the app's forced RTL the FIRST
        // child of a Row is placed on the RIGHT — so the month grid is declared
        // first (it lands right) and the day detail second (it lands left),
        // matching the mockup in DESKTOP_PLAN.md §4א.
        MonthSide(
            grid = grid,
            today = today,
            selected = selected,
            showTodayButton = monthIndex != todayMonthIndex,
            onSelect = { selected = it },
            onStepMonth = ::stepMonth,
            onToday = {
                selected = today
                monthIndex = HebrewMonthSequence.clamp(todayMonthIndex)
            },
            // The month takes whatever the window has SPARE.
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        VerticalDivider()
        DaySide(
            view = view,
            service = service,
            now = now,
            onShiftDay = { selected = selected.plusDays(it.toLong()) },
            // FIXED, and equal to the whole window on the zmanim tab.
            //
            // These were 0.45/0.55 of the window, which meant opening the
            // calendar RESIZED the zmanim column — the same list, the same
            // rows, at a different width, so switching tabs made the times
            // jump about. The day column is now the same width wherever it
            // appears and the month grid is a genuine ADDITION beside it, not
            // a renegotiation of the space.
            modifier = Modifier.width(DAY_SIDE_WIDTH).fillMaxHeight(),
        )
    }
}

/**
 * The zmanim column's width, on the calendar tab and on the zmanim tab alike.
 *
 * Equal to MainTab.ZMANIM's window width, so the list looks identical in both
 * places. If one moves, move the other.
 */
private val DAY_SIDE_WIDTH = 400.dp

// ---------------------------------------------------------------------------
// Month side
// ---------------------------------------------------------------------------

/** ראשון … שבת. Declared Sunday-first; RTL puts ראשון on the right by itself. */
private val WEEKDAY_INITIALS = listOf("א", "ב", "ג", "ד", "ה", "ו", "ש")

/**
 * Desktop density, deliberately not the phone's.
 *
 * The Android cell is 58dp because it is also a 48dp-minimum touch target with
 * three stacked lines. A mouse needs no such minimum, and a first desktop build
 * at phone sizes was rejected as far too large. 34dp holds the three lines at
 * 13/9/7sp with nothing clipped, and keeps six rows to ~204dp so the grid, the
 * ribbon and the day pane all sit above the fold of an 600dp window.
 */
// 34dp was not enough: three stacked Texts measure taller than the sum of
// their lineHeights (font ascent/descent metrics add to it), so the parsha
// silently vanished from every Shabbat cell — the identical defect the Android
// grid hit at 52dp. Verified by offscreen render, not by arithmetic.
private val CELL_HEIGHT = 42.dp

@Composable
private fun MonthSide(
    grid: MonthGrid,
    today: LocalDate,
    selected: LocalDate,
    showTodayButton: Boolean,
    onSelect: (LocalDate) -> Unit,
    onStepMonth: (Int) -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
        MonthHeader(
            grid = grid,
            showTodayButton = showTodayButton,
            onBackward = { onStepMonth(-1) },
            onForward = { onStepMonth(1) },
            onToday = onToday,
        )
        MonthGridBody(
            grid = grid,
            today = today,
            selected = selected,
            onSelect = onSelect,
            onStepMonth = onStepMonth,
        )
        MonthEventRibbon(events = grid.events, onSelect = onSelect)
    }
}

/**
 * Hebrew month and year large and clickable, Gregorian span small underneath,
 * an arrow on each side.
 *
 * ⚠️ ARROW DIRECTION — the trap this comment exists to keep shut.
 * In a Hebrew calendar forward-in-time is LEFTWARD. Under the app's forced RTL
 * the FIRST child of a Row is placed on the RIGHT. Therefore:
 *
 *   declared FIRST  → sits on the RIGHT → steps BACKWARD in time → glyph ›
 *   declared LAST   → sits on the LEFT  → steps FORWARD  in time → glyph ‹
 *
 * The glyphs are picked by the direction they physically point, via
 * `Icons.Filled.*`. `Icons.AutoMirrored.*` must NEVER be used here: it mirrors
 * itself under RTL, which combined with the forced RTL flips both arrows back
 * to pointing the wrong way — the exact bug this layout already had once.
 */
@Composable
private fun MonthHeader(
    grid: MonthGrid,
    showTodayButton: Boolean,
    onBackward: () -> Unit,
    onForward: () -> Unit,
    onToday: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // RIGHT-hand arrow: back one month, chevron pointing right.
        StepChevron(pointsLeft = false, description = "החודש הקודם", onClick = onBackward)
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable(onClick = onToday)
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Two Hebrew runs, so one string is safe here — unlike the day
            // cells, where a Hebrew and a Latin run must stay separate.
            Text(
                "${grid.hebrewMonthLabel}  ${grid.hebrewYearLabel}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
            )
            Text(
                grid.gregorianSpanLabel,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
        }
        // LEFT-hand arrow: forward one month, chevron pointing left.
        StepChevron(pointsLeft = true, description = "החודש הבא", onClick = onForward)
        // Fixed slot: the button appears and disappears with the visible month,
        // and without a reserved width the arrows would shift as it does.
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            if (showTodayButton) {
                Text(
                    "היום",
                    Modifier.clip(RoundedCornerShape(50)).background(cs.primaryContainer)
                        .clickable(onClick = onToday)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = cs.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * A step arrow, named for the direction the GLYPH points rather than for
 * "next"/"previous" — the two are opposite here, and naming it by intent is
 * what makes the mistake easy to reintroduce.
 */
@Composable
private fun StepChevron(pointsLeft: Boolean, description: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(26.dp).clip(CircleShape).background(cs.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (pointsLeft) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight,
            contentDescription = description,
            tint = cs.onPrimaryContainer,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The weekday header and the six week rows, over one painted Shabbat column.
 *
 * ALWAYS six rows, even when five suffice. A Hebrew month is 29 or 30 days and
 * can begin on any weekday, so `firstColumnOffset + daysInMonth` is between 29
 * and 36 — never fewer than 5 rows, never more than 6. Reserving the sixth
 * keeps the grid a constant height, so the ribbon and the divider below it do
 * not jump a row's worth every time the month changes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MonthGridBody(
    grid: MonthGrid,
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onStepMonth: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shabbatTint = cs.primaryContainer.copy(alpha = 0.35f)

    // A precision touchpad emits many fractional scroll events per gesture
    // while a wheel notch emits one unit, so travel is accumulated and the
    // month steps on whole units. A plain array rather than a State: this is
    // written from the pointer callback and never read during composition, so
    // giving it snapshot machinery would only add recompositions.
    val wheelTravel = remember { floatArrayOf(0f) }

    Box(
        Modifier.fillMaxWidth().onPointerEvent(PointerEventType.Scroll) { event ->
            // scrollDelta.y is positive when the wheel turns toward the user,
            // i.e. "down the page" — mapped to forward in time, the same way
            // every desktop calendar behaves.
            wheelTravel[0] += event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
            val steps = wheelTravel[0].toInt()
            if (steps != 0) {
                wheelTravel[0] -= steps
                onStepMonth(steps)
            }
        },
    ) {
        // The Shabbat column is painted ONCE, behind the whole grid, rather
        // than per cell: that is what a printed luach does, it costs nothing
        // per cell, and it reads as one continuous column running up into the
        // ש header instead of seven separate tinted boxes.
        // Column 0 is ראשון and column 6 is שבת; under RTL the LAST child of
        // this Row lands on the left, which is where שבת belongs.
        Row(Modifier.matchParentSize()) {
            repeat(6) { Spacer(Modifier.weight(1f)) }
            Box(Modifier.weight(1f).fillMaxHeight().background(shabbatTint))
        }

        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                WEEKDAY_INITIALS.forEachIndexed { i, label ->
                    Text(
                        label,
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = if (i == 6) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == 6) cs.primary else cs.onSurfaceVariant,
                    )
                }
            }
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
    val dim = !meta.isInDisplayedMonth
    val shape = RoundedCornerShape(6.dp)

    // Shabbat is deliberately absent from this list: it is the stripe painted
    // behind the grid, not a per-cell fill.
    val background = when {
        isSelected -> cs.primary
        meta.isYomTovAssurBemelacha -> cs.tertiaryContainer
        // Chol hamoed reads as "half a yom tov" — which is exactly right.
        meta.isCholHamoed -> cs.tertiaryContainer.copy(alpha = 0.4f)
        else -> Color.Transparent
    }
    val hebrewColor = when {
        isSelected -> cs.onPrimary
        dim -> cs.onSurfaceVariant.copy(alpha = 0.38f)
        meta.isRoshChodesh -> ext.accentGold
        meta.isYomTovAssurBemelacha -> cs.onTertiaryContainer
        else -> cs.onSurface
    }
    val gregorianColor =
        if (isSelected) cs.onPrimary.copy(alpha = 0.75f)
        else cs.onSurfaceVariant.copy(alpha = if (dim) 0.38f else 1f)

    // Today is an outline and selected is a fill, which is the cleanest way to
    // tell them apart. When a cell is BOTH, the fill wins and the ring turns
    // gold — a ring rather than the mockup's gold dot because the bottom of the
    // cell is already spoken for by the fast bar and the modern-holiday dot,
    // and a day can be today, selected and a fast at once (10 בטבת, often).
    val ring = when {
        isToday && isSelected -> ext.accentGold
        isToday -> cs.primary
        else -> null
    }

    Box(
        modifier
            .height(CELL_HEIGHT)
            .padding(1.dp)
            .clip(shape)
            .background(background)
            .then(if (ring != null) Modifier.border(2.dp, ring, shape) else Modifier)
            // Filler days stay clickable: tapping one jumps to that month and
            // selects the day, a free shortcut. Empty cells were rejected —
            // they make the first and last weeks read as broken and cut the
            // Shabbat column in half.
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // ⚠️ The Hebrew numeral and the Latin one stay in SEPARATE Text
            // composables. Interpolated into one string they are handed to the
            // bidi algorithm, which reorders them in a way that looks right in
            // some months and wrong in others. Same rule as the Gregorian span
            // in the header.
            // Explicit lineHeight on every line: the default leading is
            // generous enough that three stacked Texts overflow a 34dp cell and
            // the parsha silently vanishes.
            Text(
                meta.hebrewDayLabel,
                fontSize = 13.sp,
                lineHeight = 13.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = hebrewColor,
            )
            Text(
                meta.gregorianDayLabel,
                fontSize = 9.sp,
                lineHeight = 9.sp,
                color = gregorianColor,
            )
            // The parsha only exists on Shabbat — formatParsha returns blank on
            // every other day — and it is the single most looked-up datum in a
            // Hebrew calendar, so it earns the third line.
            meta.parshaName?.takeIf { meta.isShabbat && !dim }?.let {
                Text(
                    it,
                    fontSize = 7.sp,
                    lineHeight = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) cs.onPrimary.copy(alpha = 0.9f) else cs.primary,
                )
            }
        }

        // Rosh Chodesh already recolours the numeral; the hairline above it
        // makes the day findable by scanning rather than by reading.
        if (meta.isRoshChodesh && !dim && !isSelected) {
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 1.dp)
                    .size(width = 12.dp, height = 2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(ext.accentGold),
            )
        }
        // A fast is a state that holds all day, so it reads as an underline. A
        // dot would read as "an event happens at some point".
        if (meta.fast != null && !dim) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp)
                    .size(width = 12.dp, height = 2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(ext.deadline),
            )
        }
        // Modern Israeli holidays are marked softly and never like Yom Tov:
        // they are not assur bemelacha, and a fill would say that they are.
        if (meta.isModernHoliday && !dim && meta.fast == null) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp)
                    .size(3.dp).clip(CircleShape)
                    .background(if (isSelected) cs.onPrimary else cs.primary),
            )
        }
    }
}

/**
 * The month's notable days as chips.
 *
 * Not a duplicate of the grid: the grid answers "what date is X", the ribbon
 * answers "what happens this month" — and it gives holiday names the room a
 * 34dp cell cannot, which is why no cell ever tries to print one.
 */
@Composable
private fun MonthEventRibbon(events: List<MonthEvent>, onSelect: (LocalDate) -> Unit) {
    if (events.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val ext = Ext.colors
    // LazyRow rather than horizontalScroll: it lays items out RTL-first and it
    // takes contentPadding, so the row starts inset from the pane edge instead
    // of the first chip appearing sliced off by it.
    LazyRow(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
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
                Modifier.clip(RoundedCornerShape(50)).background(tint)
                    .clickable { onSelect(event.date) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    color = cs.onSurface,
                )
                Text(
                    " · ${event.hebrewDayLabel}",
                    fontSize = 9.sp,
                    maxLines = 1,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Day side
// ---------------------------------------------------------------------------

/**
 * The selected day: the same navy hero and the same zman rows as the זמנים
 * tab, drawn by the same composables rather than by lookalikes — so the two
 * tabs cannot drift apart in wording, formatting or rounding.
 */
@Composable
private fun DaySide(
    view: DayView,
    service: DesktopZmanimService,
    now: Instant,
    onShiftDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier) {
        DayHero(view, service, now)
        DayBar(view.weekdayName, onShiftDay)
        HorizontalDivider()
        if (view.undefined) {
            // Stated rather than shown as an empty list: at extreme latitudes
            // the sun may not cross the required depression angle at all, and a
            // blank pane would read as a loading failure.
            Text(
                "לא ניתן לחשב זמנים ליום זה במיקום הנבחר.",
                Modifier.fillMaxWidth().padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(view.rows, key = { it.kind.name }) { ZmanListRow(it, compact = true) }
            }
        }
    }
}

/**
 * Weekday plus day arrows, so a day can be stepped without returning to the
 * grid — and so crossing a month boundary is one click rather than two.
 *
 * Same direction rule as the month header: declared FIRST lands on the RIGHT
 * and steps BACKWARD (chevron ›), declared LAST lands on the LEFT and steps
 * FORWARD (chevron ‹). Never Icons.AutoMirrored.
 */
@Composable
private fun DayBar(weekdayName: String, onShiftDay: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepChevron(pointsLeft = false, description = "היום הקודם") { onShiftDay(-1) }
        Text(
            weekdayName,
            Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        StepChevron(pointsLeft = true, description = "היום הבא") { onShiftDay(1) }
    }
}
