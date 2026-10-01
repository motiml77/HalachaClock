package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zmanimclock.app.feature.womensarea.model.PrishaDay
import com.zmanimclock.app.feature.womensarea.model.WomensAreaLabels
import com.zmanimclock.app.feature.womensarea.model.WomensAreaSemiFixed
import com.zmanimclock.app.ui.OnWomensAreaLilacContainer
import com.zmanimclock.app.ui.WomensAreaLilac
import com.zmanimclock.app.ui.WomensAreaLilacContainer
import com.zmanimclock.app.ui.WomensAreaPrishaRed

/**
 * וסת חצי קבוע on the main screen: a button to set it while there is none;
 * once set, its N with עריכה / ביטול — and, when the latest veset came before
 * day N, the warning that this month shows every separation day.
 */
@Composable
internal fun SemiFixedCard(ui: SemiFixedUi, onSave: (Int) -> Unit, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    val sf = ui.semiFixed

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(1.dp, WomensAreaLilac.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(WomensAreaLilacContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.DateRange, contentDescription = null, tint = WomensAreaLilac, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("וסת חצי קבוע", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (sf == null) "לא הוגדר · מגדירים רק לפי הוראת רב"
                        else "מוגדר: אינה רואה לפני יום ${sf.minDay}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }

            if (sf != null && ui.status?.latestContradicts == true) {
                SemiFixedWarning(
                    WomensAreaLabels.semiFixedContradiction(
                        ui.status.latestInterval,
                        sf.minDay,
                        ui.status.consecutiveContradictions,
                    ),
                )
            } else if (sf != null) {
                Text(
                    "ימי פרישה שחלים לפני יום ${sf.minDay} אינם מוצגים בלוח.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (sf == null) {
                Button(
                    onClick = { editing = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WomensAreaLilac),
                ) { Text("הגדרת וסת חצי קבוע") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Text("עריכה")
                    }
                    OutlinedButton(onClick = { removing = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Text("ביטול ההגדרה")
                    }
                }
            }
        }
    }

    if (editing) {
        SemiFixedDialog(
            initial = sf?.minDay
                ?: ui.shortestHaflaga?.coerceIn(WomensAreaSemiFixed.MIN_DAY, WomensAreaSemiFixed.MAX_DAY)
                ?: 30,
            shortestHaflaga = ui.shortestHaflaga,
            onSave = { onSave(it); editing = false },
            onDismiss = { editing = false },
        )
    }
    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text("ביטול וסת חצי קבוע") },
            text = { Text("לאחר הביטול יוצגו בלוח כל ימי הפרישה.") },
            confirmButton = { TextButton(onClick = { onRemove(); removing = false }) { Text("ביטול ההגדרה") } },
            dismissButton = { TextButton(onClick = { removing = false }) { Text("חזרה") } },
        )
    }
}

/**
 * Where she sets N — with the method spelled out, live for the N chosen,
 * since what is hidden depends on it (the עונה בינונית only past day 30).
 */
@Composable
private fun SemiFixedDialog(initial: Int, shortestHaflaga: Int?, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var n by remember { mutableIntStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.DateRange, contentDescription = null, tint = WomensAreaLilac) },
        title = { Text("הגדרת וסת חצי קבוע", textAlign = TextAlign.Center) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("אינה רואה לפני יום:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    FilledTonalIconButton(
                        onClick = { n = (n - 1).coerceAtLeast(WomensAreaSemiFixed.MIN_DAY) },
                        enabled = n > WomensAreaSemiFixed.MIN_DAY,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = WomensAreaLilacContainer),
                    ) { Icon(Icons.Filled.Remove, contentDescription = "פחות") }
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp).size(64.dp).clip(CircleShape).background(WomensAreaLilac),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$n", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    FilledTonalIconButton(
                        onClick = { n = (n + 1).coerceAtMost(WomensAreaSemiFixed.MAX_DAY) },
                        enabled = n < WomensAreaSemiFixed.MAX_DAY,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = WomensAreaLilacContainer),
                    ) { Icon(Icons.Filled.Add, contentDescription = "יותר") }
                }
                shortestHaflaga?.let {
                    Text(
                        "ההפלגה הקצרה ביותר בהיסטוריה: $it יום",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WomensAreaLilacContainer)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("לפי השיטה הזו:", fontWeight = FontWeight.Bold, color = OnWomensAreaLilacContainer)
                    WomensAreaLabels.semiFixedMethod(n).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = OnWomensAreaLilacContainer)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(n) }) { Text("שמירה") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/** The red warning: a sighting before day N. */
@Composable
internal fun SemiFixedWarning(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WomensAreaPrishaRed.copy(alpha = 0.08f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = WomensAreaPrishaRed, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = WomensAreaPrishaRed)
    }
}

/** What the וסת חצי קבוע leaves out of the calendar — said, not silently dropped. */
@Composable
internal fun SemiFixedHiddenNote(minDay: Int, hidden: List<PrishaDay>) {
    Text(
        WomensAreaLabels.semiFixedHiddenLine(minDay, hidden),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Once, after the app cancelled it: three sightings in a row before day N. */
@Composable
internal fun SemiFixedCancelledDialog(minDay: Int, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = WomensAreaPrishaRed) },
        title = { Text("הוסת החצי קבוע בוטל", textAlign = TextAlign.Center) },
        text = { Text(WomensAreaLabels.semiFixedCancelled(minDay)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("הבנתי") } },
    )
}

