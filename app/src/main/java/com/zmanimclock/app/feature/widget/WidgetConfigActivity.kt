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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.ui.theme.ZmanimTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Widget configuration: pick which zmanim (up to 5) appear on this widget.
 * Launched when the widget is placed AND from long-press → reconfigure.
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

        val initial = WidgetPrefs.getSelection(this, widgetId).toSet()

        setContent {
            ZmanimTheme {
                val selected = remember {
                    mutableStateListOf<String>().apply { addAll(initial) }
                }
                Scaffold { padding ->
                    Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                        Text("בחר זמנים לווידג'ט", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "עד 5 זמנים · הזמן הבא והספירה לאחור תמיד מוצגים",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(ZmanKind.entries.toList()) { kind ->
                                val checked = kind.name in selected
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (checked) selected.remove(kind.name)
                                            else if (selected.size < 5) selected.add(kind.name)
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { on ->
                                            if (on) { if (selected.size < 5) selected.add(kind.name) }
                                            else selected.remove(kind.name)
                                        },
                                    )
                                    Text(kind.hebrewName, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        Button(
                            onClick = { save(selected.toList()) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("שמירה") }
                    }
                }
            }
        }
    }

    private fun save(kinds: List<String>) {
        WidgetPrefs.setSelection(this, widgetId, kinds.ifEmpty { WidgetPrefs.DEFAULT_SELECTION })
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
