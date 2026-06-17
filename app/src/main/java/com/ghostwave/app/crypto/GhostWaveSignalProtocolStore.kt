package com.ghostwave.app.crypto

import com.ghostwave.app.data.IdentityRepository
import com.ghostwave.app.data.db.SignalIdentityKeyDao
import com.ghostwave.app.data.db.SignalPreKeyDao
import com.ghostwave.app.data.db.SignalSessionDao
import com.ghostwave.app.data.db.SignalSignedPreKeyDao
import com.ghostwave.app.data.model.SignalIdentityKey
import com.ghostwave.app.data.model.SignalPreKey
import com.ghostwave.app.data.model.SignalSession
import com.ghostwave.app.data.model.SignalSignedPreKey
import kotlinx.coroutines.runBlocking
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of the Signal Protocol's four store interfaces.
 *
 * Interface contract notes:
 * ──────────────────────────────────────────────────────────────────────────
 * Signal Protocol's store interfaces are synchronous (legacy Java design).
 * We satisfy them with [runBlocking] so Room's `suspend` DAOs can be called
 * from the sync context. This is safe because:
 *   a) All Signal crypto operations run on [Dispatchers.Default], so
 *      `runBlocking` there just re-enters a Default thread — no deadlock.
 *   b) The DB operations are short (indexed key lookups, not scans).
 *
 * Identity trust model:
 *   - First time we see a peer's identity key: save as TRUST_DEFAULT.
 *   - If the key CHANGES: update to TRUST_UNVERIFIED and surface a warning
 *     to the user (handled in the ViewModel layer in Step 5).
 *   - After user confirms Safety Numbers: mark TRUST_VERIFIED.
 *   - isTrustedIdentity returns true for DEFAULT and VERIFIED; false for
 *     UNVERIFIED (blocks message send until user acknowledges the change).
 *
 * Address format:
 *   Signal addresses are (name, deviceId). In GhostWave:
 *     name     = GW-XXXX-XXXX  (the peer's GhostWave ID)
 *     deviceId = 1             (single device per GW-ID)
 * ──────────────────────────────────────────────────────────────────────────
 */
@Singleton
class GhostWaveSignalProtocolStore @Inject constructor(
    private val identityRepository:  IdentityRepository,
    private val sessionDao:          SignalSessionDao,
    private val preKeyDao:           SignalPreKeyDao,
    private val signedPreKeyDao:     SignalSignedPreKeyDao,
    private val identityKeyDao:      SignalIdentityKeyDao,
) : IdentityKeyStore, PreKeyStore, SessionStore, SignedPreKeyStore {

    // ── In-memory cache for local identity (hot path — used on every message) ──
    @Volatile private var cachedIdentityKeyPair: IdentityKeyPair? = null
    @Volatile private var cachedRegistrationId:  Int              = -1

    private fun loadLocalIdentity() {
        if (cachedIdentityKeyPair == null) {
            runBlocking {
                cachedIdentityKeyPair = identityRepository.getIdentityKeyPair()
                cachedRegistrationId  = identityRepository.getRegistrationId()
            }
        }
    }

    // ── IdentityKeyStore ────────────────────────────────────────────────────

    override fun getIdentityKeyPair(): IdentityKeyPair {
        loadLocalIdentity()
        return cachedIdentityKeyPair
            ?: error("Identity key pair not initialised — was createAndSaveIdentity() called?")
    }

    override fun getLocalRegistrationId(): Int {
        loadLocalIdentity()
        return cachedRegistrationId
    }

    /**
     * Called by Signal protocol when establishing a session with [address].
     * Returns true if the key is new or unchanged (proceed with message).
     * Returns false if the key changed (block message, surface warning to user).
     */
    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
        val gwId    = address.name
        val stored  = identityKeyDao.getIdentityKey(gwId)

        return if (stored == null) {
            // First contact: save as DEFAULT trust
            identityKeyDao.saveIdentityKey(
                SignalIdentityKey(
                    gwId          = gwId,
                    serializedKey = identityKey.serialize(),
                    trustLevel    = SignalIdentityKey.TRUST_DEFAULT,
                    addedAt       = System.currentTimeMillis(),
                )
            )
            false   // Signal spec: return false when a new identity is saved
        } else {
            val storedKey = IdentityKey(stored.serializedKey, 0)
            if (storedKey == identityKey) {
                false   // unchanged — no update needed
            } else {
                // KEY CHANGED — mark unverified; the caller (Step 5 messaging layer)
                // will surface a safety-number-mismatch warning to the user.
                identityKeyDao.saveIdentityKey(
                    stored.copy(
                        serializedKey = identityKey.serialize(),
                        trustLevel    = SignalIdentityKey.TRUST_UNVERIFIED,
                    )
                )
                true    // Signal spec: return true when an identity is updated
            }
        }
    }

    override fun isTrustedIdentity(
        address:     SignalProtocolAddress,
        identityKey: IdentityKey,
        direction:   IdentityKeyStore.Direction,
    ): Boolean {
        val gwId   = address.name
        val stored = identityKeyDao.getIdentityKey(gwId) ?: return true  // first contact: trust by default

        val storedKey = IdentityKey(stored.serializedKey, 0)
        if (storedKey != identityKey) return false  // key mismatch — reject

        // Block messaging if user hasn't acknowledged a key change
        return stored.trustLevel != SignalIdentityKey.TRUST_UNVERIFIED
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        val stored = identityKeyDao.getIdentityKey(address.name) ?: return null
        return IdentityKey(stored.serializedKey, 0)
    }

    // ── PreKeyStore ────────────────────────────────────────────────────────

    @Throws(InvalidKeyIdException::class)
    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        return preKeyDao.loadPreKey(preKeyId)
            ?.let { PreKeyRecord(it.serializedRecord) }
            ?: throw InvalidKeyIdException("PreKey $preKeyId not found")
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        preKeyDao.storePreKey(SignalPreKey(preKeyId, record.serialize()))
    }

    override fun containsPreKey(preKeyId: Int): Boolean =
        preKeyDao.containsPreKey(preKeyId)

    override fun removePreKey(preKeyId: Int) {
        preKeyDao.removePreKey(preKeyId)
    }

    // ── SessionStore ────────────────────────────────────────────────────────

    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        val compositeId = address.toCompositeId()
        val stored      = sessionDao.loadSession(compositeId)
        return if (stored != null) {
            SessionRecord(stored.serializedRecord)
        } else {
            SessionRecord()   // Signal spec: return empty record if no session
        }
    }

    override fun getSubDeviceSessions(name: String): List<Int> =
        sessionDao.getSubDeviceIds(name)

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        sessionDao.storeSession(
            SignalSession(
                compositeId      = address.toCompositeId(),
                gwId             = address.name,
                deviceId         = address.deviceId,
                serializedRecord = record.serialize(),
                updatedAt        = System.currentTimeMillis(),
            )
        )
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean =
        sessionDao.containsSession(address.toCompositeId())

    override fun deleteSession(address: SignalProtocolAddress) {
        sessionDao.deleteSession(address.toCompositeId())
    }

    override fun deleteAllSessions(name: String) {
        sessionDao.deleteAllSessionsForGwId(name)
    }

    // ── SignedPreKeyStore ──────────────────────────────────────────────────

    @Throws(InvalidKeyIdException::class)
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        return signedPreKeyDao.loadSignedPreKey(signedPreKeyId)
            ?.let { SignedPreKeyRecord(it.serializedRecord) }
            ?: throw InvalidKeyIdException("SignedPreKey $signedPreKeyId not found")
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> =
        signedPreKeyDao.loadAllSignedPreKeys()
            .map { SignedPreKeyRecord(it.serializedRecord) }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        signedPreKeyDao.storeSignedPreKey(
            SignalSignedPreKey(
                signedPreKeyId   = signedPreKeyId,
                serializedRecord = record.serialize(),
                createdAt        = System.currentTimeMillis(),
            )
        )
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean =
        signedPreKeyDao.containsSignedPreKey(signedPreKeyId)

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        signedPreKeyDao.removeSignedPreKey(signedPreKeyId)
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Invalidates the in-memory identity cache (called after re-register). */
    fun clearCache() {
        cachedIdentityKeyPair = null
        cachedRegistrationId  = -1
    }

    private fun SignalProtocolAddress.toCompositeId() = "${name}:${deviceId}"
}
