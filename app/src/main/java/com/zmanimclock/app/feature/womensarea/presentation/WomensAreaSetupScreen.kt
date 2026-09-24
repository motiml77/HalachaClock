package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zmanimclock.app.feature.womensarea.security.BiometricAuthenticator
import com.zmanimclock.app.ui.WomensAreaLilac

/**
 * First-time setup for the Women's Area's security gate — mirrors
 * [com.zmanimclock.app.feature.onboarding.OnboardingScreen]'s own
 * icon+headline+description+card-list+bottom-button shape and its
 * live-recheck-on-resume pattern (she may leave to enroll a fingerprint in
 * system Settings and come back).
 */
@Composable
fun WomensAreaSetupScreen(
    onDone: () -> Unit,
    viewModel: WomensAreaSetupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    var refreshTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val biometricAvailable = remember(refreshTick) {
        activity != null && BiometricAuthenticator.canAuthenticate(activity)
    }

    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var biometricEnabled by remember(biometricAvailable) { mutableStateOf(biometricAvailable) }

    val validationError = viewModel.validationError(pin, confirmPin)
    val canSave = viewModel.canSave(pin, confirmPin)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = WomensAreaLilac)
        Text("הגדרת גישה לאיזור נשי", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            "קוד בן 4 ספרות, נפרד מהקוד הרגיל של הטלפון. אפשר גם טביעת אצבע, בנוסף.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                    label = { Text("קוד") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) confirmPin = it },
                    label = { Text("אימות קוד") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = validationError != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                validationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (biometricAvailable) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("טביעת אצבע", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "כניסה מהירה, בנוסף לקוד",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Switch(checked = biometricEnabled, onCheckedChange = { biometricEnabled = it })
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.save(pin, biometricEnabled, onDone) },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (canSave) Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (canSave) "  שמירה" else "שמירה")
        }
    }
}
