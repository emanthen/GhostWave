package com.ghostwave.app.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts and decrypts messages using the Signal Double Ratchet algorithm.
 *
 * Double Ratchet properties:
 * ─────────────────────────────────────────────────────────────────────────
 * Forward secrecy:  Each message uses a fresh derived key. Compromising the
 *                   current state doesn't reveal past messages.
 * Break-in recovery: After a compromise, key material "heals" as the ratchet
 *                    advances with fresh Diffie-Hellman contributions.
 * Out-of-order:     Messages can be decrypted in any order within a window.
 * ─────────────────────────────────────────────────────────────────────────
 *
 * Wire format:
 *   First message in a session → PreKeySignalMessage (contains X3DH headers)
 *   Subsequent messages        → SignalMessage (compact Double Ratchet only)
 *
 * All operations run on [Dispatchers.Default] — CPU-bound crypto.
 */
@Singleton
class MessageEncryptor @Inject constructor(
    private val signalStore: GhostWaveSignalProtocolStore,
) {

    /**
     * Encrypts [plaintext] for the peer identified by [peerGwId].
     * Returns raw ciphertext bytes ready for P2P transmission.
     */
    suspend fun encrypt(peerGwId: String, plaintext: ByteArray): ByteArray =
        withContext(Dispatchers.Default) {
            val address = SignalProtocolAddress(peerGwId, 1)
            val cipher  = SessionCipher(signalStore, address)
            cipher.encrypt(plaintext).serialize()
        }

    /**
     * Decrypts [ciphertext] bytes received from [senderGwId].
     * Automatically detects PreKeySignalMessage vs SignalMessage.
     */
    suspend fun decrypt(senderGwId: String, ciphertext: ByteArray): ByteArray =
        withContext(Dispatchers.Default) {
            val address = SignalProtocolAddress(senderGwId, 1)
            val cipher  = SessionCipher(signalStore, address)

            // Detect message type from the version byte at ciphertext[0]
            when (ciphertext[0].toInt() and 0xFF) {
                CiphertextMessage.PREKEY_TYPE   ->
                    cipher.decrypt(PreKeySignalMessage(ciphertext))
                CiphertextMessage.WHISPER_TYPE  ->
                    cipher.decrypt(SignalMessage(ciphertext))
                else ->
                    throw IllegalArgumentException("Unknown ciphertext type: ${ciphertext[0]}")
            }
        }

    /** Returns true if a Signal session exists with this peer. */
    fun hasSession(peerGwId: String): Boolean =
        signalStore.containsSession(SignalProtocolAddress(peerGwId, 1))
}
