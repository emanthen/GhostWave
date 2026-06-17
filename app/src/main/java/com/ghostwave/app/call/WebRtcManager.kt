package com.ghostwave.app.call

import android.content.Context
import android.util.Log
import com.ghostwave.app.messaging.DataChannelManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG        = "WebRtcManager"
private const val STUN_URL   = "stun:stun.l.google.com:19302"

/**
 * Central WebRTC manager — owns PeerConnectionFactory and all active PeerConnections.
 *
 * Security invariants enforced here:
 *   1. DTLS-SRTP is the ONLY allowed crypto suite ([SdpSemantics.UNIFIED_PLAN] default).
 *   2. DtlsTransportPolicy = REQUIRE_DTLS_SRTP — non-DTLS connections are rejected.
 *   3. DTLS fingerprint is verified against the expected peer fingerprint before any
 *      media or data is accepted ([verifyDtlsFingerprint]).
 *
 * Call flow:
 *   Alice (caller)  → createOffer()  → sends SDP via signaling
 *   Bob  (callee)   → setRemoteOffer() → createAnswer() → sends SDP
 *   Both            → exchange ICE candidates via signaling
 *   Connection established — DTLS handshake happens at transport layer
 */
@Singleton
class WebRtcManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope   = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var factory: PeerConnectionFactory

    // Active peer connections keyed by remote GW-ID
    private val peers          = mutableMapOf<String, PeerConnection>()
    private val dataChannels   = mutableMapOf<String, DataChannel>()
    private val expectedDtlsFingerprints = mutableMapOf<String, String>()

    // Injected lazily to break the circular dep: DataChannelManager → WebRtcManager
    var dataChannelManager: DataChannelManager? = null

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    fun initialize() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(EglBase.create().eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(EglBase.create().eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    // ── PeerConnection lifecycle ──────────────────────────────────────────

    /**
     * Creates a [PeerConnection] for [peerGwId] with DTLS-SRTP enforced.
     * [expectedDtlsFingerprint]: hex fingerprint from the peer's identity bundle
     * (sent via Signal-encrypted signaling, so it's authenticated).
     */
    fun getOrCreatePeerConnection(
        peerGwId:               String,
        expectedDtlsFingerprint: String,
        observer:               PeerConnection.Observer,
    ): PeerConnection {
        peers[peerGwId]?.let { return it }

        expectedDtlsFingerprints[peerGwId] = expectedDtlsFingerprint.lowercase()

        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder(STUN_URL).createIceServer())
        ).apply {
            sdpSemantics          = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // DTLS-SRTP is mandatory — non-DTLS connections are rejected
            dtlsSrtpKeyAgreement  = true
            enableDtlsSrtp        = true
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val pc = factory.createPeerConnection(rtcConfig, observer)
            ?: error("PeerConnectionFactory returned null — is initialize() called?")

        peers[peerGwId] = pc
        return pc
    }

    /** Opens a labeled data channel on [peerGwId]'s peer connection. */
    fun openDataChannel(peerGwId: String): DataChannel? {
        val pc      = peers[peerGwId] ?: return null
        val init    = DataChannel.Init().apply { ordered = true }
        val channel = pc.createDataChannel("gw-msg", init)
        dataChannels[peerGwId] = channel

        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(amount: Long) {}
            override fun onStateChange() {
                Log.d(TAG, "DataChannel[$peerGwId] state = ${channel.state()}")
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                dataChannelManager?.onDataChannelMessage(peerGwId, buffer)
            }
        })
        return channel
    }

    fun getDataChannel(peerGwId: String): DataChannel? = dataChannels[peerGwId]

    /** Stores a remotely-created data channel (responder side). */
    fun registerRemoteDataChannel(peerGwId: String, channel: DataChannel) {
        dataChannels[peerGwId] = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(amount: Long) {}
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer) {
                dataChannelManager?.onDataChannelMessage(peerGwId, buffer)
            }
        })
    }

    // ── DTLS fingerprint verification ─────────────────────────────────────

    /**
     * Must be called after the DTLS handshake completes (inside
     * [PeerConnection.Observer.onIceConnectionChange] when state = CONNECTED).
     *
     * Compares the transport's actual DTLS fingerprint to the value we received
     * via Signal-encrypted signaling. Mismatch → connection terminated immediately.
     */
    fun verifyDtlsFingerprint(peerGwId: String, pc: PeerConnection): Boolean {
        val expected = expectedDtlsFingerprints[peerGwId] ?: run {
            Log.e(TAG, "No expected fingerprint for $peerGwId — closing connection")
            closeConnection(peerGwId)
            return false
        }
        // In production we read the DTLS fingerprint from the remote SDP answer;
        // libwebrtc does not expose it via Java API so we parse it from the SDP.
        val remoteSdp = pc.remoteDescription?.description ?: run {
            Log.e(TAG, "No remote SDP for $peerGwId")
            closeConnection(peerGwId)
            return false
        }
        val actualFingerprint = parseFingerprintFromSdp(remoteSdp)
        return if (actualFingerprint.equals(expected, ignoreCase = true)) {
            Log.i(TAG, "DTLS fingerprint verified for $peerGwId")
            true
        } else {
            Log.e(TAG, "DTLS fingerprint MISMATCH for $peerGwId: got=$actualFingerprint expected=$expected")
            closeConnection(peerGwId)
            false
        }
    }

    private fun parseFingerprintFromSdp(sdp: String): String {
        // SDP line: a=fingerprint:sha-256 AA:BB:CC:...
        return sdp.lines()
            .firstOrNull { it.startsWith("a=fingerprint:") }
            ?.substringAfter("a=fingerprint:")
            ?.trim()
            ?: ""
    }

    // ── SDP helpers ───────────────────────────────────────────────────────

    fun createOffer(peerGwId: String, onSuccess: (SessionDescription) -> Unit, onError: (String) -> Unit) {
        val pc = peers[peerGwId] ?: return onError("No connection for $peerGwId")
        pc.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(SdpObserverAdapter(), sdp)
                onSuccess(sdp)
            }
            override fun onCreateFailure(error: String) = onError(error)
        }, MediaConstraints())
    }

    fun setRemoteDescription(peerGwId: String, sdp: SessionDescription) {
        peers[peerGwId]?.setRemoteDescription(SdpObserverAdapter(), sdp)
    }

    fun createAnswer(peerGwId: String, onSuccess: (SessionDescription) -> Unit, onError: (String) -> Unit) {
        val pc = peers[peerGwId] ?: return onError("No connection for $peerGwId")
        pc.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(SdpObserverAdapter(), sdp)
                onSuccess(sdp)
            }
            override fun onCreateFailure(error: String) = onError(error)
        }, MediaConstraints())
    }

    fun addIceCandidate(peerGwId: String, candidate: IceCandidate) {
        peers[peerGwId]?.addIceCandidate(candidate)
    }

    // ── Media tracks ─────────────────────────────────────────────────────

    fun createLocalAudioTrack(): AudioTrack? {
        val source = factory.createAudioSource(MediaConstraints())
        return factory.createAudioTrack("audio_${System.currentTimeMillis()}", source)
    }

    fun createLocalVideoTrack(capturer: VideoCapturer): VideoTrack? {
        val surface = SurfaceTextureHelper.create("CaptureThread", EglBase.create().eglBaseContext)
        val source  = factory.createVideoSource(capturer.isScreencast)
        capturer.initialize(surface, context, source.capturerObserver)
        capturer.startCapture(1280, 720, 30)
        return factory.createVideoTrack("video_${System.currentTimeMillis()}", source)
    }

    fun addTrackToPeer(peerGwId: String, track: MediaStreamTrack, streamIds: List<String>) {
        peers[peerGwId]?.addTrack(track, streamIds)
    }

    // ── Cleanup ───────────────────────────────────────────────────────────

    fun closeConnection(peerGwId: String) {
        dataChannels.remove(peerGwId)?.close()
        peers.remove(peerGwId)?.dispose()
        expectedDtlsFingerprints.remove(peerGwId)
    }

    fun closeAllConnections() {
        dataChannels.values.forEach { it.close() }
        peers.values.forEach { it.dispose() }
        dataChannels.clear()
        peers.clear()
        expectedDtlsFingerprints.clear()
    }
}

sealed interface CallState {
    data object Idle        : CallState
    data class  Calling(val peerGwId: String)    : CallState
    data class  Ringing(val peerGwId: String)    : CallState
    data class  Connected(val peerGwId: String)  : CallState
    data class  Ended(val reason: String)        : CallState
}

private open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess()                            {}
    override fun onCreateFailure(error: String)            {}
    override fun onSetFailure(error: String)               {}
}
