package com.zmanimclock.app.feature.womensarea.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine

sealed class BiometricAuthResult {
    data object Success : BiometricAuthResult()
    data class Error(val code: Int, val message: String) : BiometricAuthResult()
    data object Cancelled : BiometricAuthResult()
}

/**
 * BIOMETRIC_STRONG only — deliberately never DEVICE_CREDENTIAL. That constant
 * would fall back to prompting the DEVICE's own lock (PIN/pattern/password),
 * which must stay separate from this feature's own PIN: the whole point is a
 * secret independent of whatever unlocks the phone generally.
 */
class BiometricAuthenticator(private val activity: FragmentActivity) {

    suspend fun authenticate(title: String, negativeButtonText: String): BiometricAuthResult =
        suspendCancellableCoroutine { cont ->
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resumeWith(Result.success(BiometricAuthResult.Success))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!cont.isActive) return
                    val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    val outcome = if (cancelled) {
                        BiometricAuthResult.Cancelled
                    } else {
                        BiometricAuthResult.Error(errorCode, errString.toString())
                    }
                    cont.resumeWith(Result.success(outcome))
                }

                // onAuthenticationFailed: one bad biometric read (not a hard
                // error) — the system dialog stays open on its own for retry,
                // nothing to do here.
            }
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setNegativeButtonText(negativeButtonText)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
            prompt.authenticate(info)
        }

    companion object {
        fun canAuthenticate(activity: FragmentActivity): Boolean =
            BiometricManager.from(activity)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }
}
