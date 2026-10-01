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
    /** A change the app made to it on its own (uprooted / established again), until she confirms the message. */
    val semiFixedNotice: SemiFixedNotice? = null,
)

/** The one-time messages about a וסת חצי קבוע. */
enum class SemiFixedNotice { UPROOTED, REESTABLISHED }

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
        val SEMI_FIXED_UPROOTED_AT = longPreferencesKey("semi_fixed_uprooted_at_epoch_day")
        val SEMI_FIXED_NOTICE = stringPreferencesKey("semi_fixed_notice")
        // The last change the vesets made on their own, so deleting the veset
        // that made it puts back what was there before it.
        val SEMI_FIXED_CHANGED_BY = longPreferencesKey("semi_fixed_changed_by_epoch_day")
        val SEMI_FIXED_PREV_SINCE = longPreferencesKey("semi_fixed_prev_since_epoch_day")
        val SEMI_FIXED_PREV_UPROOTED_AT = longPreferencesKey("semi_fixed_prev_uprooted_at_epoch_day")
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
                SemiFixedVeset(
                    minDay = minDay,
                    since = LocalDate.ofEpochDay(prefs[Keys.SEMI_FIXED_SINCE] ?: 0L),
                    uprootedAt = prefs[Keys.SEMI_FIXED_UPROOTED_AT]?.let(LocalDate::ofEpochDay),
                )
            },
            semiFixedNotice = prefs[Keys.SEMI_FIXED_NOTICE]?.let { name ->
                SemiFixedNotice.entries.firstOrNull { it.name == name }
            },
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
     * Sets (or, with null, removes) the וסת חצי קבוע. A new one counts
     * sightings only from today — see SemiFixedVeset.since. Changing N keeps
     * the rest (its date, and whether it is uprooted), so an edit does not
     * wipe a run already counting.
     */
    suspend fun setSemiFixed(minDay: Int?) {
        context.womensAreaSecurityStore.edit {
            if (minDay == null) {
                it.remove(Keys.SEMI_FIXED_MIN_DAY)
                it.remove(Keys.SEMI_FIXED_SINCE)
                it.remove(Keys.SEMI_FIXED_UPROOTED_AT)
                it.remove(Keys.SEMI_FIXED_CHANGED_BY)
            } else {
                if (it[Keys.SEMI_FIXED_MIN_DAY] == null) it[Keys.SEMI_FIXED_SINCE] = LocalDate.now().toEpochDay()
                it[Keys.SEMI_FIXED_MIN_DAY] = minDay
            }
            it.remove(Keys.SEMI_FIXED_NOTICE)
        }
    }

    /**
     * Saves where the vesets took it on their own — uprooted, or established
     * again (WomensAreaSemiFixed.after) — with the message she sees once.
     */
    suspend fun saveSemiFixedChange(before: SemiFixedVeset, updated: SemiFixedVeset, changedBy: LocalDate, notice: SemiFixedNotice) {
        context.womensAreaSecurityStore.edit {
            if (it[Keys.SEMI_FIXED_MIN_DAY] == null) return@edit // removed meanwhile
            it[Keys.SEMI_FIXED_CHANGED_BY] = changedBy.toEpochDay()
            it[Keys.SEMI_FIXED_PREV_SINCE] = before.since.toEpochDay()
            val prevUprooted = before.uprootedAt
            if (prevUprooted == null) it.remove(Keys.SEMI_FIXED_PREV_UPROOTED_AT)
            else it[Keys.SEMI_FIXED_PREV_UPROOTED_AT] = prevUprooted.toEpochDay()
            it[Keys.SEMI_FIXED_SINCE] = updated.since.toEpochDay()
            val uprootedAt = updated.uprootedAt
            if (uprootedAt == null) it.remove(Keys.SEMI_FIXED_UPROOTED_AT)
            else it[Keys.SEMI_FIXED_UPROOTED_AT] = uprootedAt.toEpochDay()
            it[Keys.SEMI_FIXED_NOTICE] = notice.name
        }
    }

    /**
     * The veset on [date] was deleted or moved: if it was the one that just
     * uprooted the וסת חצי קבוע or established it again, that change is
     * undone — the calculations go back to what they were before that veset.
     */
    suspend fun undoSemiFixedChangeBy(date: LocalDate) {
        context.womensAreaSecurityStore.edit {
            if (it[Keys.SEMI_FIXED_CHANGED_BY] != date.toEpochDay()) return@edit
            it[Keys.SEMI_FIXED_PREV_SINCE]?.let { since -> it[Keys.SEMI_FIXED_SINCE] = since }
            val prevUprooted = it[Keys.SEMI_FIXED_PREV_UPROOTED_AT]
            if (prevUprooted == null) it.remove(Keys.SEMI_FIXED_UPROOTED_AT) else it[Keys.SEMI_FIXED_UPROOTED_AT] = prevUprooted
            it.remove(Keys.SEMI_FIXED_CHANGED_BY)
            it.remove(Keys.SEMI_FIXED_PREV_SINCE)
            it.remove(Keys.SEMI_FIXED_PREV_UPROOTED_AT)
            it.remove(Keys.SEMI_FIXED_NOTICE)
        }
    }

    /** She saw the message and confirmed it. */
    suspend fun dismissSemiFixedNotice() {
        context.womensAreaSecurityStore.edit { it.remove(Keys.SEMI_FIXED_NOTICE) }
    }
}
