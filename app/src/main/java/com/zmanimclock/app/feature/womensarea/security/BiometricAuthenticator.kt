package com.zmanimclock.app.feature.womensarea.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
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
 * Unlocks with the DEVICE's own lock: a fingerprint/face, or the phone's own
 * PIN, pattern or password. There is no separate code for this feature — the
 * owner asked for exactly the lock she already uses on the phone.
 *
 * The allowed set depends on the API level: BIOMETRIC_STRONG | DEVICE_CREDENTIAL
 * is unsupported on API 28-29 (androidx.biometric rejects it there), so below
 * API 30 it is BIOMETRIC_WEAK | DEVICE_CREDENTIAL instead. With
 * DEVICE_CREDENTIAL allowed, the prompt must NOT be given a negative button —
 * the system supplies "use PIN" itself, and setting one throws.
 */
class BiometricAuthenticator(private val activity: FragmentActivity) {

    suspend fun authenticate(title: String, subtitle: String? = null): BiometricAuthResult =
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
                .apply { subtitle?.let { setSubtitle(it) } }
                .setAllowedAuthenticators(ALLOWED)
                .build()
            prompt.authenticate(info)
        }

    companion object {
        private val ALLOWED: Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
            else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

        /** False when the phone has no screen lock at all — there is then nothing to unlock with. */
        fun canAuthenticate(activity: FragmentActivity): Boolean =
            BiometricManager.from(activity).canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
