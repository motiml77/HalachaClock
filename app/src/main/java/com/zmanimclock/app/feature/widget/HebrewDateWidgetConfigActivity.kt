package com.zmanimclock.app.feature.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.ui.theme.ZmanimTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Configuration for the Hebrew-date-only widget: a background color from a
 * fixed palette, and how transparent the whole thing is. Nothing to switch
 * on or off — this widget IS the date, always — so there is no "nothing
 * selected" state to guard against the way [WidgetConfigActivity] has to.
 *
 * Launched when the widget is placed AND from long-press → reconfigure.
 */
@AndroidEntryPoint
class HebrewDateWidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var prefsRepository: UserPreferencesRepository

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val hebrewFormatter = HebrewDateFormatter().apply {
        isHebrewFormat = true
        isUseGershGershayim = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }

        val initial = HebrewDateWidgetPrefs.getConfig(this, widgetId)

        setContent {
            ZmanimTheme {
                var preset by remember { mutableStateOf(initial.preset) }
                var opacity by remember { mutableIntStateOf(initial.opacityPercent) }

                // Starts from the system zone so the preview shows something
                // immediately, then refines to the app's configured zone —
                // see the LaunchedEffect below. A preview briefly one zone
                // off is a far smaller problem than a blank screen on open.
                var parts by remember {
                    mutableStateOf(
                        hebrewDateParts(LocalDate.now(), ZoneId.systemDefault(), hebrewFormatter),
                    )
                }
                LaunchedEffect(Unit) {
                    runCatching {
                        val zone = ZoneId.of(prefsRepository.schedulingPreferences().timeZoneId)
                        parts = hebrewDateParts(LocalDate.now(zone), zone, hebrewFormatter)
                    }
                }

                Scaffold { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .padding(16.dp),
                    ) {
                        Text("הגדרת ווידג'ט תאריך עברי", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "רק התאריך העברי — בחר צבע רקע ורמת שקיפות.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            HebrewDateWidgetMockPreview(parts, preset, opacity)

                            Spacer(Modifier.height(18.dp))
                            Text(
                                "צבע רקע",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                WidgetColorPreset.entries.forEach { candidate ->
                                    ColorSwatch(
                                        preset = candidate,
                                        selected = candidate == preset,
                                        onClick = { preset = candidate },
                                    )
                                }
                            }

                            Spacer(Modifier.height(22.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "שקיפות",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "$opacity%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Slider(
                                value = opacity.toFloat(),
                                onValueChange = { opacity = it.toInt() },
                                valueRange = HebrewDateWidgetPrefs.MIN_OPACITY_PERCENT.toFloat()..
                                    HebrewDateWidgetPrefs.MAX_OPACITY_PERCENT.toFloat(),
                                // 21 stops, 5% apart — steps counts the stops
                                // BETWEEN the two ends, not including them.
                                steps = 19,
                            )
                        }

                        Button(
                            onClick = {
                                save(HebrewDateWidgetPrefs.Config(preset = preset, opacityPercent = opacity))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("שמירה") }
                    }
                }
            }
        }
    }

    private fun save(config: HebrewDateWidgetPrefs.Config) {
        HebrewDateWidgetPrefs.setConfig(this, widgetId, config)
        // Render immediately, then return OK so the launcher places the widget.
        // Reuses ZmanWidgetProvider's EntryPoint — see HebrewDateWidgetProvider.
        val renderer = EntryPointAccessors
            .fromApplication(applicationContext, ZmanWidgetProvider.WidgetEntryPoint::class.java)
            .widgetRenderer()
        CoroutineScope(Dispatchers.Default).launch { runCatching { renderer.renderAll() } }

        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        )
        finish()
    }
}

@Composable
private fun ColorSwatch(preset: WidgetColorPreset, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(preset.color))
                .then(
                    if (selected) {
                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            preset.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
