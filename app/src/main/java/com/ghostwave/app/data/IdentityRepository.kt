package com.ghostwave.app.data

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ghostwave.app.BuildConfig
import com.ghostwave.app.crypto.EncryptedBlob
import com.ghostwave.app.crypto.KeyStoreManager
import com.ghostwave.app.crypto.SignalProtocolManager
import com.ghostwave.app.util.GhostWaveIdUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.IdentityKeyPair
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the local device identity.
 *
 * Persistence model:
 * ──────────────────────────────────────────────────────────────────────────
 * The Signal IdentityKeyPair.serialize() byte array is encrypted with an
 * AES-256-GCM key that lives in the Android Keystore (hardware-backed,
 * non-extractable). The resulting (IV, ciphertext) pair is stored in
 * DataStore as Base64 strings.
 *
 * Why DataStore and not EncryptedSharedPreferences?
 *   - DataStore is Kotlin-coroutine-native and type-safe.
 *   - We do our own encryption (Keystore-AES) so the DataStore layer does
 *     not need to be encrypted itself.
 *   - EncryptedSharedPreferences uses AES-256-SIV internally, but its
 *     underlying Keystore key is not hardware-backed on all API levels.
 *
 * Security invariants:
 *   - Private key bytes are ALWAYS zeroed after encryption or after
 *     deserialisation and use.
 *   - The IdentityKeyPair object is never written to logs.
 *   - Backup is explicitly disabled (AndroidManifest allowBackup=false +
 *     data_extraction_rules.xml).
 * ──────────────────────────────────────────────────────────────────────────
 */
@Singleton
class IdentityRepository @Inject constructor(
    private val dataStore:           DataStore<Preferences>,
    private val keyStoreManager:     KeyStoreManager,
    private val signalManager:       SignalProtocolManager,
) {

    // ── DataStore preference keys ────────────────────────────────────────

    private object Keys {
        // Encrypted identity key pair (Base64-encoded IV + ciphertext)
        val IDENTITY_KP_IV  = stringPreferencesKey("identity_kp_iv")
        val IDENTITY_KP_CT  = stringPreferencesKey("identity_kp_ct")

        val REGISTRATION_ID     = intPreferencesKey("registration_id")
        val DISPLAY_NAME        = stringPreferencesKey("display_name")
        val GW_ID               = stringPreferencesKey("gw_id")

        // Counter for signed prekey rotation (incremented each rotation)
        val SIGNED_PREKEY_ID    = intPreferencesKey("signed_prekey_id")
    }

    // The Keystore alias for the AES key that wraps the Signal identity private key
    private val KEY_ALIAS = BuildConfig.SIGNAL_STORE_KEY_ALIAS

    // ── Public API ───────────────────────────────────────────────────────

    /** Returns true if a complete identity has been generated and stored. */
    suspend fun hasIdentity(): Boolean =
        dataStore.data.first()[Keys.GW_ID] != null

    /**
     * Creates and persists a new identity.
     *
     * Steps performed:
     *   1. Generate Curve25519 identity key pair
     *   2. Generate registration ID
     *   3. Derive GW-ID from public key
     *   4. Encrypt private key material with Keystore AES key
     *   5. Persist encrypted blob + metadata to DataStore
     *   6. Zero plaintext key bytes immediately after encryption
     */
    suspend fun createAndSaveIdentity(displayName: String): String =
        withContext(Dispatchers.Default) {
            val keyPair        = signalManager.generateIdentityKeyPair()
            val registrationId = signalManager.generateRegistrationId()
            val ghostWaveId    = GhostWaveIdUtil.deriveFromPublicKey(
                keyPair.publicKey.publicKey.serialize()
            )

            // Serialise → encrypt → zero plaintext immediately
            val serialized = signalManager.serializeIdentityKeyPair(keyPair)
            val blob       = keyStoreManager.encrypt(KEY_ALIAS, serialized)
            serialized.fill(0)   // SECURITY: wipe plaintext from heap

            dataStore.edit { prefs ->
                prefs[Keys.IDENTITY_KP_IV]  = Base64.encodeToString(blob.iv,         Base64.NO_WRAP)
                prefs[Keys.IDENTITY_KP_CT]  = Base64.encodeToString(blob.ciphertext, Base64.NO_WRAP)
                prefs[Keys.REGISTRATION_ID] = registrationId
                prefs[Keys.DISPLAY_NAME]    = displayName.trim()
                prefs[Keys.GW_ID]           = ghostWaveId
                prefs[Keys.SIGNED_PREKEY_ID] = 1
            }

            ghostWaveId
        }

    /**
     * Loads and decrypts the identity key pair.
     *
     * Returns null if no identity exists yet (i.e. first launch before setup).
     *
     * SECURITY: The IdentityKeyPair holds private key bytes. Callers must
     * use it promptly and not retain strong references across suspend points.
     * Zero the bytes from [SignalProtocolManager.serializeIdentityKeyPair]
     * if you re-serialise for any reason.
     */
    suspend fun getIdentityKeyPair(): IdentityKeyPair? =
        withContext(Dispatchers.Default) {
            val prefs = dataStore.data.first()

            val ivB64 = prefs[Keys.IDENTITY_KP_IV]  ?: return@withContext null
            val ctB64 = prefs[Keys.IDENTITY_KP_CT]  ?: return@withContext null

            val blob = EncryptedBlob(
                iv         = Base64.decode(ivB64, Base64.NO_WRAP),
                ciphertext = Base64.decode(ctB64, Base64.NO_WRAP),
            )

            val decrypted = keyStoreManager.decrypt(KEY_ALIAS, blob)
            val keyPair   = signalManager.deserializeIdentityKeyPair(decrypted)
            decrypted.fill(0)   // SECURITY: wipe plaintext from heap

            keyPair
        }

    suspend fun getRegistrationId(): Int =
        dataStore.data.first()[Keys.REGISTRATION_ID] ?: 0

    suspend fun getDisplayName(): String? =
        dataStore.data.first()[Keys.DISPLAY_NAME]

    suspend fun getGhostWaveId(): String? =
        dataStore.data.first()[Keys.GW_ID]

    /**
     * Increments and returns the signed prekey ID counter.
     * Called by SignalProtocolManager when rotating the signed prekey (~30 days).
     */
    suspend fun nextSignedPreKeyId(): Int {
        val current = dataStore.data.first()[Keys.SIGNED_PREKEY_ID] ?: 1
        val next    = current + 1
        dataStore.edit { it[Keys.SIGNED_PREKEY_ID] = next }
        return current
    }

    /**
     * NUCLEAR OPTION — called from Settings > Security > Re-register.
     *
     * Permanently deletes:
     *   - The Keystore AES wrapping key (makes encrypted blobs unreadable)
     *   - All DataStore preferences
     *
     * After this call the device has no identity and will boot to the
     * onboarding screen. All message history and contacts must also be
     * cleared by the caller (RoomDatabase.clearAllTables + IPFS cache wipe).
     */
    suspend fun deleteIdentity() {
        keyStoreManager.deleteKey(KEY_ALIAS)
        dataStore.edit { it.clear() }
    }
}
