package com.ghostwave.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A one-time prekey owned by the local device.
 *
 * Prekeys are generated in batches (Step 2) and uploaded to IPFS DHT (Step 5)
 * as part of the prekey bundle. When an X3DH initiator consumes a prekey,
 * it is permanently removed from this table via [SignalPreKeyDao.removePreKey].
 *
 * SECURITY: Once a prekey is consumed, it MUST NOT be reused.
 * Reuse would break the one-time-prekey forward-secrecy guarantee.
 */
@Entity(tableName = "signal_prekeys")
data class SignalPreKey(
    @PrimaryKey val preKeyId: Int,
    val serializedRecord: ByteArray,         // PreKeyRecord.serialize()
)
