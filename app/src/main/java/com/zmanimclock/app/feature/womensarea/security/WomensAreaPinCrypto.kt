package com.zmanimclock.app.feature.womensarea.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Device-bound keyed hash of the Women's Area PIN. The PIN itself is never
 * persisted anywhere — only this MAC, computed with a non-exportable
 * AndroidKeyStore HMAC key. That key never leaves secure hardware and can't
 * be read out even from a rooted phone's app storage, unlike a manually
 * salted hash stored in plain prefs, which could be brute-forced offline at
 * unlimited speed given the stored salt.
 *
 * [KeyGenParameterSpec] deliberately does NOT set `setUserAuthenticationRequired` —
 * this key must not depend on the device's own lock/biometric state, only on
 * this app's own PIN flow, since the whole point is a secret independent of
 * the device's general unlock.
 *
 * Not using androidx.security:security-crypto/EncryptedSharedPreferences: it
 * has stayed in alpha for years with known issues on some Android versions —
 * the same alpha-avoidance already applied to the billing dependency
 * elsewhere in this project.
 */
object WomensAreaPinCrypto {
    private const val ALIAS = "womens_area_pin_hmac_key"
    private const val KEYSTORE = "AndroidKeyStore"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return generator.generateKey()
    }

    fun hash(pin: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(getOrCreateKey()) }
        return Base64.encodeToString(mac.doFinal(pin.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    /** Constant-time compare (via [MessageDigest.isEqual]) to avoid a timing side-channel. */
    fun matches(pin: String, storedHash: String): Boolean =
        MessageDigest.isEqual(hash(pin).toByteArray(Charsets.UTF_8), storedHash.toByteArray(Charsets.UTF_8))

    /** Called when the PIN is reset/removed — drops the Keystore key so a stale hash can never verify again. */
    fun deleteKey() {
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
    }
}
