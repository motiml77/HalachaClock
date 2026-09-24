package com.zmanimclock.app.feature.womensarea.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zmanimclock.app.feature.womensarea.security.WomensAreaSecurityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PIN_LENGTH = 4

@HiltViewModel
class WomensAreaSetupViewModel @Inject constructor(
    private val securityRepository: WomensAreaSecurityRepository,
) : ViewModel() {

    /** null = no error yet; non-null = shown under the confirm field. */
    fun validationError(pin: String, confirmPin: String): String? = when {
        pin.length < PIN_LENGTH -> null // not an error mid-typing, just not ready
        confirmPin.isEmpty() -> null
        pin != confirmPin -> "הקודים אינם תואמים"
        else -> null
    }

    fun canSave(pin: String, confirmPin: String): Boolean =
        pin.length == PIN_LENGTH && pin == confirmPin

    fun save(pin: String, biometricEnabled: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            securityRepository.savePin(pin)
            securityRepository.setBiometricEnabled(biometricEnabled)
            securityRepository.setEnabled(true)
            onDone()
        }
    }
}
