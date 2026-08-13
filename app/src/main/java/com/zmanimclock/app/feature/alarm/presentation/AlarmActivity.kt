package com.zmanimclock.app.feature.alarm.presentation

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.zmanimclock.app.feature.alarm.MathChallenge
import com.zmanimclock.app.feature.alarms.data.DismissChallenge
import com.zmanimclock.app.scheduling.AlarmRingBus
import com.zmanimclock.app.scheduling.AlarmSoundService

/** Everything the ringing screen renders, captured from one intent. */
private data class AlarmUiArgs(
    val alarmId: Long,
    val title: String,
    val timeText: String,
    val snoozeMinutes: Int,
    val shabbatMode: Boolean,
    val snoozesLeft: Int,
    val challenge: DismissChallenge,
) {
    companion object {
        fun from(intent: Intent) = AlarmUiArgs(
            alarmId = intent.getLongExtra(AlarmSoundService.EXTRA_ALARM_ID, -1),
            title = intent.getStringExtra(AlarmSoundService.EXTRA_TITLE) ?: "שעון מעורר",
            timeText = intent.getStringExtra(AlarmSoundService.EXTRA_TIME_TEXT) ?: "",
            snoozeMinutes = intent.getIntExtra(AlarmSoundService.EXTRA_SNOOZE_MINUTES, 5),
            shabbatMode = intent.getBooleanExtra(AlarmSoundService.EXTRA_SHABBAT, false),
            snoozesLeft = intent.getIntExtra(AlarmSoundService.EXTRA_SNOOZES_LEFT, -1),
            challenge = intent.getStringExtra(AlarmSoundService.EXTRA_CHALLENGE)
                ?.let { runCatching { DismissChallenge.valueOf(it) }.getOrNull() }
                ?: DismissChallenge.NONE,
        )
    }
}

/**
 * Full-screen ringing UI over the lock screen — night-friendly designed
 * screen with a big אישור button.
 *
 * Two skins:
 *  - Regular: deep-night gradient, huge time, zman name.
 *  - Shabbat entry: warm sunset gradient + drawn candles — "שבת נכנסת!".
 * A math dismiss-challenge (when set) gates אישור only; snooze never.
 *
 * launchMode="singleInstance" (AndroidManifest.xml) means a SECOND alarm
 * firing while this screen is already up does not create a new Activity —
 * Android brings this instance forward and delivers the new alarm's extras
 * via onNewIntent, not onCreate. [args] is therefore live Compose state, not
 * a val captured once, so a re-fronted instance always redraws the alarm
 * that is ACTUALLY ringing rather than silently keeping the first one's
 * title, time and — critically — its dismiss challenge on screen.
 */
class AlarmActivity : ComponentActivity() {

    private lateinit var argsState: androidx.compose.runtime.MutableState<AlarmUiArgs>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnScreenOnOverLockscreen()
        argsState = androidx.compose.runtime.mutableStateOf(AlarmUiArgs.from(intent))

        setContent {
            // ZmanimTheme forces RTL and provides typography; the screen's
            // night/shabbat gradients override its surfaces entirely.
            com.zmanimclock.app.ui.theme.ZmanimTheme {
                val args by argsState

                // The service tells us when THIS alarm's ring ended for any
                // reason other than this screen's own buttons — an unattended
                // auto-silence, or a dismiss/snooze recovered from Room by a
                // freshly-restarted service instance. Without this the screen
                // used to stay up, silent and stale, until the next alarm
                // reused it (see the class doc above).
                androidx.compose.runtime.LaunchedEffect(args.alarmId) {
                    AlarmRingBus.closed.collect { endedId ->
                        if (endedId == args.alarmId) finish()
                    }
                }

                // The ring duration the user configured governs the NOISE
                // only. When it elapses the sound and vibration stop but this
                // screen stays up — otherwise a ring nobody answered erased
                // its own evidence, and someone returning to their phone found
                // nothing to say it had gone off.
                var silenced by androidx.compose.runtime.remember(args.alarmId) {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                androidx.compose.runtime.LaunchedEffect(args.alarmId) {
                    AlarmRingBus.silenced.collect { id ->
                        if (id == args.alarmId) silenced = true
                    }
                }

                AlarmScreen(
                    silenced = silenced,
                    title = args.title,
                    timeText = args.timeText,
                    snoozeMinutes = args.snoozeMinutes,
                    snoozesLeft = args.snoozesLeft,
                    challenge = args.challenge,
                    shabbatMode = args.shabbatMode,
                    onDismiss = { sendCommand(AlarmSoundService.ACTION_DISMISS, args.alarmId); finish() },
                    onSnooze = { sendCommand(AlarmSoundService.ACTION_SNOOZE, args.alarmId); finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        argsState.value = AlarmUiArgs.from(intent)
    }

    private fun sendCommand(action: String, alarmId: Long) {
        startService(Intent(this, AlarmSoundService::class.java).apply {
            this.action = action
            putExtra(AlarmSoundService.EXTRA_ALARM_ID, alarmId)
        })
    }

    private fun turnScreenOnOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService<KeyguardManager>()?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }
}

// === Ringing-screen tokens — style 1D (README §7.7 mock) + Shabbat skin ===
private val NightTop = Color(0xFF0B1220)
private val NightBottom = Color(0xFF0B1220)
private val NightButton = Color(0xFF123A8B)
private val ShabbatTop = Color(0xFF2A1233)
private val ShabbatBottom = Color(0xFF7A3B2E)
private val WarmGold = Color(0xFFF5C518)
private val SoftWhite = Color(0xFFE6EAF4)

@Composable
private fun AlarmScreen(
    /** Sound and vibration have stopped; the alarm is only awaiting אישור. */
    silenced: Boolean = false,
    title: String,
    timeText: String,
    snoozeMinutes: Int,
    snoozesLeft: Int,
    challenge: DismissChallenge,
    shabbatMode: Boolean,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    var problem by remember { mutableStateOf(MathChallenge.generate(challenge)) }
    var answerText by remember { mutableStateOf("") }
    var wrongCount by remember { mutableStateOf(0) }

    // Anti-snooze (B3): hide the snooze button when no snoozes remain — but as
    // a safety valve reveal it after 60s so a distressed user is never trapped.
    var safetyElapsed by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60_000); safetyElapsed = true
    }
    val showSnooze = snoozesLeft != 0 || safetyElapsed
    // snoozesLeft == 0 means the button is ONLY showing because the 60s
    // safety valve forced it — and AlarmSoundService.snooze() computes that
    // exact same condition (`maxSnoozes in 0..snoozeCount`) as its reason to
    // REFUSE the request and keep ringing. The valve used to reveal a button
    // that always did nothing: the screen closed (this composable's onClick
    // called finish() unconditionally) while the alarm kept blaring with no
    // full-screen control left to stop it — the opposite of what a safety
    // valve is for. Once the budget is genuinely exhausted the revealed
    // button now performs — and is labelled as — a real stop, not a snooze
    // the service will silently ignore.
    val budgetExhausted = snoozesLeft == 0

    fun tryDismiss() {
        val p = problem
        when {
            p == null -> onDismiss()
            answerText.toIntOrNull() == p.answer -> onDismiss()
            else -> {
                wrongCount++
                answerText = ""
                problem = MathChallenge.generate(challenge)
            }
        }
    }

    // Back is the reflex move of someone half-asleep trying to make the noise
    // stop. It used to finish() the Activity with zero effect on the service
    // — the alarm kept ringing with no full-screen UI left and no chance to
    // reappear, and the snooze-count reset / wake-check that a real dismiss
    // performs were both silently skipped. Route it through the SAME gated
    // path as אישור so a deliberately-set math challenge still applies.
    BackHandler(enabled = true) { tryDismiss() }

    val gradient = if (shabbatMode) {
        Brush.verticalGradient(listOf(ShabbatTop, ShabbatBottom))
    } else {
        Brush.verticalGradient(listOf(NightTop, NightBottom))
    }
    val accent = if (shabbatMode) WarmGold else SoftWhite

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (shabbatMode) {
                Candles(modifier = Modifier.size(width = 150.dp, height = 130.dp))
                Spacer(Modifier.height(18.dp))
                Text(
                    "שבת נכנסת!",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = WarmGold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "עוד מעט שקיעה — זמן להדליק נרות",
                    style = MaterialTheme.typography.titleMedium,
                    color = SoftWhite.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Alarm,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = accent,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = SoftWhite,
                    textAlign = TextAlign.Center,
                )
            }

            if (timeText.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = SoftWhite,
                )
            }

            // Says why it went quiet. Without this the screen looks identical
            // whether it is still ringing or has timed out, and a user who
            // walks up to a silent phone cannot tell which — so they cannot
            // tell whether pressing אישור is still doing anything.
            if (silenced) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "הצלצול הסתיים — ממתין לאישור",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    textAlign = TextAlign.Center,
                )
            }

            problem?.let { p ->
                Spacer(Modifier.height(24.dp))
                Text(
                    "כדי לכבות — פתור:",
                    style = MaterialTheme.typography.titleMedium,
                    color = SoftWhite.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    p.text,
                    style = MaterialTheme.typography.headlineLarge,
                    color = SoftWhite,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = answerText,
                    onValueChange = { v -> if (v.length <= 4 && v.all(Char::isDigit)) answerText = v },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("התשובה") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SoftWhite,
                        unfocusedTextColor = SoftWhite,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = SoftWhite.copy(alpha = 0.5f),
                        focusedLabelColor = accent,
                        unfocusedLabelColor = SoftWhite.copy(alpha = 0.7f),
                    ),
                )
                if (wrongCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "לא נכון — נסה שוב",
                        color = Color(0xFFFF8A80),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(Modifier.height(44.dp))
            Button(
                onClick = ::tryDismiss,
                shape = RoundedCornerShape(32.dp),
                colors = if (shabbatMode) {
                    ButtonDefaults.buttonColors(containerColor = WarmGold, contentColor = ShabbatTop)
                } else {
                    ButtonDefaults.buttonColors(containerColor = NightButton, contentColor = Color.White)
                },
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(64.dp),
            ) {
                Text("אישור", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            if (showSnooze) {
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = if (budgetExhausted) ::tryDismiss else onSnooze,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftWhite),
                ) {
                    Text(
                        when {
                            budgetExhausted -> "עצור"
                            snoozesLeft > 0 -> "נודניק ($snoozeMinutes ד' · נשארו $snoozesLeft)"
                            else -> "נודניק ($snoozeMinutes ד')" // unlimited (-1)
                        }
                    )
                }
            }
        }
    }
}

/** Two Shabbat candles with warm flames — drawn, no assets. */
@Composable
private fun Candles(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val candleWidth = w * 0.13f
        val candleTop = h * 0.42f
        val candleBottom = h * 0.98f

        listOf(w * 0.32f, w * 0.68f).forEach { cx ->
            // body
            drawRoundRect(
                color = Color(0xFFFDF6E3),
                topLeft = Offset(cx - candleWidth / 2, candleTop),
                size = androidx.compose.ui.geometry.Size(candleWidth, candleBottom - candleTop),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(candleWidth * 0.3f),
            )
            // wick
            drawLine(
                color = Color(0xFF6B5B45),
                start = Offset(cx, candleTop),
                end = Offset(cx, candleTop - h * 0.05f),
                strokeWidth = w * 0.012f,
            )
            // flame glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xCCFFC969), Color(0x00FFC969)),
                    center = Offset(cx, candleTop - h * 0.14f),
                    radius = h * 0.14f,
                ),
                radius = h * 0.14f,
                center = Offset(cx, candleTop - h * 0.14f),
            )
            // flame core
            drawOval(
                color = Color(0xFFFFB300),
                topLeft = Offset(cx - w * 0.035f, candleTop - h * 0.21f),
                size = androidx.compose.ui.geometry.Size(w * 0.07f, h * 0.16f),
            )
            drawOval(
                color = Color(0xFFFFF3C4),
                topLeft = Offset(cx - w * 0.018f, candleTop - h * 0.15f),
                size = androidx.compose.ui.geometry.Size(w * 0.036f, h * 0.09f),
            )
        }
    }
}
