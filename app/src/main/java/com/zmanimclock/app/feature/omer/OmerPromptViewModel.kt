package com.zmanimclock.app.feature.omer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zmanimclock.app.feature.settings.data.UserPreferencesRepository
import com.zmanimclock.app.feature.zmanim.model.OmerCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Decides whether to offer the once-a-season "start counting the omer?" prompt.
 *
 * It appears when the omer season is under way, the alert is not already on,
 * and this season's prompt has not yet been answered — so a user who opens the
 * app any time from the first night onward is offered it exactly once. Answer
 * it either way and it stamps [UserPreferencesRepository.setOmerPromptedYear],
 * so it stays quiet until next year; the Settings switch remains the way to
 * change one's mind. The user's ruling drove the timing: the first count is
 * motzaei the first Yom Tov of Pesach, so the earliest this can fire is after
 * that Yom Tov is already out.
 */
@HiltViewModel
class OmerPromptViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
    private val omerAlerts: OmerAlertManager,
) : ViewModel() {

    val showPrompt: StateFlow<Boolean> =
        combine(prefsRepository.preferences, omerAlerts.enabled) { prefs, enabled ->
            val zone = ZoneId.of(prefs.timeZoneId)
            val season = OmerCount.seasonYearOrNull(LocalDate.now(zone), zone)
            season != null && !enabled && prefs.omerPromptedYear != season
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** "כן, הפעל" — turn the alert on and don't ask again this season. */
    fun enable() {
        viewModelScope.launch {
            omerAlerts.setEnabled(true)
            stampPromptedForThisSeason()
        }
    }

    /** "לא עכשיו" — leave it off, and don't ask again this season. */
    fun dismiss() {
        viewModelScope.launch { stampPromptedForThisSeason() }
    }

    private suspend fun stampPromptedForThisSeason() {
        val prefs = prefsRepository.preferences.first()
        val zone = ZoneId.of(prefs.timeZoneId)
        val today = LocalDate.now(zone)
        prefsRepository.setOmerPromptedYear(OmerCount.seasonYearOrNull(today, zone) ?: today.year)
    }
}
