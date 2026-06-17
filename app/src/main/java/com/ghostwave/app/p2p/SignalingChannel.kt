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
 * All signaling messages are Double-Ratchet encrypted before being sent,
 * so DTLS fingerprints and ICE candidates are authenticated.
 * This prevents MITM attacks that could substitute a rogue DTLS fingerprint.
 */
@Singleton
class SignalingChannel @Inject constructor(
    private val messageEncryptor:  MessageEncryptor,
    private val contactRepository: ContactRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _incomingSignals = MutableSharedFlow<SignalingMessage>(
        replay = 0, extraBufferCapacity = 16,
    )
    val incomingSignals: SharedFlow<SignalingMessage> = _incomingSignals.asSharedFlow()

    // Set by CallManager after injection to break the circular dependency.
    var onSendEncrypted: (suspend (peerGwId: String, ciphertext: ByteArray) -> Unit)? = null

    // ── Outbound ──────────────────────────────────────────────────────────────

    /** Encrypts [message] with the Signal session for [peerGwId] and dispatches it. */
    suspend fun send(peerGwId: String, message: SignalingMessage) {
        val contact = contactRepository.getContactByGwId(peerGwId) ?: run {
            Log.w(TAG, "Cannot send signal to unknown peer $peerGwId")
            return
        }
        try {
            val json      = message.toJson()
            val encrypted = messageEncryptor.encrypt(peerGwId, json.toByteArray(Charsets.UTF_8))
            onSendEncrypted?.invoke(peerGwId, encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt signaling message for $peerGwId", e)
        }
    }

    // ── Inbound ───────────────────────────────────────────────────────────────

    /** Called by DataChannelManager when it receives a signaling-type frame. */
    fun onEncryptedSignalReceived(senderGwId: String, ciphertext: ByteArray) {
        scope.launch {
            try {
                val plaintext = messageEncryptor.decrypt(senderGwId, ciphertext)
                val msg = SignalingMessage.fromJson(senderGwId, String(plaintext, Charsets.UTF_8))
                if (msg != null) _incomingSignals.emit(msg)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt signaling message from $senderGwId", e)
            }
        }
    }
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
        val from:          String,
        val candidate:     String,
        val sdpMid:        String?,
        val sdpMLineIndex: Int,
    ) : SignalingMessage(from)

    data class CallRequest(
        val from:     String,
        val callId:   String,
        val callType: String,
    ) : SignalingMessage(from)

    data class CallDecline(val from: String, val callId: String) : SignalingMessage(from)
    data class CallEnd(val from: String, val callId: String)     : SignalingMessage(from)

    fun toJson(): String = when (this) {
        is Offer        -> JSONObject().put("type","offer").put("sdp",sdp).put("fp",dtlsFingerprint).toString()
        is Answer       -> JSONObject().put("type","answer").put("sdp",sdp).put("fp",dtlsFingerprint).toString()
        is IceCandidate -> JSONObject().put("type","ice").put("c",candidate).put("mid",sdpMid?:"").put("idx",sdpMLineIndex).toString()
        is CallRequest  -> JSONObject().put("type","call_req").put("id",callId).put("ct",callType).toString()
        is CallDecline  -> JSONObject().put("type","call_dec").put("id",callId).toString()
        is CallEnd      -> JSONObject().put("type","call_end").put("id",callId).toString()
    }

    companion object {
        fun fromJson(from: String, json: String): SignalingMessage? = try {
            val o = JSONObject(json)
            when (o.optString("type")) {
                "offer"    -> Offer(from, o.getString("sdp"), o.getString("fp"))
                "answer"   -> Answer(from, o.getString("sdp"), o.getString("fp"))
                "ice"      -> IceCandidate(from, o.getString("c"), o.optString("mid").ifEmpty { null }, o.getInt("idx"))
                "call_req" -> CallRequest(from, o.getString("id"), o.getString("ct"))
                "call_dec" -> CallDecline(from, o.getString("id"))
                "call_end" -> CallEnd(from, o.getString("id"))
                else       -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse signaling JSON: $json", e)
            null
        }
    }
}
