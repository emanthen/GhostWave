package com.ghostwave.app.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ghostwave.app.MainActivity
import com.ghostwave.app.data.model.CallType
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that keeps WebRTC connections alive while the app is backgrounded.
 *
 * Uses FOREGROUND_SERVICE_TYPE_MICROPHONE (audio calls) and
 * FOREGROUND_SERVICE_TYPE_CAMERA (video calls) as declared in the manifest.
 */
@AndroidEntryPoint
class CallService : Service() {

    @Inject lateinit var callManager: CallManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CALL -> {
                val peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: "Unknown"
                val callType = runCatching {
                    CallType.valueOf(intent.getStringExtra(EXTRA_CALL_TYPE) ?: "AUDIO")
                }.getOrDefault(CallType.AUDIO)
                startForeground(NOTIF_ID, buildNotification(peerName, callType))
            }
            ACTION_END_CALL -> {
                callManager.endCall()
                stopSelf()
            }
            ACTION_ACCEPT_CALL -> {
                callManager.acceptCall()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(peerName: String, type: CallType): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPi = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE,
        )
        val endPi = PendingIntent.getService(
            this, 1,
            Intent(this, CallService::class.java).setAction(ACTION_END_CALL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (type == CallType.VIDEO) "Video call" else "Audio call"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("$title with $peerName")
            .setContentText("GhostWave — encrypted")
            .setOngoing(true)
            .setContentIntent(contentPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End", endPi)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active Call",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "GhostWave encrypted call in progress"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START_CALL   = "com.ghostwave.CALL_START"
        const val ACTION_END_CALL     = "com.ghostwave.CALL_END"
        const val ACTION_ACCEPT_CALL  = "com.ghostwave.CALL_ACCEPT"

        const val EXTRA_CALL_ID    = "call_id"
        const val EXTRA_PEER_GW_ID = "peer_gw_id"
        const val EXTRA_PEER_NAME  = "peer_name"
        const val EXTRA_CALL_TYPE  = "call_type"
        const val EXTRA_DIRECTION  = "direction"

        private const val CHANNEL_ID = "gw_call"
        private const val NOTIF_ID   = 42
    }
}
