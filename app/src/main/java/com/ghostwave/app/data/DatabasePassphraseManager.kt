package com.ghostwave.app.data

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ghostwave.app.BuildConfig
import com.ghostwave.app.crypto.EncryptedBlob
import com.ghostwave.app.crypto.KeyStoreManager
import kotlinx.coroutines.flow.first
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the SQLCipher database passphrase lifecycle.
 *
 * Passphrase model:
 * ──────────────────────────────────────────────────────────────────────────
 * 1. On first launch, generate a 32-byte cryptographically random passphrase.
 * 2. Encrypt it with the AES-256-GCM key at [KEY_ALIAS] (hardware-backed Keystore).
 * 3. Persist the (IV, ciphertext) pair as Base64 strings in DataStore.
 * 4. On every subsequent launch, decrypt from DataStore using the Keystore key.
 * 5. Hand the decrypted passphrase to SQLCipher's SupportFactory.
 * 6. ZERO the passphrase bytes immediately after the SupportFactory is built
 *    (SQLCipher copies them internally — it does not hold our reference).
 *
 * Why not derive the passphrase directly from the Keystore key?
 *   The Keystore AES key is non-extractable — we cannot get its raw bytes.
 *   Encrypting a separate random passphrase is the standard approach used by
 *   SQLCipher for Android documentation and the Signal reference app.
 * ──────────────────────────────────────────────────────────────────────────
 */
@Singleton
class DatabasePassphraseManager @Inject constructor(
    private val dataStore:       DataStore<Preferences>,
    private val keyStoreManager: KeyStoreManager,
) {
    private val KEY_ALIAS  = BuildConfig.KEYSTORE_KEY_ALIAS  // "ghostwave_db_master_key"
    private val KEY_PASS_IV = stringPreferencesKey("db_passphrase_iv")
    private val KEY_PASS_CT = stringPreferencesKey("db_passphrase_ct")

    /**
     * Returns the database passphrase, creating and persisting it on first call.
     *
     * Caller MUST zero the returned [ByteArray] immediately after passing it
     * to SQLCipher's SupportFactory. SQLCipher makes an internal copy and
     * does not retain the reference.
     */
    suspend fun getOrCreatePassphrase(): ByteArray {
        val prefs = dataStore.data.first()
        val ivB64 = prefs[KEY_PASS_IV]
        val ctB64 = prefs[KEY_PASS_CT]

        if (ivB64 != null && ctB64 != null) {
            // Existing passphrase: decrypt from DataStore
            val blob = EncryptedBlob(
                iv         = Base64.decode(ivB64, Base64.NO_WRAP),
                ciphertext = Base64.decode(ctB64, Base64.NO_WRAP),
            )
            return keyStoreManager.decrypt(KEY_ALIAS, blob)
        }

        // First launch: generate a fresh 32-byte random passphrase
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }

        val blob = keyStoreManager.encrypt(KEY_ALIAS, passphrase)
        dataStore.edit { p ->
            p[KEY_PASS_IV] = Base64.encodeToString(blob.iv,         Base64.NO_WRAP)
            p[KEY_PASS_CT] = Base64.encodeToString(blob.ciphertext, Base64.NO_WRAP)
        }

        return passphrase
    }

    /**
     * Called during "Re-register" nuclear option.
     * Removes the encrypted passphrase from DataStore; the Keystore key is
     * deleted separately by [IdentityRepository.deleteIdentity].
     * After this the database file is permanently unreadable.
     */
    suspend fun deletePassphrase() {
        dataStore.edit { p ->
            p.remove(KEY_PASS_IV)
            p.remove(KEY_PASS_CT)
        }
    }
}
