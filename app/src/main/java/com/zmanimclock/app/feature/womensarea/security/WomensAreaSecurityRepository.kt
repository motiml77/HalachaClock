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

/**
 * The reminders she chose — each kind off until she turns it on, then at the
 * times of day she picked. Asked for right where she records a הפסק טהרה,
 * and editable on the area's main screen.
 */
data class WomensAreaReminders(
    /** On each of the 7 clean days. */
    val cleanEnabled: Boolean = false,
    val cleanTimes: List<LocalTime> = WomensAreaReminderTimes.DEFAULT,
    /** On the evening of the 7th day, before its tzeit. */
    val tevilaEnabled: Boolean = false,
    val tevilaTimes: List<LocalTime> = WomensAreaReminderTimes.TEVILA_DEFAULT,
)

data class WomensAreaSecurity(
    /**
     * "איזור נשי" — whether the tab is shown at all. Only ever turned on after
     * the device lock was passed in Settings (see SettingsScreen).
     */
    val enabled: Boolean = false,
    val reminders: WomensAreaReminders = WomensAreaReminders(),
)

@Singleton
class WomensAreaSecurityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_TIMES = stringPreferencesKey("reminder_times")
        val TEVILA_REMINDER_ENABLED = booleanPreferencesKey("tevila_reminder_enabled")
        val TEVILA_REMINDER_TIMES = stringPreferencesKey("tevila_reminder_times")
    }

    val state: Flow<WomensAreaSecurity> = context.womensAreaSecurityStore.data.map { prefs ->
        WomensAreaSecurity(
            enabled = prefs[Keys.ENABLED] ?: false,
            reminders = WomensAreaReminders(
                cleanEnabled = prefs[Keys.REMINDERS_ENABLED] ?: false,
                cleanTimes = WomensAreaReminderTimes.decode(prefs[Keys.REMINDER_TIMES]),
                tevilaEnabled = prefs[Keys.TEVILA_REMINDER_ENABLED] ?: false,
                tevilaTimes = WomensAreaReminderTimes.decode(
                    prefs[Keys.TEVILA_REMINDER_TIMES],
                    default = WomensAreaReminderTimes.TEVILA_DEFAULT,
                ),
            ),
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.womensAreaSecurityStore.edit { it[Keys.ENABLED] = enabled }
    }

    /** All four reminder settings in one write, so a reader never sees half a change. */
    suspend fun setReminders(reminders: WomensAreaReminders) {
        context.womensAreaSecurityStore.edit {
            it[Keys.REMINDERS_ENABLED] = reminders.cleanEnabled
            it[Keys.REMINDER_TIMES] = WomensAreaReminderTimes.encode(reminders.cleanTimes)
            it[Keys.TEVILA_REMINDER_ENABLED] = reminders.tevilaEnabled
            it[Keys.TEVILA_REMINDER_TIMES] = WomensAreaReminderTimes.encode(reminders.tevilaTimes)
        }
    }
}
