package com.zmanimclock.app.feature.alarm.presentation

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.zmanimclock.app.ui.theme.Rubik
import com.zmanimclock.app.ui.theme.TNUM
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.zmanimclock.app.feature.alarm.MathChallenge
import com.zmanimclock.app.feature.alarms.data.DismissChallenge
import com.zmanimclock.app.scheduling.AlarmRingBus
import com.zmanimclock.app.scheduling.AlarmSoundService
import com.zmanimclock.app.ui.OmerNightScene

/** Everything the ringing screen renders, captured from one intent. */
private data class AlarmUiArgs(
    val alarmId: Long,
    val title: String,
    val timeText: String,
    val snoozeMinutes: Int,
    val shabbatMode: Boolean,
    /** Null unless this ring is an omer count — the fixed nightly text, already resolved. */
    val omerText: String?,
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
            omerText = intent.getStringExtra(AlarmSoundService.EXTRA_OMER_TEXT),
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
 * Three skins:
 *  - Regular: deep-night gradient, huge time, zman name.
 *  - Shabbat entry: warm sunset gradient + drawn candles — "שבת נכנסת!".
 *  - Omer count: olive-gold gradient, tonight's fixed "היום ... לעומר" text.
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
                    omerText = args.omerText,
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

// === Ringing-screen tokens — design ג: slide to confirm, pulsing rings ===
private val NightTop = Color(0xFF0A1226)
private val NightBottom = Color(0xFF13234F)
private val NightInk = Color(0xFF123A8B)
private val ShabbatTop = Color(0xFF2A1233)
private val ShabbatBottom = Color(0xFF7A3B2E)
private val WarmGold = Color(0xFFF5C518)
private val SoftWhite = Color(0xFFE6EAF4)
private val MutedWhite = Color(0xFF9AA6C4)
private val OmerTop = Color(0xFF1B2410)
private val OmerBottom = Color(0xFF4C5A22)
// The same warm gold as the sheaf's own lit grain (OmerNightScene's
// grainLight) — not a separate yellow-green — so the title text reads as
// part of the same wheat, not a clashing accent next to it.
private val OmerAccent = Color(0xFFE9D170)

/** The big digits: the app's Rubik time face, tabular so they do not jitter. */
private val RingTimeStyle = TextStyle(
    fontFamily = Rubik,
    fontSize = 84.sp,
    lineHeight = 90.sp,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = TNUM,
)

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
    /** Null unless this ring is an omer count — the fixed nightly text, already resolved. */
    omerText: String?,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    // Keyed on `challenge`, not bare `remember` — [AlarmUiArgs.challenge] can
    // change UNDER this same composable instance: onNewIntent (the
    // singleInstance re-fire documented on the class above) updates
    // [argsState] without ever leaving/re-entering composition, and even a
    // single alarm's own first ring can arrive this way — the placeholder
    // notification AlarmSoundService.start() posts before its Room lookup
    // completes carries no challenge, and the real one lands moments later
    // via that same onNewIntent path once the ring() call resolves it. A bare
    // `remember` computes [problem] ONCE from whatever `challenge` this
    // composable first saw and then never again — reproduced live: firing a
    // no-challenge alarm, then a second, challenged alarm while the first was
    // still ringing, left אישור dismissing the second alarm with no problem
    // ever shown, silently defeating the gate for exactly the alarm that
    // asked for it. Keying on `challenge` regenerates the problem (and clears
    // any half-typed answer / wrong-count) the moment it actually changes,
    // while leaving the "wrong answer → new problem" reassignment inside
    // tryDismiss() untouched — that already reads the current `challenge`.
    var problem by remember(challenge) { mutableStateOf(MathChallenge.generate(challenge)) }
    var answerText by remember(challenge) { mutableStateOf("") }
    var wrongCount by remember(challenge) { mutableStateOf(0) }

    // The snooze button appears only while there is a snooze to give: -1 is
    // unlimited, >0 is a remaining budget, 0 is none.
    //
    // There used to be a 60-second "safety valve" that revealed the button
    // anyway once the budget ran out, relabelled "עצור" and wired to the same
    // ::tryDismiss as the primary button. It was a second control for exactly
    // the action אישור already performs, with the same challenge gate — two
    // buttons, one behaviour, no way for the user to tell them apart. The
    // valve was protecting against being trapped with no way to stop the
    // noise, and אישור is always on screen, so there was nothing to protect
    // against.
    val showSnooze = snoozesLeft != 0 && !silenced

    // Once the ring duration has elapsed, the challenge has done its job. It
    // exists to stop someone dismissing the alarm while half asleep and still
    // being woken by it; the noise has already run for the full time the user
    // configured, so continuing to demand arithmetic only risks trapping
    // someone behind a screen they cannot dismiss — which is a trap this
    // screen never had before it started outliving the ring.
    val challengeActive = challengeVisible(silenced, problem != null)

    fun tryDismiss() {
        if (canDismiss(silenced, problem?.answer, answerText)) {
            onDismiss()
        } else {
            wrongCount++
            answerText = ""
            problem = MathChallenge.generate(challenge)
        }
    }

    // Back is the reflex move of someone half-asleep trying to make the noise
    // stop. It used to finish() the Activity with zero effect on the service
    // — the alarm kept ringing with no full-screen UI left and no chance to
    // reappear, and the snooze-count reset / wake-check that a real dismiss
    // performs were both silently skipped. Route it through the SAME gated
    // path as אישור so a deliberately-set math challenge still applies.
    BackHandler(enabled = true) { tryDismiss() }

    val gradient = when {
        shabbatMode -> Brush.verticalGradient(listOf(ShabbatTop, ShabbatBottom))
        omerText != null -> Brush.verticalGradient(listOf(OmerTop, OmerBottom))
        else -> Brush.verticalGradient(listOf(NightTop, NightBottom))
    }
    val accent = when {
        shabbatMode -> WarmGold
        omerText != null -> OmerAccent
        else -> SoftWhite
    }
    // The slider's thumb and the ink drawn on it, per skin.
    val (thumbColor, thumbInk) = when {
        shabbatMode -> WarmGold to ShabbatTop
        omerText != null -> OmerAccent to OmerTop
        else -> Color(0xFFF4F6FB) to NightInk
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Where am I in the week — the first thing a just-woken person asks.
            Text(
                text = todayLine(),
                style = MaterialTheme.typography.labelLarge,
                color = MutedWhite,
            )

            // The middle scrolls (the challenge keyboard can take half the
            // screen); the two controls below it never move.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val minHeight = maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = minHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (shabbatMode) {
                        Candles(modifier = Modifier.size(width = 190.dp, height = 230.dp))
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
                    } else if (omerText != null) {
                        // The drawn sheaf-under-stars scene IS the special thing about
                        // this ring, exactly as the candles are for Shabbat entry.
                        OmerNightScene(modifier = Modifier.size(width = 210.dp, height = 200.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        // The fixed nightly count — the whole point of this screen.
                        Text(
                            text = omerText,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = SoftWhite,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        RingingBadge(ringing = !silenced, tint = accent)
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = SoftWhite,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.09f))
                                .padding(horizontal = 18.dp, vertical = 7.dp),
                        )
                    }

                    if (timeText.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(text = timeText, style = RingTimeStyle, color = SoftWhite)
                    }

                    // Says why it went quiet. Without this the screen looks identical
                    // whether it is still ringing or has timed out, and a user who
                    // walks up to a silent phone cannot tell which — so they cannot
                    // tell whether confirming is still doing anything.
                    if (silenced) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(accent.copy(alpha = 0.12f))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.NotificationsOff,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "הצלצול הסתיים — ממתין לאישור",
                                style = MaterialTheme.typography.titleSmall,
                                color = accent,
                            )
                        }
                    }

                    problem?.takeIf { challengeActive }?.let { p ->
                        Spacer(Modifier.height(22.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(alpha = 0.07f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "כדי לכבות — פתרו ואז החליקו:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MutedWhite,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                p.text,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = SoftWhite,
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = answerText,
                                onValueChange = { v -> if (v.length <= 4 && v.all(Char::isDigit)) answerText = v },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                                placeholder = {
                                    Text(
                                        "התשובה",
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                        color = SoftWhite.copy(alpha = 0.5f),
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.width(180.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = SoftWhite,
                                    unfocusedTextColor = SoftWhite,
                                    focusedBorderColor = accent,
                                    unfocusedBorderColor = SoftWhite.copy(alpha = 0.4f),
                                    cursorColor = accent,
                                ),
                            )
                            if (wrongCount > 0) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "לא נכון — נסו שוב",
                                    color = Color(0xFFFF8A80),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            if (showSnooze) {
                Button(
                    onClick = onSnooze,
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.10f),
                        contentColor = SoftWhite,
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Icon(Icons.Filled.Snooze, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "נודניק · $snoozeMinutes דק'",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (snoozesLeft > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "(נשארו $snoozesLeft)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedWhite,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            SlideToConfirm(
                label = "החליקו לאישור",
                thumbColor = thumbColor,
                thumbInk = thumbInk,
                onConfirmed = ::tryDismiss,
            )
        }
    }
}

/** "יום חמישי · 8.10" — the weekday and date, read off the device clock. */
private fun todayLine(): String {
    val today = java.time.LocalDate.now()
    val day = when (today.dayOfWeek) {
        java.time.DayOfWeek.SUNDAY -> "יום ראשון"
        java.time.DayOfWeek.MONDAY -> "יום שני"
        java.time.DayOfWeek.TUESDAY -> "יום שלישי"
        java.time.DayOfWeek.WEDNESDAY -> "יום רביעי"
        java.time.DayOfWeek.THURSDAY -> "יום חמישי"
        java.time.DayOfWeek.FRIDAY -> "יום שישי"
        java.time.DayOfWeek.SATURDAY -> "שבת"
    }
    return "$day · ${today.dayOfMonth}.${today.monthValue}"
}

/**
 * The alarm icon inside two rings. While it rings, waves spread out from it
 * and the clock rocks gently; once silenced everything stands still, so the
 * screen itself says whether it is still sounding.
 */
@Composable
private fun RingingBadge(ringing: Boolean, tint: Color) {
    val transition = rememberInfiniteTransition(label = "ring")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "wave",
    )
    val rock by transition.animateFloat(
        initialValue = -9f,
        targetValue = 9f,
        animationSpec = infiniteRepeatable(tween(260, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rock",
    )
    Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val outer = size.minDimension / 2f
            val inner = outer * 0.48f
            val ring = outer * 0.70f
            if (ringing) {
                // Two waves, half a cycle apart, each growing from the inner
                // ring to the edge and fading as it goes.
                listOf(wave, (wave + 0.5f) % 1f).forEach { f ->
                    drawCircle(
                        color = tint.copy(alpha = 0.30f * (1f - f)),
                        radius = inner + (outer - inner) * f,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            drawCircle(color = tint.copy(alpha = 0.16f), radius = ring, style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = Color.White.copy(alpha = 0.08f), radius = inner)
            drawCircle(color = tint.copy(alpha = 0.40f), radius = inner, style = Stroke(width = 3.dp.toPx()))
        }
        Icon(
            imageVector = Icons.Filled.Alarm,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(54.dp)
                .rotate(if (ringing) rock else 0f),
        )
    }
}

/**
 * אישור by sliding the thumb across — a deliberate gesture, so a hand that
 * brushes the screen in the dark does not end the alarm. A tap on the thumb
 * nudges it to show which way to go. Screen readers get a plain "אישור"
 * action on the whole track, since a drag is not something they can perform.
 *
 * The thumb starts at the reading start (the right, in Hebrew) and travels to
 * the end; past 80% of the way it completes, otherwise it springs back.
 */
@Composable
private fun SlideToConfirm(
    label: String,
    thumbColor: Color,
    thumbInk: Color,
    onConfirmed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val offset = remember { Animatable(0f) }
    val pulse by rememberInfiniteTransition(label = "hint").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "hint-alpha",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(34.dp))
            .semantics {
                onClick(label = "אישור") { onConfirmed(); true }
            },
    ) {
        val thumb = 58.dp
        val maxPx = with(density) { (maxWidth - thumb - 10.dp).toPx() }.coerceAtLeast(1f)
        val progress = (offset.value / maxPx).coerceIn(0f, 1f)

        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = SoftWhite.copy(alpha = pulse * (1f - progress)),
            modifier = Modifier.align(Alignment.Center),
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 5.dp)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(thumbColor)
                .clickable {
                    scope.launch {
                        offset.animateTo(maxPx * 0.22f, tween(180))
                        offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    // In Hebrew the thumb moves leftwards; flip the drag so a
                    // move toward the end always reads as positive.
                    reverseDirection = isRtl,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, maxPx)) }
                    },
                    onDragStopped = {
                        if (offset.value >= maxPx * 0.8f) {
                            offset.animateTo(maxPx, tween(120))
                            onConfirmed()
                            // Still here (a wrong challenge answer): come back.
                            offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        } else {
                            offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = thumbInk,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * Two Shabbat candles — drawn, no assets: holders on a shadowed table, a
 * layered warm glow, and a slow flicker (the two candles on different
 * periods so they never move in lockstep, which reads as fake). This scene
 * IS the special thing about this alarm — the ring itself is deliberately
 * ordinary (see the AlarmEditViewModel comment on volumePercent/gradual):
 * the owner's ruling was full volume immediately, since candle-lighting has
 * a real deadline, so the moment being warm and unmistakable was left
 * entirely to what's on screen.
 */
@Composable
private fun Candles(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "candle-flicker")
    @Composable
    fun flicker(periodMs: Int, from: Float, to: Float) = infinite.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flame",
    )
    val flickerLeft by flicker(1100, 0.90f, 1.08f)
    val flickerRight by flicker(1450, 0.93f, 1.10f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Ambient bloom behind everything — candlelight filling the room,
        // not just the flames themselves.
        val bloomCenter = Offset(w * 0.5f, h * 0.36f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x40FFC969), Color(0x00FFC969)),
                center = bloomCenter,
                radius = w * 0.9f,
            ),
            radius = w * 0.9f,
            center = bloomCenter,
        )

        val candleWidth = w * 0.16f
        val candleTop = h * 0.28f
        val candleBottom = h * 0.78f
        val holderBottom = h * 0.88f
        val tableY = h * 0.92f

        // Contact shadow on the table, grounding the whole scene.
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x4D000000), Color(0x00000000)),
                center = Offset(w * 0.5f, tableY),
                radius = w * 0.46f,
            ),
            topLeft = Offset(w * 0.06f, tableY - h * 0.035f),
            size = androidx.compose.ui.geometry.Size(w * 0.88f, h * 0.07f),
        )

        listOf(w * 0.30f to flickerLeft, w * 0.70f to flickerRight).forEach { (cx, flicker) ->
            // Brass candlestick: a wide foot and a narrow stem/cup.
            drawRoundRect(
                color = Color(0xFFC79A4B),
                topLeft = Offset(cx - candleWidth * 0.62f, holderBottom - h * 0.018f),
                size = androidx.compose.ui.geometry.Size(candleWidth * 1.24f, h * 0.03f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.012f),
            )
            drawRoundRect(
                color = Color(0xFFB8863A),
                topLeft = Offset(cx - candleWidth * 0.2f, candleBottom - h * 0.01f),
                size = androidx.compose.ui.geometry.Size(candleWidth * 0.4f, holderBottom - candleBottom),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(candleWidth * 0.08f),
            )

            // Candle body.
            drawRoundRect(
                color = Color(0xFFFDF6E3),
                topLeft = Offset(cx - candleWidth / 2, candleTop),
                size = androidx.compose.ui.geometry.Size(candleWidth, candleBottom - candleTop),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(candleWidth * 0.22f),
            )
            // A soft highlight down one side, for roundness rather than a flat bar.
            drawRoundRect(
                color = Color(0x52FFFFFF),
                topLeft = Offset(cx - candleWidth * 0.3f, candleTop + h * 0.015f),
                size = androidx.compose.ui.geometry.Size(
                    candleWidth * 0.2f,
                    (candleBottom - candleTop) - h * 0.03f,
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(candleWidth * 0.1f),
            )

            // Wick.
            drawLine(
                color = Color(0xFF4A3B2A),
                start = Offset(cx, candleTop),
                end = Offset(cx, candleTop - h * 0.032f),
                strokeWidth = w * 0.01f,
                cap = StrokeCap.Round,
            )

            val flameBase = Offset(cx, candleTop - h * 0.09f)
            // Outer halo — the part that "flickers" most visibly.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xB3FFC969), Color(0x00FFC969)),
                    center = flameBase,
                    radius = h * 0.16f * flicker,
                ),
                radius = h * 0.16f * flicker,
                center = flameBase,
            )
            // Flame body, teardrop-ish from three stacked ovals: deep orange
            // base, golden middle, near-white hot tip.
            drawOval(
                color = Color(0xFFFF8A1F),
                topLeft = Offset(
                    cx - w * 0.044f * flicker,
                    candleTop - h * 0.145f * flicker,
                ),
                size = androidx.compose.ui.geometry.Size(w * 0.088f * flicker, h * 0.155f * flicker),
            )
            drawOval(
                color = Color(0xFFFFC94D),
                topLeft = Offset(
                    cx - w * 0.027f * flicker,
                    candleTop - h * 0.122f * flicker,
                ),
                size = androidx.compose.ui.geometry.Size(w * 0.054f * flicker, h * 0.10f * flicker),
            )
            drawOval(
                color = Color(0xFFFFF7E0),
                topLeft = Offset(
                    cx - w * 0.012f * flicker,
                    candleTop - h * 0.088f * flicker,
                ),
                size = androidx.compose.ui.geometry.Size(w * 0.024f * flicker, h * 0.045f * flicker),
            )
        }
    }
}
