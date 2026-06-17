package com.ghostwave.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted Double Ratchet session state for one remote peer device.
 *
 * compositeId = "${gwId}:${deviceId}"  (e.g. "GW-AAAA-BBBB:1")
 *
 * GhostWave is single-device per GW-ID so deviceId is always 1.
 * The field is kept so the Signal protocol's multi-device interface
 * compiles cleanly; future multi-device support only needs schema changes.
 *
 * serializedRecord is SessionRecord.serialize() — an opaque blob managed
 * entirely by libsignal. Never inspect or modify it directly.
 */
@Entity(
    tableName = "signal_sessions",
    indices   = [Index("gwId")],
)
data class SignalSession(
    @PrimaryKey val compositeId: String,
    val gwId: String,
    val deviceId: Int       = 1,
    val serializedRecord: ByteArray,
    val updatedAt: Long,
)
