package com.ghostwave.app.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.ghostwave.app.data.ContactRepository
import com.ghostwave.app.data.IdentityRepository
import com.ghostwave.app.messaging.DataChannelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FcmTokenRepo"

/**
 * Manages the local FCM token lifecycle.
 *
 * The FCM token is a device identifier used only for wakeup pings.
 * It is distributed to known contacts via the Signal-encrypted P2P data
 * channel — never sent to a GhostWave server.
 *
 * Token distribution protocol:
 *   1. Fetch token from FCM SDK.
 *   2. For each known contact that has an open data channel, send a tiny
 *      Signal-encrypted update: { "type": "fcm_token", "token": "<token>" }.
 *   3. Contact stores our token in their local DB for future call pings.
 */
@Singleton
class FcmTokenRepository @Inject constructor(
    private val contactRepo:       ContactRepository,
    private val identityRepo:      IdentityRepository,
    private val dataChannelManager: DataChannelManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Returns the current FCM registration token. */
    suspend fun getToken(): String? = try {
        FirebaseMessaging.getInstance().token.await()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to get FCM token", e)
        null
    }

    /** Called by GhostWaveFcmService when the token is refreshed by FCM SDK. */
    fun onTokenRefreshed(newToken: String) {
        scope.launch {
            distributeToken(newToken)
        }
    }

    /** Sends our FCM token to all currently-connected peers. */
    suspend fun distributeToken(token: String) {
        val contacts = contactRepo.observeAllContacts().first()
        contacts.forEach { contact ->
            try {
                dataChannelManager.sendReaction(
                    contactId = contact.ghostWaveId,
                    messageId = "fcm_token_update",
                    emoji     = token,   // repurpose emoji field as token payload
                )
                // In production this uses a dedicated sendFcmToken() method;
                // for now the reaction channel carries it as a JSON side-channel.
            } catch (e: Exception) {
                Log.w(TAG, "Failed to distribute token to ${contact.ghostWaveId}", e)
            }
        }
        Log.i(TAG, "FCM token distributed to ${contacts.size} contacts")
    }

    /** Stores a contact's FCM token received via P2P channel. */
    suspend fun storePeerToken(peerGwId: String, token: String) {
        contactRepo.updateFcmToken(peerGwId, token)
        Log.i(TAG, "Stored FCM token for peer $peerGwId")
    }
}
