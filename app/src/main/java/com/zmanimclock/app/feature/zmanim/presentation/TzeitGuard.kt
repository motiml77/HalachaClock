package com.zmanimclock.app.feature.zmanim.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.sp

/**
 * שומר לערבית — one tap on the zmanim screen arms tonight's maariv nudge.
 *
 * The whole feature is deliberately small. Everything the alarms screen can do
 * — repeat days, sound choice, volume, snooze, a dismiss challenge — is
 * absent, because this is not an alarm someone builds and keeps. It is a
 * thought at four in the afternoon: "don't let me miss maariv tonight." It
 * exists for one evening, rings for ten seconds, and removes itself.
 */
private val GuardRed = Color(0xFFD32F2F)

/**
 * A small red disc, sized to the ROW rather than to the words.
 *
 * The text is two lines of one word each — שומר over לערבית — which is what
 * lets a circle hold a two-word Hebrew phrase without either stretching into
 * a lozenge or shrinking the type below reading size. 44dp is close to the
 * height of the row's own text, so the badge sits IN the line instead of
 * pushing the row taller.
 *
 * Red, and the only red on the screen: it is the one control in the list that
 * arms something which will make a noise. Armed, the disc goes fully opaque
 * and gains a white ring, so "this is set" reads from the corner of the eye
 * without re-reading the words.
 */
@Composable
internal fun TzeitGuardBadge(armed: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (armed) GuardRed else GuardRed.copy(alpha = 0.85f))
            .then(
                if (armed) Modifier.border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "שומר\nלערבית",
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The one question the badge asks: at the zman, or at a time of your own?
 *
 * Two taps at most for the common case. The point of the badge is that arming
 * tonight's reminder should not mean a trip to the alarms screen and a form —
 * "בדיוק בצאת הכוכבים" is one press and done. The custom time is there for
 * someone whose minyan is at a quarter past.
 */
@Composable
internal fun TzeitGuardDialog(
    /** Today's tzeit, "HH:mm" — the default and the thing being safeguarded. */
    zmanTime: String,
    /** "HH:mm" when one is already armed, else null. */
    armedAt: String?,
    onArm: (hour: Int, minute: Int) -> Unit,
    onCancelGuard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val zmanHour = zmanTime.substringBefore(':').toIntOrNull() ?: 19
    val zmanMinute = zmanTime.substringAfter(':').toIntOrNull() ?: 0

    var custom by remember { mutableStateOf(false) }
    var hour by remember { mutableIntStateOf(zmanHour) }
    var minute by remember { mutableIntStateOf(zmanMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("שומר לערבית", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // Says what will happen, in the terms the owner specified,
                // rather than leaving the user to discover that it rings.
                Text(
                    "התראה חד-פעמית להיום בלבד — צלצול ורטט של " +
                        "${ZmanimViewModel.TZEIT_GUARD_RING_SECONDS} שניות, " +
                        "והמסך יישאר פתוח עד שתלחץ אישור.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))

                GuardOption("בדיוק בצאת הכוכבים · $zmanTime", selected = !custom) {
                    custom = false
                    hour = zmanHour
                    minute = zmanMinute
                }
                GuardOption("בשעה אחרת", selected = custom) { custom = true }

                if (custom) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Two steppers rather than a TimePicker: a picker
                        // dialog on top of this one would be a dialog over a
                        // dialog for two numbers.
                        TimeStepper("שעה", hour, 0..23) { hour = it }
                        Spacer(Modifier.size(12.dp))
                        TimeStepper("דקה", minute, 0..59) { minute = it }
                    }
                }

                if (armedAt != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "כרגע דרוך ל-$armedAt",
                        style = MaterialTheme.typography.bodySmall,
                        color = GuardRed,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onArm(hour, minute) }) {
                Text(if (armedAt != null) "עדכן" else "הפעל")
            }
        },
        dismissButton = {
            // Cancelling the ALERT and closing the DIALOG are different acts,
            // so they get different buttons; only one of them destroys
            // anything, and it is the only one coloured.
            if (armedAt != null) {
                TextButton(onClick = onCancelGuard) { Text("בטל התראה", color = GuardRed) }
            } else {
                TextButton(onClick = onDismiss) { Text("סגור") }
            }
        },
    )
}

@Composable
private fun GuardOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Minus / number / plus, wrapping within [range] so it can never get stuck. */
@Composable
private fun TimeStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange(if (value <= range.first) range.last else value - 1) }) {
                Text("−", fontWeight = FontWeight.Bold)
            }
            Text(
                "%02d".format(value),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = { onChange(if (value >= range.last) range.first else value + 1) }) {
                Text("+", fontWeight = FontWeight.Bold)
            }
        }
    }
}
