package com.ghostwave.app.p2p

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory registry of all currently reachable peers.
 *
 * A peer is added when discovered via:
 *   - mDNS ([MdnsDiscovery]) — LAN peers
 *   - Direct connection after FCM wakeup — remote peers
 *
 * A peer is removed when:
 *   - mDNS unregisters them
 *   - WebRTC connection closes and no reconnection attempt succeeds
 *
 * This registry does NOT persist across process restarts — it is rebuilt
 * on each launch from mDNS discovery and active WebRTC connections.
 */
@Singleton
class PeerRegistry @Inject constructor() {

    private val _peers = MutableStateFlow<Map<String, PeerInfo>>(emptyMap())
    val peers: StateFlow<Map<String, PeerInfo>> = _peers.asStateFlow()

    fun addOrUpdate(peer: PeerInfo) {
        _peers.update { current -> current + (peer.gwId to peer) }
    }

    fun remove(gwId: String) {
        _peers.update { current -> current - gwId }
    }

    fun get(gwId: String): PeerInfo? = _peers.value[gwId]

    fun isReachable(gwId: String): Boolean = _peers.value.containsKey(gwId)

    fun updateConnectionState(gwId: String, state: PeerConnectionState) {
        _peers.update { current ->
            val existing = current[gwId] ?: return@update current
            current + (gwId to existing.copy(connectionState = state))
        }
    }

    fun setFcmToken(gwId: String, token: String) {
        _peers.update { current ->
            val existing = current[gwId] ?: PeerInfo(gwId = gwId, fcmToken = token)
            current + (gwId to existing.copy(fcmToken = token))
        }
    }

    fun all(): List<PeerInfo> = _peers.value.values.toList()
}

data class PeerInfo(
    val gwId:            String,
    val displayName:     String       = "",
    val host:            String       = "",
    val port:            Int          = 0,
    val fcmToken:        String?      = null,
    val dtlsFingerprint: String?      = null,
    val connectionState: PeerConnectionState = PeerConnectionState.DISCONNECTED,
    val discoveredVia:   DiscoverySource     = DiscoverySource.UNKNOWN,
)

enum class PeerConnectionState { DISCONNECTED, CONNECTING, CONNECTED, FAILED }
enum class DiscoverySource      { MDNS, FCM_WAKEUP, MANUAL, UNKNOWN }
