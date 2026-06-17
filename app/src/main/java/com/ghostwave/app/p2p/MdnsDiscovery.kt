package com.ghostwave.app.p2p

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG          = "MdnsDiscovery"
private const val SERVICE_TYPE = "_ghostwave._tcp."
private const val ATTR_GW_ID   = "gw_id"
private const val ATTR_PORT    = "port"

/**
 * LAN peer discovery via Android NSD (mDNS / DNS-SD).
 *
 * Each GhostWave device advertises itself as:
 *   service type:  _ghostwave._tcp.
 *   service name:  GW-XXXX-XXXX   (the local GW-ID)
 *   TXT attributes: gw_id=GW-XXXX-XXXX, port=<signaling port>
 *
 * Discovery callback updates [discoveredPeers] — the UI and WebRTC signaling
 * layer observe this flow to initiate connections to nearby peers.
 *
 * Security note: mDNS reveals the GW-ID on the local network. This is
 * intentional — the GW-ID is a public identifier. Actual message content
 * is always Signal-encrypted before transmission.
 */
@Singleton
class MdnsDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener:    NsdManager.DiscoveryListener?    = null

    private val _discoveredPeers = MutableStateFlow<Map<String, DiscoveredPeer>>(emptyMap())
    val discoveredPeers: StateFlow<Map<String, DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    // ── Advertising ───────────────────────────────────────────────────────

    fun startAdvertising(localGwId: String, signalingPort: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = localGwId
            serviceType = SERVICE_TYPE
            port        = signalingPort
            setAttribute(ATTR_GW_ID, localGwId)
            setAttribute(ATTR_PORT,  signalingPort.toString())
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "Registered: ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.e(TAG, "Registration failed: $code")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "Unregistered: ${info.serviceName}")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.e(TAG, "Unregistration failed: $code")
            }
        }
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun stopAdvertising() {
        registrationListener?.let {
            try { nsdManager.unregisterService(it) } catch (_: Exception) {}
            registrationListener = null
        }
    }

    // ── Discovery ─────────────────────────────────────────────────────────

    fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {
                Log.i(TAG, "Discovery started for $type")
            }
            override fun onDiscoveryStopped(type: String) {
                Log.i(TAG, "Discovery stopped for $type")
            }
            override fun onStartDiscoveryFailed(type: String, code: Int) {
                Log.e(TAG, "Start discovery failed: $code")
            }
            override fun onStopDiscoveryFailed(type: String, code: Int) {
                Log.e(TAG, "Stop discovery failed: $code")
            }
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType.contains("_ghostwave._tcp")) {
                    resolveService(info)
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                val gwId = info.serviceName
                _discoveredPeers.value = _discoveredPeers.value - gwId
                Log.i(TAG, "Peer lost: $gwId")
            }
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try { nsdManager.stopServiceDiscovery(it) } catch (_: Exception) {}
            discoveryListener = null
        }
        _discoveredPeers.value = emptyMap()
    }

    private fun resolveService(info: NsdServiceInfo) {
        nsdManager.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
                Log.w(TAG, "Resolve failed for ${info.serviceName}: $code")
            }
            override fun onServiceResolved(info: NsdServiceInfo) {
                val gwId = info.attributes[ATTR_GW_ID]
                    ?.let { String(it) } ?: info.serviceName
                val port = info.attributes[ATTR_PORT]
                    ?.let { String(it).toIntOrNull() } ?: info.port
                val host = info.host?.hostAddress ?: return

                val peer = DiscoveredPeer(gwId = gwId, host = host, port = port)
                _discoveredPeers.value = _discoveredPeers.value + (gwId to peer)
                Log.i(TAG, "Peer discovered: $gwId @ $host:$port")
            }
        })
    }
}

data class DiscoveredPeer(
    val gwId: String,
    val host: String,
    val port: Int,
)
