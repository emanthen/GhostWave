package com.ghostwave.app.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ghostwave.app.MainActivity
import com.ghostwave.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_CALLS     = "gw_calls"
private const val CHANNEL_INCOMING  = "gw_incoming_calls"
private const val NOTIF_ACTIVE_CALL = 1001
private const val NOTIF_INCOMING    = 1002

/**
 * Creates and manages all call-related notifications.
 *
 *   - [buildActiveCallNotification]: persistent ongoing notification while a call is live
 *     (required for foreground service — microphone/camera access needs this).
 *   - [showIncomingCallNotification]: full-screen intent + action buttons for incoming calls.
 *   - [cancelIncomingCallNotification]: dismissed when call is accepted, declined, or timed out.
 */
@Singleton
class CallNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notifManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    // ── Active call (foreground service notification) ─────────────────────────

    fun buildActiveCallNotification(
        peerName:  String,
        callType:  String,   // "Audio" | "Video"
    ): Notification {
        val endIntent = PendingIntent.getService(
            context, 0,
            Intent(context, CallService::class.java).apply {
                action = CallService.ACTION_END_CALL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("$callType call with $peerName")
            .setContentText("Tap to return to call")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(android.R.drawable.ic_delete, "End", endIntent)
            .build()
    }

    // ── Incoming call (full-screen intent) ────────────────────────────────────

    fun showIncomingCallNotification(
        peerName: String,
        peerGwId: String,
        callId:   String,
        callType: String,
    ) {
        val acceptIntent = PendingIntent.getService(
            context, 1,
            Intent(context, CallService::class.java).apply {
                action = CallService.ACTION_ACCEPT_CALL
                putExtra("call_id",   callId)
                putExtra("peer_gw_id", peerGwId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val declineIntent = PendingIntent.getService(
            context, 2,
            Intent(context, CallService::class.java).apply {
                action = CallService.ACTION_END_CALL
                putExtra("call_id", callId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val fullScreenIntent = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("incoming_call",  true)
                putExtra("peer_gw_id",    peerGwId)
                putExtra("call_id",       callId)
                putExtra("call_type",     callType)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming $callType call")
            .setContentText(peerName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(android.R.drawable.ic_menu_call, "Accept", acceptIntent)
            .addAction(android.R.drawable.ic_delete,   "Decline", declineIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        notifManager.notify(NOTIF_INCOMING, notification)
    }

    fun cancelIncomingCallNotification() {
        notifManager.cancel(NOTIF_INCOMING)
    }

    fun cancelActiveCallNotification() {
        notifManager.cancel(NOTIF_ACTIVE_CALL)
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun createChannels() {
        val callsChannel = NotificationChannel(
            CHANNEL_CALLS,
            "Active calls",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Shown during an active voice or video call" }

        val incomingChannel = NotificationChannel(
            CHANNEL_INCOMING,
            "Incoming calls",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description   = "Alerts for incoming calls"
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        notifManager.createNotificationChannels(listOf(callsChannel, incomingChannel))
    }
}
