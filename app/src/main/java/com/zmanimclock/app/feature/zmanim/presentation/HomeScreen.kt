package com.zmanimclock.app.feature.zmanim.presentation

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zmanimclock.app.feature.zmanim.data.model.ZmanCategory
import com.zmanimclock.app.feature.zmanim.data.model.ZmanId
import com.zmanimclock.app.feature.zmanim.data.model.ZmanOpinion
import com.zmanimclock.app.feature.zmanim.data.model.ZmanSource
import com.zmanimclock.app.feature.zmanim.data.model.ZmanTime
import com.zmanimclock.app.ui.theme.NextZmanHighlight
import com.zmanimclock.app.ui.theme.ShabbatGold
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToAlertEditor: (String) -> Unit,
    viewModel: ZmanimViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(state.locationName, style = MaterialTheme.typography.titleMedium)
                }
            },
            actions = {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "הגדרות")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.error ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.refresh() }) {
                        Text("נסה שוב")
                    }
                }
            }
        } else {

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header: Clock + Date
            item {
                HeaderSection(
                    currentTime = state.currentTime,
                    hebrewDate = state.hebrewDate,
                    secularDate = state.secularDate,
                    holiday = state.holiday,
                    nextZman = state.nextZman,
                    countdown = state.countdownToNext,
                )
            }

            // Zmanim by category
            val categoryOrder = listOf(
                ZmanCategory.MORNING, ZmanCategory.MIDDAY, ZmanCategory.AFTERNOON,
                ZmanCategory.EVENING, ZmanCategory.SHABBAT_HOLIDAY, ZmanCategory.NIGHT,
                ZmanCategory.SEASONAL,
            )

            categoryOrder.forEach { category ->
                val categoryZmanim = state.zmanimByCategory[category]
                if (!categoryZmanim.isNullOrEmpty()) {
                    item {
                        CategoryHeader(category)
                    }
                    items(categoryZmanim, key = { it.id.name }) { zman ->
                        ZmanRow(
                            zman = zman,
                            timeZone = state.locationTimeZone,
                            onInfoClick = { viewModel.showZmanInfo(zman.id) },
                            onAlertClick = { onNavigateToAlertEditor(zman.id.name) },
                        )
                    }
                }
            }

            // Shaah Zmanit info
            item {
                ShaahZmanitCard(state.shaahZmanisGra, state.shaahZmanisMga)
            }

            item { Spacer(Modifier.height(16.dp)) }
        }

        } // end else
    }

    // Info bottom sheet
    state.selectedInfoZman?.let { zmanId ->
        ZmanInfoBottomSheet(
            zmanId = zmanId,
            onDismiss = { viewModel.dismissZmanInfo() },
        )
    }
}

@Composable
private fun HeaderSection(
    currentTime: String,
    hebrewDate: String,
    secularDate: String,
    holiday: String?,
    nextZman: ZmanTime?,
    countdown: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Current time
            Text(
                text = currentTime,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(Modifier.height(4.dp))

            // Hebrew date
            Text(
                text = hebrewDate,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            // Secular date
            Text(
                text = secularDate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )

            // Holiday
            holiday?.let {
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = ShabbatGold.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Next zman countdown
            if (nextZman != null && countdown.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(nextZman.id.hebrewNameRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    text = "בעוד $countdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: ZmanCategory) {
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ZmanRow(
    zman: ZmanTime,
    timeZone: TimeZone,
    onInfoClick: () -> Unit,
    onAlertClick: () -> Unit,
) {
    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        this.timeZone = timeZone
    }
    val backgroundColor = when {
        zman.isNext -> NextZmanHighlight
        zman.isPassed -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }
    val textAlpha = if (zman.isPassed) 0.5f else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (zman.isNext) 4.dp else 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Zman name
            Text(
                text = stringResource(zman.id.hebrewNameRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (zman.isNext) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
                modifier = Modifier.weight(1f),
            )

            // Time or display value
            Text(
                text = zman.displayValue ?: zman.time?.let { timeFormatter.format(it) } ?: "--:--",
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
            )

            Spacer(Modifier.width(8.dp))

            // Info button - 48dp touch target
            IconButton(onClick = onInfoClick, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "מידע",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                )
            }

            // Alert bell - 48dp touch target
            IconButton(onClick = onAlertClick, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (zman.hasAlert) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                    contentDescription = "התראה",
                    modifier = Modifier.size(20.dp),
                    tint = if (zman.hasAlert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Composable
private fun ShaahZmanitCard(gra: String, mga: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("שעה זמנית גר\"א", style = MaterialTheme.typography.labelMedium)
                Text(gra, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("שעה זמנית מג\"א", style = MaterialTheme.typography.labelMedium)
                Text(mga, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZmanInfoBottomSheet(
    zmanId: ZmanId,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(zmanId.hebrewNameRes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(8.dp))

            // Opinion badge
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = when (zmanId.opinion) {
                        ZmanOpinion.GRA -> "שיטת הגר\"א"
                        ZmanOpinion.MAGEN_AVRAHAM -> "שיטת המגן אברהם"
                        ZmanOpinion.RABBEINU_TAM -> "שיטת רבנו תם"
                        ZmanOpinion.RAV_OVADIA -> "שיטת הרב עובדיה יוסף"
                        ZmanOpinion.YEREIM -> "שיטת היראים"
                        ZmanOpinion.GEONIM -> "שיטת הגאונים"
                        ZmanOpinion.GENERAL -> "כללי"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Info text
            Text(
                text = zmanId.infoText,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 28.sp,
            )

            // Calculation method note
            Spacer(Modifier.height(12.dp))
            Text(
                text = "כל הזמנים מחושבים לפי מיקומך ותאריך נוכחי",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

