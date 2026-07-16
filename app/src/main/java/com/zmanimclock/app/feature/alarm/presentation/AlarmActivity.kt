package com.zmanimclock.app.feature.alarm.presentation

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.zmanimclock.app.feature.alarm.MathChallenge
import com.zmanimclock.app.feature.alarms.data.DismissChallenge
import com.zmanimclock.app.scheduling.AlarmSoundService
import com.zmanimclock.app.ui.theme.ZmanimTheme

/**
 * Full-screen ringing UI over the lock screen.
 *
 * The אישור (acknowledge) button is the primary action. When the alarm has a
 * math dismiss-challenge, אישור unlocks only after a correct answer (a wrong
 * one generates a fresh problem). Snooze is NEVER gated — a groggy user must
 * always have a safe way out.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnScreenOnOverLockscreen()

        val title = intent.getStringExtra(AlarmSoundService.EXTRA_TITLE) ?: "שעון מעורר"
        val timeText = intent.getStringExtra(AlarmSoundService.EXTRA_TIME_TEXT) ?: ""
        val alarmId = intent.getLongExtra(AlarmSoundService.EXTRA_ALARM_ID, -1)
        val snoozeMinutes = intent.getIntExtra(AlarmSoundService.EXTRA_SNOOZE_MINUTES, 5)
        val challenge = intent.getStringExtra(AlarmSoundService.EXTRA_CHALLENGE)
            ?.let { runCatching { DismissChallenge.valueOf(it) }.getOrNull() }
            ?: DismissChallenge.NONE

        setContent {
            ZmanimTheme {
                AlarmScreen(
                    title = title,
                    timeText = timeText,
                    snoozeMinutes = snoozeMinutes,
                    challenge = challenge,
                    onDismiss = { sendCommand(AlarmSoundService.ACTION_DISMISS, alarmId); finish() },
                    onSnooze = { sendCommand(AlarmSoundService.ACTION_SNOOZE, alarmId); finish() },
                )
            }
        }
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

@Composable
private fun AlarmScreen(
    title: String,
    timeText: String,
    snoozeMinutes: Int,
    challenge: DismissChallenge,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    var problem by remember { mutableStateOf(MathChallenge.generate(challenge)) }
    var answerText by remember { mutableStateOf("") }
    var wrongCount by remember { mutableStateOf(0) }

    fun tryDismiss() {
        val p = problem
        if (p == null) {
            onDismiss()
        } else if (answerText.toIntOrNull() == p.answer) {
            onDismiss()
        } else {
            wrongCount++
            answerText = ""
            problem = MathChallenge.generate(challenge)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Alarm,
                contentDescription = null,
                modifier = Modifier.size(88.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(20.dp))
            Text(text = title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (timeText.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(text = timeText, style = MaterialTheme.typography.displayLarge)
            }

            problem?.let { p ->
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "כדי לכבות — פתור:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.height(8.dp))
                Text(text = p.text, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = answerText,
                    onValueChange = { v -> if (v.length <= 4 && v.all(Char::isDigit)) answerText = v },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("התשובה") },
                )
                if (wrongCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "לא נכון — נסה שוב",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                Button(onClick = ::tryDismiss) {
                    Text("אישור", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(24.dp))
                // Snooze is intentionally never gated by the challenge
                OutlinedButton(onClick = onSnooze) {
                    Text("נודניק ($snoozeMinutes ד')")
                }
            }
        }
    }
}
