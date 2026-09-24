package com.zmanimclock.app.feature.womensarea.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zmanimclock.app.feature.womensarea.model.WomensAreaReminderTimes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This feature's own settings — whether it is on, and its reminders. A store
 * separate from [com.zmanimclock.app.feature.settings.data.UserPreferencesRepository]
 * on purpose: that one is a `Flow` observed by dozens of screens across the
 * app, and this feature's state should never ride along with it. Same
 * "small, dedicated, low blast radius" reasoning already applied to this
 * feature's own color tokens and its own Room table.
 */
private val Context.womensAreaSecurityStore: DataStore<Preferences> by
    preferencesDataStore(name = "womens_area_security")

data class WomensAreaSecurity(
    /**
     * "איזור נשי" — whether the tab is shown at all. Only ever turned on after
     * the device lock was passed in Settings (see SettingsScreen).
     */
    val enabled: Boolean = false,
    /** שבעה נקיים reminders — off until she turns them on inside the area. */
    val remindersEnabled: Boolean = false,
    /** The times of day they fire at, sorted. */
    val reminderTimes: List<LocalTime> = WomensAreaReminderTimes.DEFAULT,
)

@Singleton
class WomensAreaSecurityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_TIMES = stringPreferencesKey("reminder_times")
    }

    val state: Flow<WomensAreaSecurity> = context.womensAreaSecurityStore.data.map { prefs ->
        WomensAreaSecurity(
            enabled = prefs[Keys.ENABLED] ?: false,
            remindersEnabled = prefs[Keys.REMINDERS_ENABLED] ?: false,
            reminderTimes = WomensAreaReminderTimes.decode(prefs[Keys.REMINDER_TIMES]),
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.womensAreaSecurityStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.womensAreaSecurityStore.edit { it[Keys.REMINDERS_ENABLED] = enabled }
    }

    suspend fun setReminderTimes(times: List<LocalTime>) {
        context.womensAreaSecurityStore.edit { it[Keys.REMINDER_TIMES] = WomensAreaReminderTimes.encode(times) }
    }
}
