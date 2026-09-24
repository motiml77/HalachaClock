package com.zmanimclock.app.feature.womensarea.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zmanimclock.app.feature.womensarea.security.WomensAreaSecurity
import com.zmanimclock.app.feature.womensarea.security.WomensAreaSecurityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WomensAreaGateViewModel @Inject constructor(
    private val securityRepository: WomensAreaSecurityRepository,
) : ViewModel() {

    val security: StateFlow<WomensAreaSecurity> = securityRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WomensAreaSecurity())

    /**
     * Process-scoped only — never persisted, and deliberately reset whenever
     * this ViewModel is recreated (the host re-locks on app backgrounding by
     * disposing/recreating this ViewModel's owner; see WomensAreaHostScreen).
     */
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    /** Marks the session unlocked — after a successful biometric prompt, PIN check, or fresh PIN setup. */
    fun unlock() {
        _unlocked.value = true
    }

    /** Called on ON_STOP (app backgrounded) — see WomensAreaHostScreen. */
    fun lock() {
        _unlocked.value = false
    }

    fun tryPin(pin: String) {
        viewModelScope.launch {
            val ok = securityRepository.verifyPin(pin)
            _pinError.value = if (ok) null else "קוד שגוי"
            if (ok) unlock()
        }
    }
}
