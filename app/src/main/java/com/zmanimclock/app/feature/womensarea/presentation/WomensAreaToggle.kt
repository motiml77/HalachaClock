package com.zmanimclock.app.feature.womensarea.presentation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.zmanimclock.app.feature.womensarea.security.BiometricAuthResult
import com.zmanimclock.app.feature.womensarea.security.BiometricAuthenticator
import kotlinx.coroutines.launch

/**
 * The Settings switch for "איזור נשי". Turning it ON first asks for the
 * device lock (fingerprint or the phone's own PIN/password) and only then
 * calls [setEnabled] — so the tab never appears for someone who merely picked
 * up an unlocked phone and flipped the switch. Turning it OFF only hides the
 * tab; the entries stay, locked behind the same device lock.
 */
@Composable
fun rememberWomensAreaToggle(setEnabled: (Boolean) -> Unit): (Boolean) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return { enabled ->
        val activity = context as? FragmentActivity
        when {
            !enabled -> setEnabled(false)
            activity == null -> Unit
            !BiometricAuthenticator.canAuthenticate(activity) -> Toast.makeText(
                context,
                "כדי להפעיל את האיזור הנשי יש להגדיר במכשיר נעילת מסך (קוד, סיסמה או טביעת אצבע)",
                Toast.LENGTH_LONG,
            ).show()
            else -> scope.launch {
                val result = BiometricAuthenticator(activity).authenticate(
                    title = "הפעלת איזור נשי",
                    subtitle = "אימות בטביעת אצבע או בקוד המכשיר",
                )
                if (result == BiometricAuthResult.Success) setEnabled(true)
            }
        }
    }
}
