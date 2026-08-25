package com.zmanimclock.app.feature.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zmanimclock.app.ui.components.ZmanChecklistLogic
import com.zmanimclock.app.ui.components.ZmanGroupedChecklist
import com.zmanimclock.app.ui.theme.ZmanimTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Widget configuration.
 *
 * Three independent sections the user switches on or off — the Hebrew date,
 * the halachic zmanim (and which ones), and their own alarms — so the same
 * widget can be a quiet date strip or a full board. Launched when the widget
 * is placed AND from long-press → reconfigure.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Assume cancelled until the user saves
        setResult(Activity.RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }

        val initial = WidgetPrefs.getConfig(this, widgetId)

        setContent {
            ZmanimTheme {
                var showDate by remember { mutableStateOf(initial.showHebrewDate) }
                var showNext by remember { mutableStateOf(initial.showNextZman) }
                var showZmanim by remember { mutableStateOf(initial.showZmanim) }
                var showAlarms by remember { mutableStateOf(initial.showAlarms) }
                var alarmCount by remember { mutableIntStateOf(initial.alarmCount) }
                val selected = remember {
                    mutableStateListOf<String>().apply { addAll(initial.zmanim) }
                }

                val nothingOn = !showDate && !showNext &&
                    (!showZmanim || selected.isEmpty()) && !showAlarms

                Scaffold { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .padding(16.dp),
                    ) {
                        Text("הגדרת הווידג'ט", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "בחר מה יופיע. אפשר להוסיף כמה ווידג'טים עם תוכן שונה.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        )

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // A live mock of the widget, updating as the user
                            // toggles. Without it the whole screen is a list of
                            // abstract switches whose effect only becomes
                            // visible AFTER saving and returning to the home
                            // screen — and (before the reconfigure flag was
                            // added) getting back here meant deleting the
                            // widget. Seeing the result while choosing is the
                            // difference between configuring and guessing.
                            item {
                                WidgetMockPreview(
                                    showDate = showDate,
                                    showNext = showNext,
                                    zmanim = if (showZmanim) selected.toList() else emptyList(),
                                    showAlarms = showAlarms,
                                    alarmCount = alarmCount,
                                )
                            }

                            item {
                                SectionCard {
                                    ToggleRow(
                                        title = "תאריך עברי",
                                        subtitle = "התאריך העברי, שם העיר והתאריך הלועזי",
                                        checked = showDate,
                                        onChange = { showDate = it },
                                    )
                                    HorizontalDivider()
                                    ToggleRow(
                                        title = "הזמן הבא",
                                        subtitle = "שם הזמן, השעה וספירה לאחור חיה",
                                        checked = showNext,
                                        onChange = { showNext = it },
                                    )
                                }
                            }

                            item {
                                SectionCard {
                                    ToggleRow(
                                        title = "זמנים הלכתיים",
                                        subtitle = if (showZmanim) {
                                            "${selected.size} מתוך ${WidgetPrefs.MAX_ZMANIM} נבחרו"
                                        } else {
                                            "כבוי"
                                        },
                                        checked = showZmanim,
                                        onChange = { showZmanim = it },
                                    )
                                }
                            }

                            if (showZmanim) {
                                // Grouped by time of day. A flat 19-row scroll
                                // of similarly-worded halachic names is very
                                // hard to scan — several differ only by a
                                // shita suffix — so the user hunts rather than
                                // picks. The buckets match how someone thinks
                                // about their own day. Each row carries the
                                // same illustrative time as the mock above, so
                                // the two halves of the screen visibly agree.
                                item {
                                    val atLimit = selected.size >= WidgetPrefs.MAX_ZMANIM
                                    SectionCard {
                                        ZmanGroupedChecklist(
                                            groups = ZMAN_GROUPS,
                                            isChecked = { it.name in selected },
                                            enabled = { it.name in selected || !atLimit },
                                            onToggle = { kind ->
                                                val next = ZmanChecklistLogic.toggleCapped(
                                                    selected.toList(),
                                                    kind.name,
                                                    WidgetPrefs.MAX_ZMANIM,
                                                )
                                                selected.clear()
                                                selected.addAll(next)
                                            },
                                            trailing = { SAMPLE_TIMES[it] },
                                            modifier = Modifier.padding(vertical = 6.dp),
                                        )
                                        if (atLimit) {
                                            Text(
                                                "נבחרו ${WidgetPrefs.MAX_ZMANIM} הזמנים המרביים — " +
                                                    "כדי להוסיף זמן אחר, בטל קודם סימון של אחד",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(
                                                    start = 12.dp, end = 12.dp, bottom = 10.dp,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                SectionCard {
                                    ToggleRow(
                                        title = "שעונים מעוררים",
                                        subtitle = "השעונים הפעילים הקרובים, לפי סדר הזמנים",
                                        checked = showAlarms,
                                        onChange = { showAlarms = it },
                                    )
                                    if (showAlarms) {
                                        HorizontalDivider()
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                "כמה להציג",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                (1..WidgetPrefs.MAX_ALARMS).forEach { n ->
                                                    FilterChip(
                                                        selected = alarmCount == n,
                                                        onClick = { alarmCount = n },
                                                        label = { Text("$n") },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (nothingOn) {
                            Text(
                                "צריך לבחור לפחות דבר אחד",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        Button(
                            onClick = {
                                save(
                                    WidgetPrefs.Config(
                                        showHebrewDate = showDate,
                                        showNextZman = showNext,
                                        showZmanim = showZmanim,
                                        // Save exactly what the user chose, including
                                        // empty. Collapsing an explicit empty selection
                                        // back to WidgetPrefs.DEFAULT_SELECTION here used
                                        // to silently resurrect all four defaults the
                                        // instant the user unchecked them — the subtitle
                                        // above even confirms "0 מתוך 8 נבחרו" while שמירה
                                        // stays enabled (another section is on), so nothing
                                        // in the UI hinted the save would not do what it said.
                                        zmanim = selected.toList(),
                                        showAlarms = showAlarms,
                                        alarmCount = alarmCount,
                                    )
                                )
                            },
                            enabled = !nothingOn,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("שמירה") }
                    }
                }
            }
        }
    }

    private fun save(config: WidgetPrefs.Config) {
        WidgetPrefs.setConfig(this, widgetId, config)
        // Render immediately, then return OK so the launcher places the widget
        val renderer = dagger.hilt.android.EntryPointAccessors
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
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) { Column { content() } }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
