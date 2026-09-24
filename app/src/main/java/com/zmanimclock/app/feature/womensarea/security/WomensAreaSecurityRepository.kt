package com.zmanimclock.app.feature.womensarea.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A store separate from [com.zmanimclock.app.feature.settings.data.UserPreferencesRepository]
 * on purpose: that one is a `Flow` observed by dozens of screens across the
 * app, and PIN-hash-bearing state should never ride along with it. Same
 * "small, dedicated, low blast radius" reasoning already applied to this
 * feature's own color tokens and its own Room table.
 */
private val Context.womensAreaSecurityStore: DataStore<Preferences> by
    preferencesDataStore(name = "womens_area_security")

data class WomensAreaSecurity(
    /** "מצב נשי" — whether the tab is shown at all. */
    val enabled: Boolean = false,
    /** Whether a PIN has been created at least once. */
    val setupComplete: Boolean = false,
    val pinHash: String? = null,
    val biometricEnabled: Boolean = true,
)

@Singleton
class WomensAreaSecurityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    }

    val state: Flow<WomensAreaSecurity> = context.womensAreaSecurityStore.data.map { prefs ->
        WomensAreaSecurity(
            enabled = prefs[Keys.ENABLED] ?: false,
            setupComplete = prefs[Keys.SETUP_COMPLETE] ?: false,
            pinHash = prefs[Keys.PIN_HASH],
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: true,
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.womensAreaSecurityStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun savePin(pin: String) {
        context.womensAreaSecurityStore.edit {
            it[Keys.PIN_HASH] = WomensAreaPinCrypto.hash(pin)
            it[Keys.SETUP_COMPLETE] = true
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.womensAreaSecurityStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun verifyPin(pin: String): Boolean =
        state.first().pinHash?.let { WomensAreaPinCrypto.matches(pin, it) } ?: false

    /** Full reset — clears the PIN, the setup flag, and the Keystore key itself. Does not touch [enabled]. */
    suspend fun resetSecurity() {
        context.womensAreaSecurityStore.edit {
            it.remove(Keys.PIN_HASH)
            it[Keys.SETUP_COMPLETE] = false
        }
        WomensAreaPinCrypto.deleteKey()
    }
}
