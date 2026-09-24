package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.womensarea.security.BiometricAuthResult
import com.zmanimclock.app.feature.womensarea.security.BiometricAuthenticator
import com.zmanimclock.app.ui.WomensAreaLilac
import kotlinx.coroutines.launch

/** The lock screen shown every time the Women's Area is entered in a fresh session. */
@Composable
fun WomensAreaGateScreen(
    onUnlocked: () -> Unit,
    viewModel: WomensAreaGateViewModel = hiltViewModel(),
) {
    val security by viewModel.security.collectAsStateWithLifecycle()
    val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
    val pinError by viewModel.pinError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()

    LaunchedEffect(unlocked) {
        if (unlocked) onUnlocked()
    }

    var pin by remember { mutableStateOf("") }
    val biometricAvailable = remember(activity) { activity != null && BiometricAuthenticator.canAuthenticate(activity) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = WomensAreaLilac)
        Spacer(Modifier.height(12.dp))
        Text("איזור נשי", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        if (biometricAvailable && security.biometricEnabled) {
            Button(
                onClick = {
                    scope.launch {
                        val result = BiometricAuthenticator(activity!!)
                            .authenticate(title = "אימות לאיזור נשי", negativeButtonText = "ביטול")
                        if (result == BiometricAuthResult.Success) viewModel.unlock()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  אימות באמצעות טביעת אצבע")
            }
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
            label = { Text("קוד") },
            singleLine = true,
            isError = pinError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        pinError?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { viewModel.tryPin(pin) },
            enabled = pin.length == 4,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("אישור")
        }
    }
}
