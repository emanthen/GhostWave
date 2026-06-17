package com.ghostwave.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a known peer contact stored in the local encrypted database.
 *
 * Note on publicKeyBase64:
 *   Stored as Base64-encoded IdentityKey.serialize() bytes.
 *   Used to verify Safety Numbers and detect key changes (MITM alert).
 *   Never transmitted in plaintext; only exchanged via QR / X3DH bundle.
 */
@Entity(
    tableName = "contacts",
    indices = [
        Index("ghostWaveId", unique = true),  // O(1) lookup by GW-ID
        Index("lastMessageAt"),               // sort contact list by recency
    ],
)
data class Contact(
    @PrimaryKey val id: String,              // local UUID generated on contact creation
    val ghostWaveId: String,                 // GW-XXXX-XXXX derived from their public key
    val displayName: String,
    val avatarPath: String?     = null,      // absolute path to local avatar file
    val publicKeyBase64: String,             // Base64(IdentityKey.serialize())
    val fcmToken: String?       = null,      // their FCM registration token for wakeup pings
    val isBlocked: Boolean      = false,
    val isVerified: Boolean     = false,     // true if user has confirmed Safety Numbers
    val addedAt: Long,                       // epoch millis
    val lastSeenAt: Long?       = null,
    val lastMessagePreview: String? = null,  // truncated plaintext for contact list row
    val lastMessageAt: Long?    = null,
    val unreadCount: Int        = 0,
    // Disappearing messages: null=off, value in seconds (3600=1h, 86400=24h, 604800=7d)
    val disappearingMessagesDurationSecs: Long? = null,
    val isMuted: Boolean        = false,
    val muteUntil: Long?        = null,      // epoch millis; null = muted indefinitely
)
