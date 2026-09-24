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

    /** Marks the session unlocked — after the device lock (fingerprint or phone PIN) was passed. */
    fun unlock() {
        _unlocked.value = true
    }

    /** Called on ON_STOP (app backgrounded) — see WomensAreaHostScreen. */
    fun lock() {
        _unlocked.value = false
    }
}
