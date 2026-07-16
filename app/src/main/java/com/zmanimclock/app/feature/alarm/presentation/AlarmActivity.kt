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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.zmanimclock.app.scheduling.AlarmSoundService
import com.zmanimclock.app.ui.theme.ZmanimTheme

/**
 * Full-screen ringing UI, shown over the lock screen (launched by the
 * full-screen-intent notification of [AlarmSoundService]).
 *
 * Stateless by design: it only renders the extras it was given and sends
 * dismiss/snooze commands back to the service.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnScreenOnOverLockscreen()

        val title = intent.getStringExtra(AlarmSoundService.EXTRA_TITLE) ?: "זמן הלכתי"
        val timeText = intent.getStringExtra(AlarmSoundService.EXTRA_TIME_TEXT) ?: ""
        val alertId = intent.getLongExtra(AlarmSoundService.EXTRA_ALERT_ID, -1)
        val snoozeMinutes = intent.getIntExtra(AlarmSoundService.EXTRA_SNOOZE_MINUTES, 5)

        setContent {
            ZmanimTheme {
                AlarmScreen(
                    title = title,
                    timeText = timeText,
                    snoozeMinutes = snoozeMinutes,
                    onDismiss = { sendCommand(AlarmSoundService.ACTION_DISMISS, alertId); finish() },
                    onSnooze = { sendCommand(AlarmSoundService.ACTION_SNOOZE, alertId, snoozeMinutes); finish() },
                )
            }
        }
    }

    private fun sendCommand(action: String, alertId: Long, snoozeMinutes: Int? = null) {
        startService(Intent(this, AlarmSoundService::class.java).apply {
            this.action = action
            putExtra(AlarmSoundService.EXTRA_ALERT_ID, alertId)
            snoozeMinutes?.let { putExtra(AlarmSoundService.EXTRA_SNOOZE_MINUTES, it) }
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
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
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
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
            if (timeText.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(text = timeText, style = MaterialTheme.typography.displayLarge)
            }
            Spacer(Modifier.height(48.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                Button(onClick = onDismiss) {
                    Text("ביטול")
                }
                Spacer(Modifier.width(24.dp))
                OutlinedButton(onClick = onSnooze) {
                    Text("נודניק ($snoozeMinutes ד')")
                }
            }
        }
    }
}
