package com.ghostwave.app.p2p

import android.util.Log
import com.ghostwave.app.crypto.MessageEncryptor
import com.ghostwave.app.data.ContactRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SignalingChannel"

/**
 * Signal-encrypted signaling layer for WebRTC offer/answer/ICE exchange.
 *
 * All signaling messages are encrypted with the Double Ratchet (Signal Protocol)
 * before being sent, so the DTLS fingerprint and ICE candidates are authenticated.
 * This prevents MITM attacks that could substitute a rogue DTLS fingerprint.
 *
 * Transport: currently uses the P2P data channel on an existing connection,
 * or FCM for initial call setup when no data channel is open yet.
 */
@Singleton
class SignalingChannel @Inject constructor(
    private val messageEncryptor: MessageEncryptor,
    private val contactRepository: ContactRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _incomingSignals = MutableSharedFlow<SignalingMessage>(replay = 0, extraBufferCapacity = 16)
    val incomingSignals: SharedFlow<SignalingMessage> = _incomingSignals.asSharedFlow()

    // ── Outbound ──────────────────────────────────────────────────────────────

    /**
     * Encrypts and dispatches a signaling message to [peerGwId].
     * The message body is Signal-encrypted so the fingerprint is authenticated.
     */
    suspend fun send(peerGwId: String, message: SignalingMessage) {
        val contact = contactRepository.getContactByGwId(peerGwId) ?: run {
            Log.w(TAG, "Cannot send signal to unknown peer $peerGwId")
            return
        }
        val json = message.toJson()
        // Encryption uses Signal session for this contact
        try {
            val encrypted = messageEncryptor.encrypt(
                recipientAddress = org.signal.libsignal.protocol.SignalProtocolAddress(peerGwId, 1),
                plaintext        = json.toByteArray(Charsets.UTF_8),
            )
            // Deliver via data channel manager — injected at runtime to avoid circular dep
            onSendEncrypted?.invoke(peerGwId, encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt signaling message for $peerGwId", e)
        }
    }

    // ── Inbound ───────────────────────────────────────────────────────────────

    /**
     * Called by DataChannelManager when it receives a signaling-type frame.
     * Decrypts and emits to [incomingSignals].
     */
    fun onEncryptedSignalReceived(senderGwId: String, ciphertext: ByteArray) {
        scope.launch {
            try {
                val plaintext = messageEncryptor.decrypt(
                    senderAddress = org.signal.libsignal.protocol.SignalProtocolAddress(senderGwId, 1),
                    ciphertext    = ciphertext,
                )
                val msg = SignalingMessage.fromJson(senderGwId, String(plaintext, Charsets.UTF_8))
                if (msg != null) _incomingSignals.emit(msg)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt signaling message from $senderGwId", e)
            }
        }
    }

    // Set by CallManager after injection to avoid circular dependency
    var onSendEncrypted: (suspend (peerGwId: String, ciphertext: ByteArray) -> Unit)? = null
}

// ── Signaling message model ───────────────────────────────────────────────────

sealed class SignalingMessage(val senderGwId: String) {

    data class Offer(
        val from: String,
        val sdp:  String,
        val dtlsFingerprint: String,
    ) : SignalingMessage(from)

    data class Answer(
        val from: String,
        val sdp:  String,
        val dtlsFingerprint: String,
    ) : SignalingMessage(from)

    data class IceCandidate(
        val from:      String,
        val candidate: String,
        val sdpMid:    String?,
        val sdpMLineIndex: Int,
    ) : SignalingMessage(from)

    data class CallRequest(
        val from:     String,
        val callId:   String,
        val callType: String,   // "AUDIO" | "VIDEO"
    ) : SignalingMessage(from)

    data class CallDecline(val from: String, val callId: String) : SignalingMessage(from)
    data class CallEnd(val from: String, val callId: String)     : SignalingMessage(from)

    fun toJson(): String = when (this) {
        is Offer         -> JSONObject()
            .put("type", "offer").put("sdp", sdp).put("fp", dtlsFingerprint).toString()
        is Answer        -> JSONObject()
            .put("type", "answer").put("sdp", sdp).put("fp", dtlsFingerprint).toString()
        is IceCandidate  -> JSONObject()
            .put("type", "ice").put("c", candidate).put("mid", sdpMid ?: "").put("idx", sdpMLineIndex).toString()
        is CallRequest   -> JSONObject()
            .put("type", "call_req").put("id", callId).put("ct", callType).toString()
        is CallDecline   -> JSONObject().put("type", "call_dec").put("id", callId).toString()
        is CallEnd       -> JSONObject().put("type", "call_end").put("id", callId).toString()
    }

    companion object {
        fun fromJson(from: String, json: String): SignalingMessage? = try {
            val obj = JSONObject(json)
            when (obj.optString("type")) {
                "offer"    -> Offer(from, obj.getString("sdp"), obj.getString("fp"))
                "answer"   -> Answer(from, obj.getString("sdp"), obj.getString("fp"))
                "ice"      -> IceCandidate(from, obj.getString("c"), obj.optString("mid").ifEmpty { null }, obj.getInt("idx"))
                "call_req" -> CallRequest(from, obj.getString("id"), obj.getString("ct"))
                "call_dec" -> CallDecline(from, obj.getString("id"))
                "call_end" -> CallEnd(from, obj.getString("id"))
                else       -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse signaling JSON: $json", e)
            null
        }
    }
}
