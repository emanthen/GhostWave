package com.ghostwave.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The identity public key for a known peer (contact or potential contact).
 *
 * Used by [GhostWaveSignalProtocolStore.isTrustedIdentity] to:
 *   1. Detect if a peer's key has changed (potential MITM) → warn user
 *   2. Track whether the user has verified via Safety Numbers
 *
 * trustLevel values:
 *   TRUST_DEFAULT    (0) — key seen but Safety Numbers not confirmed by user
 *   TRUST_VERIFIED   (1) — user explicitly verified Safety Numbers in-person
 *   TRUST_UNVERIFIED (2) — key changed; user must re-verify before messaging
 */
@Entity(tableName = "signal_identity_keys")
data class SignalIdentityKey(
    @PrimaryKey val gwId: String,            // GW-XXXX-XXXX of the contact
    val serializedKey: ByteArray,            // IdentityKey.serialize()
    val trustLevel: Int = TRUST_DEFAULT,
    val addedAt: Long,
) {
    companion object {
        const val TRUST_DEFAULT    = 0
        const val TRUST_VERIFIED   = 1
        const val TRUST_UNVERIFIED = 2
    }
}
