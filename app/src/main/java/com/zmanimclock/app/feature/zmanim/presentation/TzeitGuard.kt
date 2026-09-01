package com.zmanimclock.app.feature.zmanim.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.zmanimclock.app.ui.theme.Ext

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
 * How long after צאת הכוכבים (המחמירה) the guard fires by default.
 *
 * Set by the owner. The badge hangs off the stricter tzeit, and the alert is
 * a nudge for someone who still has not davened maariv — so it sits just past
 * the zman rather than on it. The custom-time picker overrides it.
 */
internal const val GUARD_OFFSET_MINUTES = 2

/**
 * [hour]:[minute] moved [byMinutes] forward, as a wall clock.
 *
 * Extracted from the dialog so the wrap can be tested: a tzeit late enough to
 * push the offset past midnight never happens in Israel, but the arithmetic
 * that would produce hour 24 is exactly the kind that survives review and then
 * throws on someone's phone abroad.
 */
internal fun shiftClock(hour: Int, minute: Int, byMinutes: Int): Pair<Int, Int> {
    val total = (hour * 60 + minute + byMinutes).mod(24 * 60)
    return total / 60 to total % 60
}

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
 * and gains a gold ring — the app's existing "this is active" accent (the
 * countdown figure, the ringing bell) — so "this is set" reads from the
 * corner of the eye without re-reading the words.
 */
@Composable
internal fun TzeitGuardBadge(armed: Boolean, onClick: () -> Unit) {
    val gold = Ext.colors.accentGold
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (armed) GuardRed else GuardRed.copy(alpha = 0.85f))
            .then(
                if (armed) Modifier.border(2.5.dp, gold, CircleShape)
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
 * the default, [GUARD_OFFSET_MINUTES] minutes past the zman, is one press and
 * done. The custom time is there for someone whose minyan is at a quarter past.
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

    val (defaultHour, defaultMinute) = shiftClock(zmanHour, zmanMinute, GUARD_OFFSET_MINUTES)
    val defaultLabel = "%02d:%02d".format(defaultHour, defaultMinute)

    var custom by remember { mutableStateOf(false) }
    var hour by remember { mutableIntStateOf(defaultHour) }
    var minute by remember { mutableIntStateOf(defaultMinute) }

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

                GuardOption(
                    "$GUARD_OFFSET_MINUTES דקות אחרי צאת הכוכבים · $defaultLabel",
                    selected = !custom,
                ) {
                    custom = false
                    hour = defaultHour
                    minute = defaultMinute
                }
                GuardOption("בשעה אחרת", selected = custom) { custom = true }

                if (custom) {
                    Spacer(Modifier.height(10.dp))
                    // One dark panel — not two separate boxes — reading
                    // "hour : minute" like an actual digital clock face:
                    // coded minute-then-hour so that in this RTL row the
                    // hour lands on the visual left and the minute on the
                    // right, which is the order asked for. Each side steps
                    // with +/- directly above/below its own digits, not
                    // beside them, so the two digit groups can sit close
                    // together instead of being pushed apart by side buttons.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Ext.colors.heroInner, RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TimeStepper("דקה", minute, 0..59) { minute = it }
                        Text(
                            ":",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                        TimeStepper("שעה", hour, 0..23) { hour = it }
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

/**
 * One digit group of the digital-clock panel: a "+" above, the value, a "−"
 * below — stepping the number in place rather than from beside it, which is
 * what let the hour and minute digits sit close together like a real clock
 * instead of being held apart by side buttons.
 */
@Composable
private fun TimeStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val ext = Ext.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ext.heroLabel)
        StepGlyph("+", ext.accentGold) { onChange(if (value >= range.last) range.first else value + 1) }
        Text(
            "%02d".format(value),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        StepGlyph("−", ext.accentGold) { onChange(if (value <= range.first) range.last else value - 1) }
    }
}

/** A compact tap target for one step — a bare glyph, not a full-size button. */
@Composable
private fun StepGlyph(symbol: String, tint: Color, onClick: () -> Unit) {
    Text(
        symbol,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 2.dp),
    )
}
