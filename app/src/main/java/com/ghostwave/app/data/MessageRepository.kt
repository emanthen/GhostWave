package com.ghostwave.app.data

import com.ghostwave.app.crypto.MessageEncryptor
import com.ghostwave.app.data.db.MessageDao
import com.ghostwave.app.data.model.Message
import com.ghostwave.app.data.model.MessageDirection
import com.ghostwave.app.data.model.MessageStatus
import com.ghostwave.app.data.model.MessageType
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val messageDao:       MessageDao,
    private val encryptor:        MessageEncryptor,
    private val contactRepository: ContactRepository,
    private val identityRepository: IdentityRepository,
) {

    fun observeMessages(contactId: String): Flow<List<Message>> =
        messageDao.observeMessagesForContact(contactId)

    fun observeUnreadCount(contactId: String): Flow<Int> =
        messageDao.observeUnreadCount(contactId)

    suspend fun getMessageById(id: String): Message? = messageDao.getMessageById(id)

    /**
     * Creates and persists an outgoing text message.
     * The message starts as PENDING; status is updated when P2P delivery succeeds.
     * The [body] is stored as plaintext — the DB is SQLCipher-encrypted at rest.
     */
    suspend fun sendTextMessage(
        contactId:        String,
        body:             String,
        replyToMessageId: String? = null,
    ): Message {
        val contact   = contactRepository.getContactById(contactId) ?: error("Contact not found")
        val localGwId = identityRepository.getGhostWaveId() ?: error("No local identity")

        val message = Message(
            id              = UUID.randomUUID().toString(),
            contactId       = contactId,
            senderGwId      = localGwId,
            body            = body,
            direction       = MessageDirection.OUTGOING,
            status          = MessageStatus.PENDING,
            replyToMessageId = replyToMessageId,
            timestamp       = System.currentTimeMillis(),
            expiresAt       = contact.disappearingMessagesDurationSecs
                ?.let { System.currentTimeMillis() + it * 1000L },
        )
        messageDao.insertMessage(message)
        contactRepository.updateLastMessage(contactId, body.take(80), message.timestamp)
        return message
    }

    /**
     * Persists an incoming message that has been decrypted from the P2P data channel.
     * [encryptedPayload] is decrypted via [MessageEncryptor] before storage.
     */
    suspend fun receiveEncryptedMessage(
        senderGwId:       String,
        encryptedPayload: ByteArray,
        messageId:        String,
        timestamp:        Long,
    ) {
        val contact = contactRepository.getContactByGwId(senderGwId) ?: return
        val plaintextBytes = encryptor.decrypt(senderGwId, encryptedPayload)
        val body           = plaintextBytes.decodeToString()
        plaintextBytes.fill(0)   // zero after decode

        val message = Message(
            id         = messageId,
            contactId  = contact.id,
            senderGwId = senderGwId,
            body       = body,
            direction  = MessageDirection.INCOMING,
            status     = MessageStatus.DELIVERED,
            timestamp  = timestamp,
            expiresAt  = contact.disappearingMessagesDurationSecs
                ?.let { System.currentTimeMillis() + it * 1000L },
        )
        messageDao.insertMessage(message)
        contactRepository.updateLastMessage(contact.id, body.take(80), timestamp)
        contactRepository.incrementUnread(contact.id)
    }

    /**
     * Encrypts a pending outgoing message for wire transmission.
     * Returns the ciphertext bytes to be sent via the P2P data channel.
     */
    suspend fun encryptForTransmission(message: Message, peerGwId: String): ByteArray {
        val plaintext   = message.body.toByteArray()
        val ciphertext  = encryptor.encrypt(peerGwId, plaintext)
        plaintext.fill(0)
        return ciphertext
    }

    suspend fun markSent(messageId: String) =
        messageDao.updateStatus(messageId, MessageStatus.SENT.name)

    suspend fun markDelivered(messageId: String) =
        messageDao.markDelivered(messageId, deliveredAt = System.currentTimeMillis())

    suspend fun markRead(messageId: String) {
        messageDao.markRead(messageId, readAt = System.currentTimeMillis())
    }

    suspend fun markAllReadForContact(contactId: String) {
        messageDao.observeMessagesForContact(contactId)  // read once then mark
        contactRepository.clearUnread(contactId)
    }

    suspend fun deleteMessage(messageId: String) = messageDao.softDeleteMessage(messageId)

    suspend fun deleteAllForContact(contactId: String) = messageDao.deleteAllForContact(contactId)

    suspend fun getPendingOutbound(): List<Message> = messageDao.getPendingOutboundMessages()

    suspend fun getPendingForContact(contactId: String): List<Message> =
        messageDao.getPendingOutboundMessagesForContact(contactId)

    suspend fun searchMessages(query: String): List<Message> = messageDao.searchMessages(query)

    suspend fun updateReactions(messageId: String, reactionsJson: String) =
        messageDao.updateReactions(messageId, reactionsJson)

    // Disappearing messages
    suspend fun deleteExpiredMessages(): Int =
        messageDao.deleteExpiredMessages(System.currentTimeMillis())

    suspend fun insertMediaMessage(
        contactId:     String,
        senderGwId:    String,
        direction:     MessageDirection,
        ipfsCid:       String,
        keyBase64:     String,
        ivBase64:      String,
        mimeType:      String,
        fileSizeBytes: Long,
    ): Message {
        val localGwId = identityRepository.getGhostWaveId() ?: senderGwId
        val message = Message(
            id                = UUID.randomUUID().toString(),
            contactId         = contactId,
            senderGwId        = if (direction == MessageDirection.OUTGOING) localGwId else senderGwId,
            body              = "",
            messageType       = MessageType.IMAGE,
            direction         = direction,
            status            = if (direction == MessageDirection.OUTGOING) MessageStatus.PENDING else MessageStatus.DELIVERED,
            mediaIpfsCid      = ipfsCid,
            mediaKeyBase64    = keyBase64,
            mediaIvBase64     = ivBase64,
            mediaMimeType     = mimeType,
            mediaFileSizeBytes = fileSizeBytes,
            timestamp         = System.currentTimeMillis(),
        )
        messageDao.insertMessage(message)
        contactRepository.updateLastMessage(contactId, "📷 Photo", message.timestamp)
        return message
    }
}
