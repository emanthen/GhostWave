package com.ghostwave.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ghostwave.app.MainActivity
import com.ghostwave.app.call.CallManager
import com.ghostwave.app.call.CallService
import com.ghostwave.app.data.model.CallType
import com.ghostwave.app.messaging.OfflineMessageQueue
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG              = "GhostWaveFCM"
private const val CHANNEL_INCOMING = "gw_incoming"

/**
 * Receives FCM data messages. GhostWave uses FCM ONLY as a wakeup mechanism —
 * NO message content, call content, or user data appears in the FCM payload.
 *
 * Allowed payload types:
 *   { "type": "ping", "from": "<GW-ID>" }          — new message waiting
 *   { "type": "call", "from": "<GW-ID>",
 *     "call_id": "<uuid>", "call_type": "AUDIO" }  — incoming call ring
 *
 * On "ping": wake P2P layer → peer connects → pulls encrypted messages via data channel.
 * On "call": show incoming-call notification with Accept / Decline.
 * NEVER decrypt or log the GW-ID in prod builds.
 */
@AndroidEntryPoint
class GhostWaveFcmService : FirebaseMessagingService() {

    @Inject lateinit var fcmTokenRepo:    FcmTokenRepository
    @Inject lateinit var callManager:     CallManager
    @Inject lateinit var offlineQueue:    OfflineMessageQueue

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed")
        // Distribute new token to known contacts via the P2P data channel.
        // FcmTokenRepository handles the async broadcast.
        fcmTokenRepo.onTokenRefreshed(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type   = message.data["type"] ?: return
        val fromGw = message.data["from"] ?: return

        Log.d(TAG, "FCM wakeup: type=$type")   // never log fromGw in prod

        when (type) {
            "ping" -> handlePing(fromGw)
            "call" -> handleCall(
                fromGwId  = fromGw,
                callId    = message.data["call_id"]   ?: return,
                callType  = message.data["call_type"] ?: "AUDIO",
            )
            else   -> Log.w(TAG, "Unknown FCM type: $type")
        }
    }

    // ── Ping: peer has messages waiting ──────────────────────────────────

    private fun handlePing(fromGwId: String) {
        // The P2P layer reconnects to the peer when the app resumes.
        // WorkManager already has the pending delivery jobs — they will
        // fire automatically once the network constraint is satisfied.
        // No action needed here beyond waking the process.
        Log.d(TAG, "Ping wakeup received")
    }

    // ── Incoming call notification ────────────────────────────────────────

    private fun handleCall(fromGwId: String, callId: String, callType: String) {
        val type = runCatching { CallType.valueOf(callType) }.getOrDefault(CallType.AUDIO)
        callManager.onIncomingCall(callId, fromGwId, type)

        val acceptIntent = Intent(this, CallService::class.java).apply {
            action = CallService.ACTION_ACCEPT_CALL
            putExtra(CallService.EXTRA_CALL_ID, callId)
            putExtra(CallService.EXTRA_PEER_GW_ID, fromGwId)
        }
        val declineIntent = Intent(this, CallService::class.java).apply {
            action = CallService.ACTION_END_CALL
        }

        val acceptPi  = PendingIntent.getService(this, 1, acceptIntent, PendingIntent.FLAG_IMMUTABLE)
        val declinePi = PendingIntent.getService(this, 2, declineIntent, PendingIntent.FLAG_IMMUTABLE)
        val openPi    = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE)

        val title = if (type == CallType.VIDEO) "Incoming video call" else "Incoming call"

        val notif = NotificationCompat.Builder(this, CHANNEL_INCOMING)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(title)
            .setContentText("GhostWave encrypted call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setFullScreenIntent(openPi, true)
            .addAction(android.R.drawable.ic_menu_call,   "Accept",  acceptPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePi)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(callId.hashCode(), notif)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_INCOMING, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "GhostWave incoming call alerts"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
