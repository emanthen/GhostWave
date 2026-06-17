package com.ghostwave.app.messaging

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues WorkManager jobs for messages that couldn't be delivered over P2P.
 * WorkManager retries with exponential back-off across reboots.
 */
@Singleton
class OfflineMessageQueue @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun enqueue(messageId: String, contactId: String, peerGwId: String) {
        val data = workDataOf(
            MessageDeliveryWorker.KEY_MESSAGE_ID  to messageId,
            MessageDeliveryWorker.KEY_CONTACT_ID  to contactId,
            MessageDeliveryWorker.KEY_PEER_GW_ID  to peerGwId,
        )

        val request = OneTimeWorkRequestBuilder<MessageDeliveryWorker>()
            .setInputData(data)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS,
            )
            .addTag(TAG_DELIVERY)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "deliver_$messageId",
                ExistingWorkPolicy.KEEP,
                request,
            )
    }

    fun cancelForMessage(messageId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("deliver_$messageId")
    }

    companion object {
        const val TAG_DELIVERY = "message_delivery"
    }
}
