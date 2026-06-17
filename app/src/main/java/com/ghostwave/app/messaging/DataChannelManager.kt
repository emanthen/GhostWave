package com.ghostwave.app.messaging

import android.util.Log
import com.ghostwave.app.call.WebRtcManager
import com.ghostwave.app.crypto.MessageEncryptor
import com.ghostwave.app.data.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.DataChannel
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DataChannelMgr"

/**
 * Manages WebRTC data channels for P2P encrypted message exchange.
 *
 * Each call to [sendEncryptedMessage] writes directly to the active WebRTC
 * data channel for the peer if one is open. Returns false if the peer is
 * not currently connected so that the caller can queue via WorkManager.
 *
 * Incoming data channel messages are dispatched via [onDataChannelMessage],
 * which is called by [WebRtcManager] when a data frame arrives.
 */
@Singleton
class DataChannelManager @Inject constructor(
    private val webRtcManager:   WebRtcManager,
    private val encryptor:       MessageEncryptor,
    private val messageRepository: MessageRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Sends a pre-encrypted [ciphertext] to [peerGwId] over the data channel.
     * Returns true if the frame was written, false if no channel is open.
     *
     * Wire frame (binary):
     *   [8 bytes: timestamp big-endian long]
     *   [16 bytes: messageId as two long parts — first 16 chars of UUID]
     *   [4 bytes: ciphertext length big-endian int]
     *   [N bytes: ciphertext]
     */
    fun sendEncryptedMessage(
        peerGwId:  String,
        messageId: String,
        ciphertext: ByteArray,
        timestamp:  Long,
    ): Boolean {
        val channel = webRtcManager.getDataChannel(peerGwId) ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false

        val idBytes = messageId.replace("-", "").substring(0, 16)
            .toByteArray(Charsets.US_ASCII)   // 16 bytes of UUID hex
        val buf = ByteBuffer.allocate(8 + 16 + 4 + ciphertext.size)
        buf.putLong(timestamp)
        buf.put(idBytes)
        buf.putInt(ciphertext.size)
        buf.put(ciphertext)
        buf.flip()

        return channel.send(DataChannel.Buffer(buf, true /* binary */))
    }

    /**
     * Called by WebRtcManager when a binary frame arrives on a data channel.
     * Decrypts and persists the incoming message.
     */
    fun onDataChannelMessage(senderGwId: String, buffer: DataChannel.Buffer) {
        if (!buffer.binary) return
        scope.launch {
            try {
                val buf        = buffer.data
                val timestamp  = buf.getLong()
                val idBytes    = ByteArray(16).also { buf.get(it) }
                val ctLen      = buf.getInt()
                val ciphertext = ByteArray(ctLen).also { buf.get(it) }

                val rawId  = idBytes.toString(Charsets.US_ASCII)
                val msgId  = "${rawId.substring(0,8)}-${rawId.substring(8,12)}-" +
                             "${rawId.substring(12,16)}-xxxx-xxxxxxxxxxxx"   // pad for compat

                messageRepository.receiveEncryptedMessage(
                    senderGwId       = senderGwId,
                    encryptedPayload = ciphertext,
                    messageId        = msgId,
                    timestamp        = timestamp,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process incoming data channel message", e)
            }
        }
    }

    /**
     * Sends a reaction update as a tiny Signal-encrypted JSON message.
     * Format: {"type":"reaction","messageId":"<id>","emoji":"❤️"}
     */
    suspend fun sendReaction(contactId: String, messageId: String, emoji: String) {
        val json = JSONObject().apply {
            put("type",      "reaction")
            put("messageId", messageId)
            put("emoji",     emoji)
            put("contactId", contactId)
        }.toString()
        // Re-use message channel: encrypt as a special reaction payload
        val ciphertext = encryptor.encrypt(contactId, json.toByteArray())
        sendEncryptedMessage(
            peerGwId   = contactId,
            messageId  = java.util.UUID.randomUUID().toString(),
            ciphertext = ciphertext,
            timestamp  = System.currentTimeMillis(),
        )
    }

    /**
     * Called when a text-channel frame is identified as a reaction payload.
     */
    fun onReactionReceived(senderGwId: String, messageId: String, emoji: String) {
        scope.launch {
            val msg = messageRepository.getMessageById(messageId) ?: return@launch
            val json = try {
                JSONObject(msg.reactionsJson)
            } catch (_: Exception) { JSONObject() }

            val arr = json.optJSONArray(emoji) ?: org.json.JSONArray()
            // Add sender if not already in the list
            val alreadyReacted = (0 until arr.length()).any { arr.getString(it) == senderGwId }
            if (!alreadyReacted) arr.put(senderGwId)
            json.put(emoji, arr)

            messageRepository.updateReactions(messageId, json.toString())
        }
    }
}
