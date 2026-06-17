package com.ghostwave.app.messaging

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Background service for delivering queued messages when a peer comes online.
 * Replaced by WorkManager in Step 14.
 */
class MessageDeliveryService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
