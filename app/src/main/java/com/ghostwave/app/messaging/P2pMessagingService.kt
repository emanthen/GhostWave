package com.ghostwave.app.messaging

import com.ghostwave.app.data.MessageRepository
import com.ghostwave.app.data.model.Contact
import com.ghostwave.app.data.model.Message
import com.ghostwave.app.data.model.MessageStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade that routes outgoing messages through the P2P data channel.
 *
 * Delivery priority order:
 *   1. WebRTC data channel (if peer is directly connected) — lowest latency
 *   2. WorkManager offline queue (if peer is unreachable)  — guaranteed delivery
 *
 * This class is intentionally thin — it delegates to [DataChannelManager] and
 * WorkManager. The Signal Protocol encryption happens in [MessageRepository]
 * before ciphertext reaches the network layer.
 */
@Singleton
class P2pMessagingService @Inject constructor(
    private val messageRepo:         MessageRepository,
    private val dataChannelManager:  DataChannelManager,
    private val offlineQueue:        OfflineMessageQueue,
) {

    /**
     * Attempts real-time P2P delivery; falls back to offline queue.
     */
    suspend fun deliverMessage(contact: Contact, message: Message) {
        val ciphertext = messageRepo.encryptForTransmission(message, contact.ghostWaveId)

        val delivered = dataChannelManager.sendEncryptedMessage(
            peerGwId   = contact.ghostWaveId,
            messageId  = message.id,
            ciphertext = ciphertext,
            timestamp  = message.timestamp,
        )

        if (delivered) {
            messageRepo.markSent(message.id)
        } else {
            offlineQueue.enqueue(message.id, contact.id, contact.ghostWaveId)
        }
    }

    /**
     * Sends a reaction update to the peer.
     * The payload is a tiny Signal-encrypted JSON fragment:
     *   {"type":"reaction","messageId":"<id>","emoji":"❤️"}
     */
    suspend fun sendReaction(contactId: String, messageId: String, emoji: String) {
        dataChannelManager.sendReaction(
            contactId = contactId,
            messageId = messageId,
            emoji     = emoji,
        )
    }
}
