package com.zmanimclock.app.feature.calendar.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zmanimclock.app.feature.zmanim.data.model.ZmanCategory
import com.zmanimclock.app.feature.zmanim.data.model.ZmanTime
import com.zmanimclock.app.ui.theme.ShabbatGold
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarViewMonth,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("לוח זמנים", style = MaterialTheme.typography.titleMedium)
                }
            },
            actions = {
                // Toggle Hebrew / Gregorian
                FilterChip(
                    selected = state.calendarMode == CalendarMode.HEBREW,
                    onClick = { viewModel.toggleCalendarMode() },
                    label = {
                        Text(
                            if (state.calendarMode == CalendarMode.HEBREW) "עברי" else "לועזי",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "החלף תצוגה",
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    modifier = Modifier.padding(end = 8.dp),
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        // Location
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                state.locationName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }

        // Month navigation
        MonthNavigationBar(
            title = state.displayMonthTitle,
            onPrevious = { viewModel.navigateMonth(-1) },
            onNext = { viewModel.navigateMonth(1) },
        )

        // Day of week headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            state.dayOfWeekHeaders.forEach { header ->
                Text(
                    text = header,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Calendar grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            userScrollEnabled = false,
        ) {
            items(state.days) { day ->
                DayCell(
                    day = day,
                    calendarMode = state.calendarMode,
                    isSelected = state.selectedDay?.let {
                        it.gregorianYear == day.gregorianYear &&
                            it.gregorianMonth == day.gregorianMonth &&
                            it.gregorianDay == day.gregorianDay
                    } ?: false,
                    onClick = { viewModel.selectDay(day) },
                )
            }
        }
    }

    // Day detail bottom sheet
    if (state.showDayDetail) {
        DayDetailBottomSheet(
            state = state,
            onDismiss = { viewModel.dismissDayDetail() },
        )
    }
}

@Composable
private fun MonthNavigationBar(
    title: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // In RTL, "right arrow" goes to previous, "left arrow" goes to next
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "חודש הבא",
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "חודש קודם",
            )
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    calendarMode: CalendarMode,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        day.isToday -> MaterialTheme.colorScheme.primaryContainer
        !day.isCurrentMonth -> Color.Transparent
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        day.isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val secondaryTextColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
        day.isToday -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
        !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }

    val primaryText: String
    val secondaryText: String
    if (calendarMode == CalendarMode.HEBREW) {
        primaryText = day.hebrewDayFormatted
        secondaryText = day.gregorianDay.toString()
    } else {
        primaryText = day.gregorianDay.toString()
        secondaryText = day.hebrewDayFormatted
    }

    Box(
        modifier = Modifier
            .aspectRatio(0.85f)
            .padding(1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .then(
                if (day.isToday && !isSelected) {
                    Modifier.border(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(6.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(enabled = day.isCurrentMonth) { onClick() }
            .padding(2.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Primary date
            Text(
                text = primaryText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (day.isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
            )

            // Secondary date (smaller)
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.labelSmall,
                color = secondaryTextColor,
                textAlign = TextAlign.Center,
                fontSize = 9.sp,
            )

            // Holiday indicator
            if (day.holiday != null && day.isCurrentMonth) {
                Text(
                    text = day.holiday,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else ShabbatGold,
                    textAlign = TextAlign.Center,
                    fontSize = 7.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 8.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailBottomSheet(
    state: CalendarUiState,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        timeZone = state.locationTimeZone
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            // Header: Hebrew date + Gregorian date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = state.selectedDayHebrewDate,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.selectedDayGregorianDate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "סגור")
                }
            }

            // Holiday
            state.selectedDayHoliday?.let { holiday ->
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = ShabbatGold.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = holiday,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Zmanim list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val categoryOrder = listOf(
                    ZmanCategory.MORNING, ZmanCategory.MIDDAY, ZmanCategory.AFTERNOON,
                    ZmanCategory.EVENING, ZmanCategory.SHABBAT_HOLIDAY, ZmanCategory.NIGHT,
                    ZmanCategory.SEASONAL,
                )

                categoryOrder.forEach { category ->
                    val categoryZmanim = state.selectedDayZmanimByCategory[category]
                    if (!categoryZmanim.isNullOrEmpty()) {
                        item {
                            DetailCategoryHeader(category)
                        }
                        items(categoryZmanim, key = { it.id.name }) { zman ->
                            DetailZmanRow(zman = zman, timeFormatter = timeFormatter)
                        }
                    }
                }

                // Shaah Zmanit
                item {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "שעה זמנית גר\"א",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    state.selectedDayShaahGra,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "שעה זמנית מג\"א",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    state.selectedDayShaahMga,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun DetailCategoryHeader(category: ZmanCategory) {
    val (icon, label) = when (category) {
        ZmanCategory.MORNING -> Icons.Outlined.LightMode to "בוקר"
        ZmanCategory.MIDDAY -> Icons.Default.WbSunny to "צהריים"
        ZmanCategory.AFTERNOON -> Icons.Default.WbTwilight to "אחר הצהריים"
        ZmanCategory.EVENING -> Icons.Outlined.DarkMode to "ערב"
        ZmanCategory.NIGHT -> Icons.Outlined.DarkMode to "לילה"
        ZmanCategory.SHABBAT_HOLIDAY -> Icons.Default.WbSunny to "שבת וחג"
        ZmanCategory.SEASONAL -> Icons.Default.WbSunny to "זמנים עונתיים"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun DetailZmanRow(
    zman: ZmanTime,
    timeFormatter: SimpleDateFormat,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(zman.id.hebrewNameRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = zman.displayValue
                ?: zman.time?.let { timeFormatter.format(it) }
                ?: "--:--",
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
