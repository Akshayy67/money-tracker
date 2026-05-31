package com.aimoneytracker.data.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the SQLCipher passphrase for the encrypted database (§21). A random 256-bit key is
 * generated on first run and stored in [EncryptedSharedPreferences] (hardware-backed where available).
 * Also stores the app PIN hash.
 */
@Singleton
class DatabaseKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** The DB passphrase as raw bytes, created once and reused. */
    fun databasePassphrase(): ByteArray {
        val existing = prefs.getString(KEY_DB, null)
        if (existing != null) return Base64.decode(existing, Base64.NO_WRAP)
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_DB, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
        return key
    }

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN, hash(pin)).apply()
    }

    fun verifyPin(pin: String): Boolean = prefs.getString(KEY_PIN, null) == hash(pin)

    fun hasPin(): Boolean = prefs.getString(KEY_PIN, null) != null

    private fun hash(pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(("aimt:$pin").toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_DB = "db_passphrase"
        private const val KEY_PIN = "app_pin_hash"
    }
}
