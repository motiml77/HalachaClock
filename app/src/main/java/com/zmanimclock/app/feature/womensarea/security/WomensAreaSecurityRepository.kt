package com.zmanimclock.app.feature.womensarea.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zmanimclock.app.feature.womensarea.model.SemiFixedVeset
import com.zmanimclock.app.feature.womensarea.model.WomensAreaReminderTimes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
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
    /** וסת חצי קבוע, if she set one (by her rabbi's ruling) — null when none. */
    val semiFixed: SemiFixedVeset? = null,
    /**
     * Set when the app itself cancelled a וסת חצי קבוע after
     * WomensAreaSemiFixed.CANCEL_AFTER contradicting sightings in a row: the N
     * it had, for the message she sees once and confirms.
     */
    val semiFixedCancelledMinDay: Int? = null,
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
        val SEMI_FIXED_MIN_DAY = intPreferencesKey("semi_fixed_min_day")
        val SEMI_FIXED_SINCE = longPreferencesKey("semi_fixed_since_epoch_day")
        val SEMI_FIXED_CANCELLED = intPreferencesKey("semi_fixed_cancelled_min_day")
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
            semiFixed = prefs[Keys.SEMI_FIXED_MIN_DAY]?.let { minDay ->
                SemiFixedVeset(minDay, LocalDate.ofEpochDay(prefs[Keys.SEMI_FIXED_SINCE] ?: 0L))
            },
            semiFixedCancelledMinDay = prefs[Keys.SEMI_FIXED_CANCELLED],
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

    /**
     * Sets (or, with null, removes) the וסת חצי קבוע. A new setting counts
     * contradictions only from today — see SemiFixedVeset.since. Changing N
     * keeps the original date, so an edit does not wipe a run already counting.
     */
    suspend fun setSemiFixed(minDay: Int?) {
        context.womensAreaSecurityStore.edit {
            if (minDay == null) {
                it.remove(Keys.SEMI_FIXED_MIN_DAY)
                it.remove(Keys.SEMI_FIXED_SINCE)
            } else {
                if (it[Keys.SEMI_FIXED_MIN_DAY] == null) it[Keys.SEMI_FIXED_SINCE] = LocalDate.now().toEpochDay()
                it[Keys.SEMI_FIXED_MIN_DAY] = minDay
            }
            it.remove(Keys.SEMI_FIXED_CANCELLED)
        }
    }

    /** Cancelled by the app after the contradictions in a row — remembered for the one-time message. */
    suspend fun cancelSemiFixed(minDay: Int) {
        context.womensAreaSecurityStore.edit {
            it.remove(Keys.SEMI_FIXED_MIN_DAY)
            it.remove(Keys.SEMI_FIXED_SINCE)
            it[Keys.SEMI_FIXED_CANCELLED] = minDay
        }
    }

    /** She saw the cancellation message and confirmed it. */
    suspend fun dismissSemiFixedCancelled() {
        context.womensAreaSecurityStore.edit { it.remove(Keys.SEMI_FIXED_CANCELLED) }
    }
}
