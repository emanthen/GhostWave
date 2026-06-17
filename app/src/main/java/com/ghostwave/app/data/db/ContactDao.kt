package com.ghostwave.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ghostwave.app.data.model.Contact
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    // ── Write ─────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertContact(contact: Contact)

    @Update
    suspend fun updateContact(contact: Contact)

    /** Upsert — used when syncing FCM token or display name updates. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContact(contact: Contact)

    @Delete
    suspend fun deleteContact(contact: Contact)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteContactById(id: String)

    // ── Read (Flows for UI observation) ──────────────────────────────────

    /**
     * All contacts sorted by most-recent message descending.
     * Contacts with no messages appear last, sorted by addedAt.
     */
    @Query("""
        SELECT * FROM contacts
        WHERE isBlocked = 0
        ORDER BY COALESCE(lastMessageAt, addedAt) DESC
    """)
    fun observeAllContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    fun observeContactById(id: String): Flow<Contact?>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getContactById(id: String): Contact?

    @Query("SELECT * FROM contacts WHERE ghostWaveId = :gwId")
    suspend fun getContactByGwId(gwId: String): Contact?

    @Query("SELECT * FROM contacts WHERE ghostWaveId = :gwId")
    fun observeContactByGwId(gwId: String): Flow<Contact?>

    @Query("SELECT COUNT(*) FROM contacts WHERE isBlocked = 0")
    fun observeContactCount(): Flow<Int>

    // ── Update helpers ────────────────────────────────────────────────────

    @Query("""
        UPDATE contacts
        SET lastMessagePreview = :preview, lastMessageAt = :timestamp
        WHERE id = :contactId
    """)
    suspend fun updateLastMessage(contactId: String, preview: String, timestamp: Long)

    @Query("UPDATE contacts SET unreadCount = unreadCount + 1 WHERE id = :contactId")
    suspend fun incrementUnreadCount(contactId: String)

    @Query("UPDATE contacts SET unreadCount = 0 WHERE id = :contactId")
    suspend fun clearUnreadCount(contactId: String)

    @Query("UPDATE contacts SET isBlocked = :isBlocked WHERE id = :id")
    suspend fun setBlocked(id: String, isBlocked: Boolean)

    @Query("UPDATE contacts SET isVerified = :isVerified WHERE id = :id")
    suspend fun setVerified(id: String, isVerified: Boolean)

    @Query("UPDATE contacts SET fcmToken = :token WHERE ghostWaveId = :gwId")
    suspend fun updateFcmToken(gwId: String, token: String)

    @Query("UPDATE contacts SET lastSeenAt = :timestamp WHERE ghostWaveId = :gwId")
    suspend fun updateLastSeen(gwId: String, timestamp: Long)

    @Query("""
        UPDATE contacts
        SET disappearingMessagesDurationSecs = :durationSecs
        WHERE id = :contactId
    """)
    suspend fun setDisappearingDuration(contactId: String, durationSecs: Long?)

    @Query("UPDATE contacts SET isMuted = :muted, muteUntil = :muteUntil WHERE id = :id")
    suspend fun setMuted(id: String, muted: Boolean, muteUntil: Long?)
}
