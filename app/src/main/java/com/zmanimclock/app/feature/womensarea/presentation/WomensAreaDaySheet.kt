package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zmanimclock.app.feature.womensarea.data.WomensAreaEntryEntity
import com.zmanimclock.app.feature.womensarea.data.WomensAreaEntryType
import com.zmanimclock.app.feature.womensarea.model.Onah
import com.zmanimclock.app.feature.womensarea.model.VesetKind
import com.zmanimclock.app.feature.womensarea.model.WomensAreaCalculator
import com.zmanimclock.app.feature.womensarea.model.WomensAreaLabels
import com.zmanimclock.app.feature.womensarea.model.WomensAreaLabels.hebrewName
import com.zmanimclock.app.feature.womensarea.security.WomensAreaReminders
import com.zmanimclock.app.ui.OnWomensAreaLilacContainer
import com.zmanimclock.app.ui.WomensAreaCleanGreen
import com.zmanimclock.app.ui.WomensAreaLilac
import com.zmanimclock.app.ui.WomensAreaLilacContainer
import com.zmanimclock.app.ui.WomensAreaPrishaRed
import com.zmanimclock.app.ui.WomensAreaTevilaBlue
import java.time.LocalDate

private enum class Step { CHOOSE, VESET, HEFSEK }

/**
 * What opens when a day on the calendar is tapped (or "רישום להיום"): a
 * bottom sheet, in the same style as the Alarms tab's "new alarm" sheet.
 *
 *   1. CHOOSE  — התחלת ווסת / הפסק טהרה, and what is already recorded here.
 *   2a. VESET  — ביום or בלילה (required: שמירה stays disabled until chosen),
 *                the ⓘ note on the Hebrew day, and a preview of the separation
 *                days that will be marked.
 *   2b. HEFSEK — the 7 clean days and the tevila it gives, and the reminder
 *                choices, each off until she turns it on.
 *
 * WHICH DAYS CAN TAKE WHICH ENTRY
 * A veset can be recorded up to tomorrow's Hebrew date: tonight after shkia
 * already IS tomorrow's date (ליל …), and its cell is tomorrow's. A hefsek is
 * made before shkia, so only a day that has already begun — today or earlier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WomensAreaDaySheet(
    date: LocalDate,
    today: LocalDate,
    entriesOnDay: List<WomensAreaEntryEntity>,
    /** The latest veset before [date], for the haflaga in the preview. */
    previousVeset: LocalDate?,
    savedReminders: WomensAreaReminders,
    onSaveVeset: (Onah) -> Unit,
    onSaveHefsek: (WomensAreaReminders) -> Unit,
    onDelete: (WomensAreaEntryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(Step.CHOOSE) }
    var onah by remember { mutableStateOf<Onah?>(null) }
    var reminders by remember { mutableStateOf(savedReminders) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(date = date, onBack = if (step == Step.CHOOSE) null else ({ step = Step.CHOOSE }))
            when (step) {
                Step.CHOOSE -> ChooseStep(
                    date = date,
                    today = today,
                    entriesOnDay = entriesOnDay,
                    onVeset = { step = Step.VESET },
                    onHefsek = { step = Step.HEFSEK },
                    onDelete = onDelete,
                )
                Step.VESET -> VesetStep(
                    date = date,
                    onah = onah,
                    previousVeset = previousVeset,
                    onOnahChange = { onah = it },
                    onSave = { onah?.let(onSaveVeset) },
                )
                Step.HEFSEK -> HefsekStep(
                    date = date,
                    reminders = reminders,
                    onRemindersChange = { reminders = it },
                    onSave = { onSaveHefsek(reminders) },
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(date: LocalDate, onBack: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
            }
        }
        Column {
            Text("יום ${WomensAreaLabels.weekdayName(date)}", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            Text(WomensAreaLabels.hebrewDate(date), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "${date.dayOfMonth}.${date.monthValue}.${date.year}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------ step 1

@Composable
private fun ChooseStep(
    date: LocalDate,
    today: LocalDate,
    entriesOnDay: List<WomensAreaEntryEntity>,
    onVeset: () -> Unit,
    onHefsek: () -> Unit,
    onDelete: (WomensAreaEntryEntity) -> Unit,
) {
    val canVeset = date <= today.plusDays(1)
    val canHefsek = date <= today
    Text("מה לרשום ביום זה?", style = MaterialTheme.typography.titleMedium)
    OptionCard(
        icon = Icons.Filled.WaterDrop,
        tint = OnWomensAreaLilacContainer,
        tile = WomensAreaLilacContainer,
        title = "התחלת ווסת",
        subtitle = if (canVeset) "הראייה התחילה ביום זה — ביום או בלילה" else "היום הזה עוד לא הגיע",
        enabled = canVeset,
        onClick = onVeset,
    )
    OptionCard(
        icon = Icons.Outlined.TaskAlt,
        tint = WomensAreaCleanGreen,
        tile = WomensAreaCleanGreen.copy(alpha = 0.14f),
        title = "הפסק טהרה",
        subtitle = if (canHefsek) "נעשה ביום זה, לפני השקיעה" else "אפשר לרשום רק מהיום ואחורה",
        enabled = canHefsek,
        onClick = onHefsek,
    )
    if (entriesOnDay.isNotEmpty()) {
        Text("רשום ביום זה", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        entriesOnDay.forEach { RecordedRow(it, onDelete = { onDelete(it) }) }
    }
}

/** A big, tappable option — the Alarms tab's TypeCard, with a chevron and a disabled state. */
@Composable
private fun OptionCard(
    icon: ImageVector,
    tint: Color,
    tile: Color,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface, disabledContainerColor = cs.surface),
        border = BorderStroke(1.dp, cs.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (enabled) tint else cs.outline, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) cs.onSurface else cs.outline,
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            if (enabled) {
                // "Go in" is ArrowRight in LTR; AutoMirrored turns it to
                // point left under the app's RTL — forward, in Hebrew.
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant)
            }
        }
    }
}

/** An entry already on this day, with a delete that asks once before it deletes. */
@Composable
private fun RecordedRow(entry: WomensAreaEntryEntity, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var confirming by remember(entry.id) { mutableStateOf(false) }
    val label = when (entry.type) {
        WomensAreaEntryType.PERIOD_START -> "התחלת ווסת" + (entry.onah?.let { " · ${it.hebrewName}" } ?: "")
        WomensAreaEntryType.HEFSEK_TAHARA -> "הפסק טהרה"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surfaceContainerHigh)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (confirming) "למחוק את \"$label\"?" else label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (confirming) {
            TextButton(onClick = { confirming = false }) { Text("לא") }
            TextButton(onClick = onDelete) { Text("מחיקה", color = cs.error) }
        } else {
            IconButton(onClick = { confirming = true }) {
                Icon(Icons.Outlined.Delete, contentDescription = "מחיקה", tint = cs.onSurfaceVariant)
            }
        }
    }
}

// ----------------------------------------------------------------- step 2a

@Composable
private fun VesetStep(
    date: LocalDate,
    onah: Onah?,
    previousVeset: LocalDate?,
    onOnahChange: (Onah) -> Unit,
    onSave: () -> Unit,
) {
    Text("התחלת ווסת — מתי התחילה הראייה?", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OnahTile(Icons.Filled.WbSunny, "ביום", "עד השקיעה", onah == Onah.DAY, { onOnahChange(Onah.DAY) }, Modifier.weight(1f))
        OnahTile(Icons.Filled.NightsStay, "בלילה", "אחרי השקיעה", onah == Onah.NIGHT, { onOnahChange(Onah.NIGHT) }, Modifier.weight(1f))
    }
    onah?.let {
        Text(
            WomensAreaLabels.onahTiming(date, it),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
    HebrewDayInfoNote()
    onah?.let { PrishaPreview(date, it, previousVeset) }
    SaveButton(enabled = onah != null, label = if (onah == null) "יש לבחור ביום או בלילה" else "שמירה", onClick = onSave)
}

@Composable
private fun OnahTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) WomensAreaLilacContainer else cs.surface),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) WomensAreaLilac else cs.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) WomensAreaLilac else cs.onSurfaceVariant, modifier = Modifier.size(30.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

/** The separation days this veset will mark, before she saves it. */
@Composable
private fun PrishaPreview(date: LocalDate, onah: Onah, previousVeset: LocalDate?) {
    val prediction = WomensAreaCalculator.predict(date, onah, previousVeset)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("ימי הפרישה שיסומנו בלוח:", style = MaterialTheme.typography.labelLarge)
        prediction.prishaDays.forEach { day ->
            val title = if (day.kind == VesetKind.HAFLAGA) {
                "${day.kind.hebrewName} (${prediction.haflagaInterval} יום)"
            } else {
                day.kind.hebrewName
            }
            FramedBlock(WomensAreaPrishaRed) {
                Text("$title · יום ${day.dayNumber}", fontWeight = FontWeight.Bold, color = WomensAreaPrishaRed)
                Text(WomensAreaLabels.onahTiming(day.date, onah), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (prediction.haflaga == null) {
            Text(
                "הפלגה: אין ראייה קודמת רשומה לחשב ממנה.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (prediction.yomHachodeshMissing) {
            Text(
                "יום החודש: הראייה בל׳, ולחודש הבא אין ל׳ — יש לברר עם רב.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ----------------------------------------------------------------- step 2b

@Composable
private fun HefsekStep(
    date: LocalDate,
    reminders: WomensAreaReminders,
    onRemindersChange: (WomensAreaReminders) -> Unit,
    onSave: () -> Unit,
) {
    Text("הפסק טהרה — ${WomensAreaLabels.hefsekTiming(date)}", style = MaterialTheme.typography.titleMedium)
    TaharaDetails(date)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text("התראות", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "לבחירתך — אפשר גם בלי. אפשר לשנות אחר כך במסך הראשי.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ReminderChoices(reminders, onRemindersChange)
    SaveButton(enabled = true, label = "שמירה", onClick = onSave)
}

/** The 7 clean days in a green frame and the tevila in a blue one — used here and on the main screen. */
@Composable
internal fun TaharaDetails(hefsek: LocalDate) {
    val clean = WomensAreaCalculator.cleanDayDates(hefsek)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FramedBlock(WomensAreaCleanGreen) {
            Text("שבעה נקיים", fontWeight = FontWeight.Bold, color = WomensAreaCleanGreen)
            Text(
                "מיום ${WomensAreaLabels.weekdayName(clean.first())} ${WomensAreaLabels.hebrewDayAndMonth(clean.first())} " +
                    "(${WomensAreaLabels.gregorianShort(clean.first())}) עד יום " +
                    "${WomensAreaLabels.weekdayName(clean.last())} ${WomensAreaLabels.hebrewDayAndMonth(clean.last())} " +
                    "(${WomensAreaLabels.gregorianShort(clean.last())})",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        FramedBlock(WomensAreaTevilaBlue) {
            Text("★ טבילה", fontWeight = FontWeight.Bold, color = WomensAreaTevilaBlue)
            Text(WomensAreaLabels.tevilaTiming(WomensAreaCalculator.tevilaDay(hefsek)), style = MaterialTheme.typography.bodySmall)
            Text("טובלים רק לאחר צאת הכוכבים — לא לפני.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun FramedBlock(color: Color, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, color, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { content() }
}

@Composable
private fun SaveButton(enabled: Boolean, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = WomensAreaLilac),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
