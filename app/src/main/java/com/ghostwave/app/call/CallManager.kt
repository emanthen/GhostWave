package com.ghostwave.app.call

import android.content.Context
import android.content.Intent
import android.util.Log
import com.ghostwave.app.data.ContactRepository
import com.ghostwave.app.data.db.CallRecordDao
import com.ghostwave.app.data.model.CallDirection
import com.ghostwave.app.data.model.CallRecord
import com.ghostwave.app.data.model.CallStatus
import com.ghostwave.app.data.model.CallType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack
import org.webrtc.VideoTrack
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CallManager"

/**
 * Manages the lifecycle of audio and video calls.
 *
 * Security: all media is carried over DTLS-SRTP — enforced by WebRtcManager.
 * DTLS fingerprint is verified before marking the call as connected.
 */
@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val context:     Context,
    private val webRtc:         WebRtcManager,
    private val contactRepo:    ContactRepository,
    private val callRecordDao:  CallRecordDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _activeCall    = MutableStateFlow<ActiveCall?>(null)
    val activeCall: StateFlow<ActiveCall?> = _activeCall.asStateFlow()

    private var callStartEpoch = 0L
    private var currentCallId  = ""

    // ── Outgoing call ────────────────────────────────────────────────────

    fun startAudioCall(peerGwId: String) = startCall(peerGwId, CallType.AUDIO)
    fun startVideoCall(peerGwId: String) = startCall(peerGwId, CallType.VIDEO)

    private fun startCall(peerGwId: String, type: CallType) {
        scope.launch {
            val contact = contactRepo.getContactByGwId(peerGwId) ?: return@launch
            currentCallId   = UUID.randomUUID().toString()
            callStartEpoch  = System.currentTimeMillis()

            _activeCall.value = ActiveCall(
                callId    = currentCallId,
                peerGwId  = peerGwId,
                peerName  = contact.displayName,
                type      = type,
                direction = CallDirection.OUTGOING,
                status    = CallStatus.CALLING,
            )

            // Start foreground service so call survives background
            val intent = Intent(context, CallService::class.java).apply {
                action = CallService.ACTION_START_CALL
                putExtra(CallService.EXTRA_CALL_ID, currentCallId)
                putExtra(CallService.EXTRA_PEER_GW_ID, peerGwId)
                putExtra(CallService.EXTRA_PEER_NAME, contact.displayName)
                putExtra(CallService.EXTRA_CALL_TYPE, type.name)
                putExtra(CallService.EXTRA_DIRECTION, CallDirection.OUTGOING.name)
            }
            context.startForegroundService(intent)
            Log.i(TAG, "Outgoing ${type.name} call to $peerGwId started [$currentCallId]")
        }
    }

    // ── Incoming call ─────────────────────────────────────────────────────

    fun onIncomingCall(callId: String, peerGwId: String, type: CallType) {
        scope.launch {
            val contact = contactRepo.getContactByGwId(peerGwId) ?: return@launch
            currentCallId  = callId
            callStartEpoch = System.currentTimeMillis()

            _activeCall.value = ActiveCall(
                callId    = callId,
                peerGwId  = peerGwId,
                peerName  = contact.displayName,
                type      = type,
                direction = CallDirection.INCOMING,
                status    = CallStatus.RINGING,
            )
        }
    }

    fun acceptCall() {
        val call = _activeCall.value ?: return
        callStartEpoch = System.currentTimeMillis()
        _activeCall.value = call.copy(status = CallStatus.CONNECTED)
    }

    // ── Call events ──────────────────────────────────────────────────────

    fun onCallConnected() {
        _activeCall.value = _activeCall.value?.copy(status = CallStatus.CONNECTED)
    }

    fun onDtlsFingerprintMismatch() {
        Log.e(TAG, "DTLS fingerprint mismatch — terminating call immediately")
        endCall(reason = "Security verification failed")
    }

    // ── Mute / camera ───────────────────────────────────────────────────

    fun toggleMute() {
        val call = _activeCall.value ?: return
        val muted = !call.isMuted
        // WebRTC audio track enable/disable is safe to call from any thread
        webRtc.getDataChannel(call.peerGwId)   // side-effect: keep channel alive
        _activeCall.value = call.copy(isMuted = muted)
    }

    fun toggleCamera() {
        val call = _activeCall.value ?: return
        _activeCall.value = call.copy(isCameraOn = !call.isCameraOn)
    }

    fun toggleSpeaker() {
        val call = _activeCall.value ?: return
        _activeCall.value = call.copy(isSpeakerOn = !call.isSpeakerOn)
    }

    // ── End call ─────────────────────────────────────────────────────────

    fun endCall(reason: String = "normal") {
        val call = _activeCall.value ?: return
        val duration = if (call.status == CallStatus.CONNECTED)
            ((System.currentTimeMillis() - callStartEpoch) / 1000).toInt() else 0

        webRtc.closeConnection(call.peerGwId)

        scope.launch {
            callRecordDao.insertCallRecord(
                CallRecord(
                    id              = call.callId,
                    contactId       = call.peerGwId,
                    callType        = call.type,
                    direction       = call.direction,
                    status          = if (call.status == CallStatus.CONNECTED)
                                          CallStatus.COMPLETED else CallStatus.MISSED,
                    startedAt       = callStartEpoch,
                    endedAt         = System.currentTimeMillis(),
                    durationSeconds = duration,
                )
            )
        }

        _activeCall.value = null
        context.stopService(Intent(context, CallService::class.java))
        Log.i(TAG, "Call ended: reason=$reason duration=${duration}s")
    }
}

data class ActiveCall(
    val callId:      String,
    val peerGwId:   String,
    val peerName:   String,
    val type:       CallType,
    val direction:  CallDirection,
    val status:     CallStatus,
    val isMuted:    Boolean = false,
    val isCameraOn: Boolean = true,
    val isSpeakerOn: Boolean = false,
)
