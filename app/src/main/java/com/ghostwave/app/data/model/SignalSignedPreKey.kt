package com.ghostwave.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A signed prekey owned by the local device.
 *
 * Unlike one-time prekeys, signed prekeys are NOT consumed on use — they
 * are medium-term keys that remain valid until rotated (~30 days).
 * The signature (Ed25519, signed by the identity private key) lets peers
 * authenticate the prekey bundle without the identity key being online.
 *
 * Rotation logic:
 *   Keep the previous signed prekey for ~24 hours after rotation in case
 *   an initiator fetched the old bundle but hasn't completed X3DH yet.
 *   After that grace period, old signed prekeys can be safely deleted.
 */
@Entity(tableName = "signal_signed_prekeys")
data class SignalSignedPreKey(
    @PrimaryKey val signedPreKeyId: Int,
    val serializedRecord: ByteArray,        // SignedPreKeyRecord.serialize()
    val createdAt: Long,                    // epoch millis — used for rotation age check
)
