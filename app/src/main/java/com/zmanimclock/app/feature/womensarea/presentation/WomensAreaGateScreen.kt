package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.zmanimclock.app.feature.womensarea.security.BiometricAuthResult
import com.zmanimclock.app.feature.womensarea.security.BiometricAuthenticator
import com.zmanimclock.app.ui.WomensAreaLilac
import com.zmanimclock.app.ui.WomensAreaSpringIcon
import kotlinx.coroutines.launch

/**
 * The lock screen shown every time the Women's Area is entered in a fresh
 * session. It opens the device-lock prompt by itself on arrival, and offers a
 * button to try again after a cancel.
 */
@Composable
fun WomensAreaGateScreen(onUnlocked: () -> Unit) {
    val activity = LocalContext.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    val available = remember(activity) { activity != null && BiometricAuthenticator.canAuthenticate(activity) }
    var error by remember { mutableStateOf<String?>(null) }

    fun prompt() {
        val host = activity ?: return
        scope.launch {
            when (val result = BiometricAuthenticator(host).authenticate(
                title = "כניסה לאיזור נשי",
                subtitle = "אימות בטביעת אצבע או בקוד המכשיר",
            )) {
                BiometricAuthResult.Success -> onUnlocked()
                BiometricAuthResult.Cancelled -> error = null
                is BiometricAuthResult.Error -> error = result.message
            }
        }
    }

    LaunchedEffect(available) { if (available) prompt() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(WomensAreaSpringIcon, contentDescription = null, modifier = Modifier.size(56.dp), tint = WomensAreaLilac)
        Spacer(Modifier.height(12.dp))
        Text("איזור נשי", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        if (available) {
            Button(
                onClick = ::prompt,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WomensAreaLilac),
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  פתיחה בטביעת אצבע או בקוד המכשיר")
            }
        } else {
            Text(
                "כדי להיכנס יש להגדיר במכשיר נעילת מסך (קוד, סיסמה או טביעת אצבע).",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
