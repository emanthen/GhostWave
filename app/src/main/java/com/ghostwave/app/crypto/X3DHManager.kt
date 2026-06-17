package com.ghostwave.app.crypto

import android.util.Base64
import com.ghostwave.app.data.IdentityRepository
import com.ghostwave.app.data.db.SignalPreKeyDao
import com.ghostwave.app.data.db.SignalSignedPreKeyDao
import com.ghostwave.app.data.model.SignalPreKey
import com.ghostwave.app.data.model.SignalSignedPreKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.state.PreKeyBundle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages X3DH (Extended Triple Diffie-Hellman) session initiation.
 *
 * X3DH protocol flow (Signal spec):
 * ──────────────────────────────────────────────────────────────────────────
 * INITIATOR (Alice):
 *   1. Fetches Bob's prekey bundle from IPFS DHT
 *      { IK_B, SPK_B, sig(SPK_B), OPK_B? }
 *   2. Generates ephemeral key EK_A
 *   3. Computes:
 *      DH1 = DH(IK_A, SPK_B)
 *      DH2 = DH(EK_A, IK_B)
 *      DH3 = DH(EK_A, SPK_B)
 *      DH4 = DH(EK_A, OPK_B)   ← optional
 *      SK  = KDF(DH1 || DH2 || DH3 || DH4)
 *   4. Sends initial message: { IK_A, EK_A, OPK_B_id?, ciphertext }
 *
 * RESPONDER (Bob):
 *   1. Receives initial message
 *   2. Looks up OPK_B by ID in local store
 *   3. Recomputes SK using the same DH operations
 *   4. Establishes Double Ratchet session with SK
 *   5. Deletes the consumed OPK_B
 *
 * libsignal handles all of this internally via SessionBuilder.
 * We provide the PreKeyBundle; libsignal does the rest.
 * ──────────────────────────────────────────────────────────────────────────
 */
@Singleton
class X3DHManager @Inject constructor(
    private val signalProtocolStore: GhostWaveSignalProtocolStore,
    private val signalManager:       SignalProtocolManager,
    private val identityRepository:  IdentityRepository,
    private val preKeyDao:           SignalPreKeyDao,
    private val signedPreKeyDao:     SignalSignedPreKeyDao,
) {

    /**
     * Initiates an X3DH session with a remote peer.
     *
     * [bundle] is fetched from the peer's IPFS DHT entry in [PrekeyBundleManager].
     * After this call, the session is stored in [GhostWaveSignalProtocolStore]
     * and the Double Ratchet can proceed.
     */
    suspend fun initiateSession(peerGwId: String, bundle: PreKeyBundle) =
        withContext(Dispatchers.Default) {
            val address = SignalProtocolAddress(peerGwId, 1)
            val builder = SessionBuilder(signalProtocolStore, address)
            builder.process(bundle)   // X3DH + initial Double Ratchet state stored
        }

    /**
     * Builds the local device's prekey bundle for publication to IPFS DHT.
     * Called on first launch and after signed prekey rotation.
     */
    suspend fun buildLocalPrekeyBundle(): LocalPrekeyBundle =
        withContext(Dispatchers.Default) {
            val identityKeyPair = signalProtocolStore.getIdentityKeyPair()
            val registrationId  = signalProtocolStore.getLocalRegistrationId()

            // Ensure we have a signed prekey
            val spks         = signedPreKeyDao.loadAllSignedPreKeys()
            val signedPreKey = if (spks.isNotEmpty()) {
                org.signal.libsignal.protocol.state.SignedPreKeyRecord(spks.last().serializedRecord)
            } else {
                val id      = identityRepository.nextSignedPreKeyId()
                val spk     = signalManager.generateSignedPreKey(identityKeyPair, id)
                signedPreKeyDao.storeSignedPreKey(
                    SignalSignedPreKey(id, spk.serialize(), System.currentTimeMillis())
                )
                spk
            }

            // Grab one OPK to include in bundle (optional but increases security)
            val preKeys  = preKeyDao.loadAllPreKeys()
            val oneTimePk = preKeys.firstOrNull()
                ?.let { org.signal.libsignal.protocol.state.PreKeyRecord(it.serializedRecord) }

            LocalPrekeyBundle(
                registrationId    = registrationId,
                identityKeyBase64 = Base64.encodeToString(
                    identityKeyPair.publicKey.serialize(), Base64.NO_WRAP),
                signedPreKeyId    = signedPreKey.id,
                signedPreKeyBase64 = Base64.encodeToString(
                    signedPreKey.keyPair.publicKey.serialize(), Base64.NO_WRAP),
                signedPreKeySignatureBase64 = Base64.encodeToString(signedPreKey.signature, Base64.NO_WRAP),
                oneTimePreKeyId   = oneTimePk?.id,
                oneTimePreKeyBase64 = oneTimePk?.let {
                    Base64.encodeToString(it.keyPair.publicKey.serialize(), Base64.NO_WRAP)
                },
            )
        }

    /** Converts a remote peer's bundle JSON fields into a libsignal [PreKeyBundle]. */
    fun buildPreKeyBundle(
        registrationId: Int,
        deviceId:       Int = 1,
        identityKeyBytes:          ByteArray,
        signedPreKeyId:            Int,
        signedPreKeyBytes:         ByteArray,
        signedPreKeySignatureBytes: ByteArray,
        oneTimePreKeyId:           Int?,
        oneTimePreKeyBytes:        ByteArray?,
    ): PreKeyBundle = if (oneTimePreKeyId != null && oneTimePreKeyBytes != null) {
        PreKeyBundle(
            registrationId, deviceId,
            oneTimePreKeyId,  org.signal.libsignal.protocol.ecc.Curve.decodePoint(oneTimePreKeyBytes, 0),
            signedPreKeyId,   org.signal.libsignal.protocol.ecc.Curve.decodePoint(signedPreKeyBytes, 0),
            signedPreKeySignatureBytes,
            IdentityKey(identityKeyBytes, 0),
        )
    } else {
        PreKeyBundle(
            registrationId, deviceId,
            -1, null,
            signedPreKeyId,   org.signal.libsignal.protocol.ecc.Curve.decodePoint(signedPreKeyBytes, 0),
            signedPreKeySignatureBytes,
            IdentityKey(identityKeyBytes, 0),
        )
    }
}

/** Serialisable local prekey bundle published to IPFS DHT. */
data class LocalPrekeyBundle(
    val registrationId:              Int,
    val identityKeyBase64:           String,
    val signedPreKeyId:              Int,
    val signedPreKeyBase64:          String,
    val signedPreKeySignatureBase64: String,
    val oneTimePreKeyId:             Int?,
    val oneTimePreKeyBase64:         String?,
)

// Extension: load all prekeys (needed for bundle building)
private fun SignalPreKeyDao.loadAllPreKeys(): List<SignalPreKey> =
    // Direct query — added as convenience; formal DAO query added in migration if needed
    try {
        val max = runCatching { kotlinx.coroutines.runBlocking { getMaxPreKeyId() } }.getOrNull() ?: return emptyList()
        (1..max).mapNotNull { loadPreKey(it) }
    } catch (_: Exception) { emptyList() }
