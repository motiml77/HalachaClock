package com.zmanimclock.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.zmanimclock.app.feature.location.CityInfo
import com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.desktop.Ext
import com.zmanimclock.desktop.ZmanNumberFamily
import com.zmanimclock.desktop.data.DesktopZmanimService
import java.util.Locale

/**
 * Settings — location, the two halachic offsets, headline/reminder filters,
 * and Windows autostart.
 *
 * TWO COLUMNS, NOT ONE. The window is 860x600. A single scroll would leave the
 * city list a few rows tall while the right half of the screen sat empty, and
 * would bury the chip grids below the fold. So the picker (which needs a tall
 * list) and the chip grids (which need width) each get their own half.
 *
 * The left half is the only one that scrolls: the location half is laid out so
 * the list absorbs the leftover height instead, which keeps the search box and
 * the coordinates pinned in view while the user scans results.
 *
 * DENSITY is deliberate and matches ZmanimPane: titleSmall headings, bodySmall
 * body, compact hand-rolled chips and text fields. Material's own FilterChip,
 * OutlinedTextField and Button all carry phone-sized touch targets (32-56dp of
 * height for a line of 11sp text), which is what made an earlier build read as
 * a blown-up phone app on a desktop.
 */
@Composable
fun SettingsPane(service: DesktopZmanimService) {
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        LocationHalf(service, Modifier.weight(1f).fillMaxHeight())
        VerticalDivider(
            Modifier.padding(horizontal = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        FiltersHalf(service, Modifier.weight(1f).fillMaxHeight())
    }
}

// ---------------------------------------------------------------- location --

/** מיקום + הגדרות חישוב + autostart. Fixed layout; only the city list scrolls. */
@Composable
private fun LocationHalf(service: DesktopZmanimService, modifier: Modifier) {
    var query by remember { mutableStateOf("") }
    val city = service.city
    val results = remember(query) { service.searchCities(query) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // ---- 1. מיקום ----
        SectionTitle("מיקום")

        Panel {
            Text(
                city.nameHebrew,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (city.region.isNotBlank()) Hint(city.region, MaterialTheme.colorScheme.primary)

            Spacer(Modifier.height(2.dp))

            // The coordinates are shown as a value, not as decoration: they ARE
            // the zmanim input. Hebrew label and Latin/numeric value are kept
            // in separate Text composables — interpolating them into one string
            // hands a mixed run to the bidi algorithm under a forced-RTL root,
            // which reorders it in ways neither side of a comparison expects.
            CoordinateRow("קו רוחב", degrees(city.latitude))
            CoordinateRow("קו אורך", degrees(city.longitude))
            CoordinateRow("אזור זמן", city.timeZoneId)

            Spacer(Modifier.height(2.dp))
            Hint(
                "באנדרואיד אפשר לחשב לפי GPS; כאן החישוב תמיד לפי הנקודה של " +
                    "היישוב שנבחר. אם הזמנים בטלפון שונים — השווה קודם את " +
                    "הקואורדינטות, לרוב זה כל ההבדל.",
            )
            Hint("הגובה אינו נכנס לחישוב — הכל לפי היום המישורי.")
        }

        SearchBox(query, { query = it }, "חיפוש יישוב…")

        Hint(
            if (query.isBlank()) "${service.allCities.size} יישובים"
            else "${results.size} תוצאות",
        )

        // weight() rather than a fixed height: the list takes whatever the
        // other sections leave, so nothing below it is ever pushed off-screen.
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp))) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { c ->
                    CityRow(c, selected = c.id == city.id) {
                        service.update { p -> p.copy(cityId = c.id) }
                    }
                }
            }
        }

        // ---- 2. הגדרות חישוב ----
        SectionTitle("הגדרות חישוב")
        CalculationPanel(service)

        // ---- 5. הפעלה עם Windows ----
        SectionTitle("הפעלה עם Windows")
        AutostartPanel(service)
    }
}

@Composable
private fun CoordinateRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            Modifier.width(62.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            // Forced LTR on the value alone. A bare "31.7683" survives either
            // direction, but "-74.006" does not: under RTL the sign detaches
            // and renders on the wrong end, turning a west longitude into
            // something the user cannot match against their phone.
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = ZmanNumberFamily,
                textDirection = TextDirection.Ltr,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Locale.ROOT: a Hebrew system locale must not turn the decimal point into a comma. */
private fun degrees(value: Double): String = "%.4f°".format(Locale.ROOT, value)

@Composable
private fun CityRow(city: CityInfo, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ext = Ext.colors
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) ext.nextRow else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            city.nameHebrew,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = cs.onSurface,
        )
        if (city.region.isNotBlank()) {
            Text(
                city.region,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------- calculation --

/**
 * The candle-lighting offset and צאת שבת.
 *
 * These two are singled out because they are the ONLY user settings that move
 * a halachic result — everything else on this screen changes what is displayed
 * or announced. Both are read from the engine's own constants rather than
 * typed here, so the desktop cannot offer a value the phone does not have.
 */
@Composable
private fun CalculationPanel(service: DesktopZmanimService) {
    val prefs = service.prefs

    Panel {
        // --- הדלקת נרות ---
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "הדלקת נרות",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${prefs.candleLightingMinutes}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = ZmanNumberFamily,
                            textDirection = TextDirection.Ltr,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(4.dp))
                    Hint("דקות לפני השקיעה")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StepButton("−", prefs.candleLightingMinutes > CANDLE_MIN) {
                    service.update { it.copy(candleLightingMinutes = it.candleLightingMinutes - 1) }
                }
                StepButton("+", prefs.candleLightingMinutes < CANDLE_MAX) {
                    service.update { it.copy(candleLightingMinutes = it.candleLightingMinutes + 1) }
                }
            }
        }
        Hint(
            when (prefs.candleLightingMinutes) {
                MaranZmanimEngine.DEFAULT_CANDLE_OFFSET_MINUTES -> "ברירת המחדל"
                40L -> "כמנהג ירושלים"
                else -> "הגדרה אישית"
            },
        )

        Spacer(Modifier.height(4.dp))

        // --- צאת שבת ---
        Text(
            "צאת שבת",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ChipFlow {
            MaranZmanimEngine.TZEIT_SHABBAT_OPTIONS.forEach { (value, label) ->
                SmallChip(
                    // The chip carries the minutes only; the provenance half of
                    // the label ("— לוח אור החיים") is printed under the row for
                    // whichever option is actually selected, so seven chips do
                    // not turn into seven sentences.
                    label = label.substringBefore(" —"),
                    selected = prefs.tzeitShabbatMinutes == value,
                    onClick = { service.update { it.copy(tzeitShabbatMinutes = value) } },
                )
            }
        }
        Hint(
            MaranZmanimEngine.TZEIT_SHABBAT_OPTIONS
                .firstOrNull { it.first == prefs.tzeitShabbatMinutes }
                ?.second
                ?: "הגדרה אישית",
        )

        Spacer(Modifier.height(2.dp))
        Text(
            "שתי ההגדרות שכאן הן היחידות באפליקציה שמשנות תוצאה הלכתית — " +
                "כל השאר קובע רק מה מוצג. אם הן לא זהות לאלה שבטלפון, " +
                "שני המכשירים יראו זמנים שונים לאותו יום.",
            style = MaterialTheme.typography.bodySmall,
            color = Ext.colors.deadline,
        )
    }
}

/** Mirrors the bounds the Android stepper clamps to, so neither build can hold a value the other refuses. */
private const val CANDLE_MIN = 10L
private const val CANDLE_MAX = 40L

// ----------------------------------------------------------------- filters --

/** "הזמן הבא" + תזכורות — the two chip grids. The scrolling half. */
@Composable
private fun FiltersHalf(service: DesktopZmanimService, modifier: Modifier) {
    val prefs = service.prefs

    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {

        // ---- 3. "הזמן הבא" ----
        SectionTitle("\"הזמן הבא\"")
        Panel {
            Hint(
                if (prefs.nextZmanFilter.isEmpty()) "כל הזמנים נחשבים — לפי הסדר"
                else "${prefs.nextZmanFilter.size} זמנים נבחרו",
            )
            Hint(
                "צמצום הרשימה גורם לכותרת לקפוץ ישר לזמן שחשוב לך, בלי לעבור " +
                    "דרך כל הזמנים שבדרך. שום זמן לא נעלם מהרשימה הראשית.",
            )
            ZmanChipGrid(
                selected = prefs.nextZmanFilter,
                onChange = { service.update { p -> p.copy(nextZmanFilter = it) } },
                // "Empty means all" is not discoverable on its own, so it gets a
                // chip of its own that both states it and clears the selection.
                allChip = true,
            )
            Hint("בלי בחירה — כל הזמנים נחשבים.")
        }

        // ---- 4. תזכורות ----
        SectionTitle("תזכורות")
        Panel {
            Hint(
                if (prefs.reminderZmanim.isEmpty()) "אין תזכורות — ברירת המחדל"
                else "${prefs.reminderZmanim.size} תזכורות פעילות",
            )
            // Empty by default ON PURPOSE. An app that starts by interrupting
            // the user about seventeen zmanim they never asked about gets its
            // notifications switched off wholesale, and then the ones they DID
            // want are gone too. Opt-in, one at a time.
            Hint("תזכורות הן בבחירה בלבד — כברירת מחדל שום זמן לא מתריע.")
            ZmanChipGrid(
                selected = prefs.reminderZmanim,
                onChange = { service.update { p -> p.copy(reminderZmanim = it) } },
                allChip = false,
            )
            if (prefs.reminderZmanim.isNotEmpty()) {
                SmallChip("נקה הכל", selected = false) {
                    service.update { it.copy(reminderZmanim = emptySet()) }
                }
            }
            Spacer(Modifier.height(2.dp))
            // Stated plainly rather than discovered later: a desktop app cannot
            // promise delivery the way a phone alarm can, and this build is a
            // zmanim board, not an alarm clock — no sound, no snooze.
            Text(
                "תזכורת תופיע רק אם המחשב פעיל ומצב \"נא לא להפריע\" כבוי.",
                style = MaterialTheme.typography.bodySmall,
                color = Ext.colors.deadline,
            )
            Hint("התזכורת שקטה — הודעה על המסך בלבד, בלי צליל.")
        }
    }
}

/**
 * One chip per [ZmanKind].
 *
 * Labelled with [ZmanKind.hebrewName], not shortName: two pairs of kinds share
 * a shortName (both מג"א shitot for ק"ש, and both for תפילה), and a grid with
 * two identical chips — one lit, one not — is unusable. The desktop has the
 * width for the full label, so it uses it.
 */
@Composable
private fun ZmanChipGrid(
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    allChip: Boolean,
) {
    ChipFlow {
        if (allChip) {
            SmallChip("הכל", selected = selected.isEmpty()) { onChange(emptySet()) }
        }
        ZmanKind.entries.forEach { kind ->
            SmallChip(kind.hebrewName, selected = kind.name in selected) {
                val next = selected.toMutableSet()
                if (!next.add(kind.name)) next.remove(kind.name)
                onChange(next)
            }
        }
    }
}

// ------------------------------------------------------------------ shared --

@Composable
private fun AutostartPanel(service: DesktopZmanimService) {
    val prefs = service.prefs
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = prefs.startWithWindows,
                // Persist only. The HKCU\...\Run entry is written elsewhere off
                // the back of this flag, so this pane never touches the
                // registry itself.
                // TODO(desktop): wired to the Run key by StartupManager
                onCheckedChange = { on -> service.update { it.copy(startWithWindows = on) } },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "הפעל את שעון הזמנים עם הפעלת המחשב",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Hint("נדרש כדי שתזכורות יעבדו בלי לפתוח את האפליקציה ידנית.")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Hint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

/** A surface block. Not material3.Card — Card's default padding is phone-sized. */
@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        content = content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

/**
 * A chip sized for a mouse, not a thumb.
 *
 * material3.FilterChip is 32dp tall with fixed internal padding around a
 * 14sp label; twenty-one of those is a wall. This is the same affordance at
 * roughly half the height.
 */
@Composable
private fun SmallChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(5.dp)
    Box(
        Modifier.clip(shape)
            .background(if (selected) cs.primaryContainer else cs.background)
            .border(1.dp, if (selected) cs.primary else cs.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(21.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) cs.primaryContainer else cs.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) cs.onPrimaryContainer else cs.onSurfaceVariant,
        )
    }
}

/** A single-line search field. BasicTextField because OutlinedTextField is 56dp tall. */
@Composable
private fun SearchBox(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(cs.surface)
            .border(1.dp, cs.outline, shape)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = cs.onSurface),
            cursorBrush = SolidColor(cs.primary),
        )
    }
}
