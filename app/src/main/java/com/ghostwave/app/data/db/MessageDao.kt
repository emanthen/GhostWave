package com.ghostwave.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ghostwave.app.data.model.Message
import com.ghostwave.app.data.model.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    // ── Write ─────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: Message)

    /** Batch insert — used when pulling multiple queued messages from a peer. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessages(messages: List<Message>)

    @Update
    suspend fun updateMessage(message: Message)

    // ── Status updates (targeted to avoid full-object round-trips) ─────────

    @Query("""
        UPDATE messages
        SET status = :status, deliveredAt = :deliveredAt
        WHERE id = :id
    """)
    suspend fun markDelivered(id: String, status: String = MessageStatus.DELIVERED.name, deliveredAt: Long)

    @Query("""
        UPDATE messages
        SET status = :status, readAt = :readAt
        WHERE id = :id
    """)
    suspend fun markRead(id: String, status: String = MessageStatus.READ.name, readAt: Long)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE messages SET reactionsJson = :reactionsJson WHERE id = :id")
    suspend fun updateReactions(id: String, reactionsJson: String)

    @Query("UPDATE messages SET mediaLocalPath = :path WHERE id = :id")
    suspend fun updateMediaLocalPath(id: String, path: String)

    // Soft-delete: hides message body but keeps the row for reply threading
    @Query("UPDATE messages SET isDeleted = 1, body = '' WHERE id = :id")
    suspend fun softDeleteMessage(id: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun hardDeleteMessage(id: String)

    @Query("DELETE FROM messages WHERE contactId = :contactId")
    suspend fun deleteAllForContact(contactId: String)

    // ── Read (Flows) ──────────────────────────────────────────────────────

    /**
     * Live ordered message list for a conversation.
     * Excludes hard-deleted rows; soft-deleted rows are included so
     * reply threads can show "This message was deleted."
     */
    @Query("""
        SELECT * FROM messages
        WHERE contactId = :contactId
        ORDER BY timestamp ASC
    """)
    fun observeMessagesForContact(contactId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: String): Message?

    @Query("""
        SELECT * FROM messages
        WHERE contactId = :contactId
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun getLatestMessageForContact(contactId: String): Message?

    // ── Offline delivery queue ────────────────────────────────────────────

    /** All PENDING or FAILED outgoing messages, oldest first — fed to WorkManager retry. */
    @Query("""
        SELECT * FROM messages
        WHERE direction = 'OUTGOING'
          AND (status = 'PENDING' OR status = 'FAILED')
        ORDER BY timestamp ASC
    """)
    suspend fun getPendingOutboundMessages(): List<Message>

    @Query("""
        SELECT * FROM messages
        WHERE direction = 'OUTGOING'
          AND (status = 'PENDING' OR status = 'FAILED')
          AND contactId = :contactId
        ORDER BY timestamp ASC
    """)
    suspend fun getPendingOutboundMessagesForContact(contactId: String): List<Message>

    // ── Unread count ──────────────────────────────────────────────────────

    @Query("""
        SELECT COUNT(*) FROM messages
        WHERE contactId = :contactId
          AND direction = 'INCOMING'
          AND status != 'READ'
          AND isDeleted = 0
    """)
    fun observeUnreadCount(contactId: String): Flow<Int>

    // ── Full-text search ──────────────────────────────────────────────────
    // SQLite LIKE is sufficient for personal message volumes; FTS5 can be
    // added as a migration in Step 15 if needed for performance.

    @Query("""
        SELECT * FROM messages
        WHERE body LIKE '%' || :query || '%'
          AND isDeleted = 0
        ORDER BY timestamp DESC
        LIMIT 100
    """)
    suspend fun searchMessages(query: String): List<Message>

    // ── Disappearing messages ─────────────────────────────────────────────

    /** Returns all messages whose timer has expired (expiresAt <= now). */
    @Query("SELECT * FROM messages WHERE expiresAt IS NOT NULL AND expiresAt <= :nowMillis")
    suspend fun getExpiredMessages(nowMillis: Long): List<Message>

    @Query("DELETE FROM messages WHERE expiresAt IS NOT NULL AND expiresAt <= :nowMillis")
    suspend fun deleteExpiredMessages(nowMillis: Long): Int

    @Query("UPDATE messages SET expiresAt = :expiresAt WHERE id = :id")
    suspend fun setExpiresAt(id: String, expiresAt: Long)
}
