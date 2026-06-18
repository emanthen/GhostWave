package com.ghostwave.app.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

// Default number of one-time prekeys to generate per refresh batch.
// Signal protocol recommends keeping ~100 prekeys uploaded so that
// multiple simultaneous session initiations can succeed without waiting.
private const val PREKEY_BATCH_SIZE = 100

/**
 * Wraps all libsignal-android cryptographic operations.
 *
 * Cryptographic decisions:
 * ─────────────────────────────────────────────────────────────────────────
 * IDENTITY KEY PAIR
 *   Curve25519 (X25519 for DH, Ed25519 for signatures).
 *   Generated once on first launch; encrypted and stored by IdentityRepository.
 *   We use libsignal's own generation (backed by libsodium) rather than
 *   Android Keystore's EC keys because Keystore does not support Curve25519.
 *   The private key is wrapped by a hardware-backed AES-256-GCM Keystore key
 *   (see KeyStoreManager) so it is effectively hardware-protected.
 *
 * REGISTRATION ID
 *   Random integer 1–16380. In Signal Protocol this identifies a device within
 *   a multi-device account. In GhostWave's single-device model it is still
 *   required by the protocol store interface.
 *
 * PREKEYS (one-time)
 *   Curve25519 ephemeral key pairs. Consumed one-per-session by X3DH.
 *   Generated in batches; step 3 persists them in the Room-backed SignalStore.
 *
 * SIGNED PREKEY
 *   Medium-term Curve25519 key signed by the identity key (Ed25519 signature).
 *   Allows session initiators to authenticate the prekey bundle without the
 *   identity key being online. Rotated every ~30 days.
 * ─────────────────────────────────────────────────────────────────────────
 *
 * All methods use [Dispatchers.Default] — crypto is CPU-bound, not I/O-bound,
 * so Default (not IO) is the correct dispatcher.
 */
@Singleton
class SignalProtocolManager @Inject constructor() {

    /**
     * Generates a new Curve25519 identity key pair.
     *
     * SECURITY: The returned private key bytes must be encrypted immediately
     * via [KeyStoreManager.encrypt] before persisting. Never log, serialize to
     * plaintext, or pass across process boundaries.
     */
    suspend fun generateIdentityKeyPair(): IdentityKeyPair =
        withContext(Dispatchers.Default) {
            IdentityKeyPair.generate()
        }

    /**
     * Generates a random registration ID (range 1–16380, never 0).
     * [extended] = false keeps the range within the original Signal spec.
     */
    suspend fun generateRegistrationId(): Int =
        withContext(Dispatchers.Default) {
            SecureRandom().nextInt(16380) + 1
        }

    /**
     * Generates [count] one-time prekeys starting at [startId].
     *
     * Prekeys are uploaded to the IPFS DHT as part of the prekey bundle
     * (Step 5). Each prekey is consumed by at most one X3DH session
     * initiation; once used it must not be reused (doing so would break
     * forward secrecy).
     */
    suspend fun generatePreKeys(
        startId: Int = 1,
        count:   Int = PREKEY_BATCH_SIZE,
    ): List<PreKeyRecord> =
        withContext(Dispatchers.Default) {
            (startId until startId + count).map { id ->
                PreKeyRecord(id, Curve.generateKeyPair())
            }
        }

    /**
     * Generates a signed prekey.
     *
     * The prekey's public key is signed with the identity private key
     * (Ed25519). Verifying peers run:
     *   identityKey.publicKey.verifySignature(signedPreKey.publicKey.serialize(), signedPreKey.signature)
     *
     * The timestamp is recorded so the key can be rotated based on age
     * (Step 5 implements rotation logic).
     *
     * @param identityKeyPair  The identity key pair whose private key signs the prekey.
     * @param id               Unique ID for this signed prekey; incremented on each rotation.
     */
    suspend fun generateSignedPreKey(
        identityKeyPair: IdentityKeyPair,
        id:              Int,
    ): SignedPreKeyRecord =
        withContext(Dispatchers.Default) {
            val keyPair   = Curve.generateKeyPair()
            val signature = Curve.calculateSignature(
                identityKeyPair.privateKey,
                keyPair.publicKey.serialize(),
            )
            SignedPreKeyRecord(id, System.currentTimeMillis(), keyPair, signature)
        }

    /**
     * Serialises an [IdentityKeyPair] to bytes for encrypted storage.
     * The caller MUST zero [result] after encrypting it.
     */
    fun serializeIdentityKeyPair(keyPair: IdentityKeyPair): ByteArray =
        keyPair.serialize()

    /**
     * Deserialises an [IdentityKeyPair] from bytes retrieved from encrypted storage.
     * The caller MUST zero [bytes] after this call returns.
     */
    fun deserializeIdentityKeyPair(bytes: ByteArray): IdentityKeyPair =
        IdentityKeyPair(bytes)
}
