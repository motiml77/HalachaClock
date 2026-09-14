package com.zmanimclock.app

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.feature.subscription.AccessPolicy
import com.zmanimclock.app.feature.subscription.AppAccess
import com.zmanimclock.app.feature.subscription.BillingRepository
import com.zmanimclock.app.scheduling.RescheduleWorker
import com.zmanimclock.app.scheduling.StatusNotificationReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepository: UserPreferencesRepository,
    private val billingRepository: BillingRepository,
) : ViewModel() {

    /** null while loading — the UI shows nothing until we know. */
    val isFirstLaunch: StateFlow<Boolean?> = prefsRepository.preferences
        .map { it.isFirstLaunch }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun finishOnboarding() {
        viewModelScope.launch {
            prefsRepository.setFirstLaunchDone()
            // Permissions may have just been granted — refresh the status line
            StatusNotificationReceiver.ping(context)
        }
    }

    // ---- the subscription gate ----------------------------------------------

    private val firstCheckDone = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)

    /** A Play query is in flight — the paywall shows a spinner instead of "no connection". */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Offers for the paywall. Null until Play has said what is on sale. */
    val offers = billingRepository.offers

    /**
     * What the front door shows. Every decision is AccessPolicy's; this only
     * feeds it the current inputs.
     */
    val access: StateFlow<AppAccess> = combine(
        billingRepository.entitlement,
        billingRepository.offers,
        firstCheckDone,
    ) { entitlement, offers, checked ->
        AccessPolicy.appAccess(BuildConfig.PAYWALL_ENABLED, entitlement, offers, checked)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AccessPolicy.appAccess(
            BuildConfig.PAYWALL_ENABLED,
            billingRepository.entitlement.value,
            billingRepository.offers.value,
            firstCheckDone = false,
        ),
    )

    init {
        // Re-arm the alarms the moment access is regained. While locked, the
        // scheduler disarmed every alarm (keeping the rows); the purchase
        // listener flips entitlement to YES, and without this the alarms would
        // stay silent until the next unrelated reschedule — a subscriber who
        // just paid, going to bed with no alarm set.
        viewModelScope.launch {
            var wasLocked = access.value is AppAccess.Locked
            access.collect { state ->
                val locked = state is AppAccess.Locked
                if (wasLocked && state == AppAccess.Allowed) {
                    RescheduleWorker.enqueueUnique(context)
                    StatusNotificationReceiver.ping(context)
                }
                // And the other direction: disarm now, not at tomorrow's
                // daily reschedule. (AlarmTriggerReceiver would refuse to ring
                // regardless; this makes the system "next alarm" icon and the
                // status line tell the truth immediately.)
                if (!wasLocked && locked) {
                    RescheduleWorker.enqueueUnique(context)
                    // Withdraw the status line and lock the widget now too.
                    StatusNotificationReceiver.ping(context)
                }
                wasLocked = locked
            }
        }
    }

    /**
     * Ask Play again. Called on every resume — returning from Play's checkout
     * sheet or from the manage-subscription page is a resume — and from the
     * paywall's "check again".
     */
    fun refreshEntitlement() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                // Bounded, so a wedged Play service turns into the paywall's
                // "no connection" state instead of a spinner forever.
                withTimeoutOrNull(REFRESH_TIMEOUT_MS) { billingRepository.refresh() }
            } catch (e: Exception) {
                Log.w(TAG, "entitlement refresh failed", e)
            } finally {
                firstCheckDone.value = true
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Open Play's checkout for the best offer this account can have: the free
     * trial if Play still offers it, the plain monthly plan otherwise. False
     * when Play could not open the sheet.
     */
    fun subscribe(activity: Activity): Boolean {
        val token = billingRepository.offers.value?.bestOfferToken ?: return false
        return billingRepository.launchPurchaseFlow(activity, token)
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val REFRESH_TIMEOUT_MS = 10_000L
    }
}
