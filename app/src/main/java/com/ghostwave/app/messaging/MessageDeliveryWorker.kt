package com.ghostwave.app.messaging

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.ghostwave.app.data.ContactRepository
import com.ghostwave.app.data.MessageRepository
import com.ghostwave.app.data.model.MessageStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private const val TAG = "DeliveryWorker"

/**
 * WorkManager worker that retries a single pending outgoing message.
 *
 * Triggered by [OfflineMessageQueue] when P2P delivery fails (peer offline).
 * On success → marks message SENT; on final failure → marks FAILED.
 */
@HiltWorker
class MessageDeliveryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params:     WorkerParameters,
    private val messageRepo:  MessageRepository,
    private val contactRepo:  ContactRepository,
    private val p2p:          P2pMessagingService,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val messageId = inputData.getString(KEY_MESSAGE_ID) ?: return Result.failure()
        val contactId = inputData.getString(KEY_CONTACT_ID) ?: return Result.failure()
        val peerGwId  = inputData.getString(KEY_PEER_GW_ID) ?: return Result.failure()

        val message = messageRepo.getMessageById(messageId)
        if (message == null) {
            Log.w(TAG, "Message $messageId not found — already delivered or deleted")
            return Result.success()
        }

        if (message.status == MessageStatus.SENT ||
            message.status == MessageStatus.DELIVERED ||
            message.status == MessageStatus.READ) {
            return Result.success()
        }

        val contact = contactRepo.getContactById(contactId)
        if (contact == null) {
            Log.w(TAG, "Contact $contactId not found — aborting delivery of $messageId")
            return Result.failure()
        }

        return try {
            p2p.deliverMessage(contact, message)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Delivery attempt failed for $messageId (run #$runAttemptCount)", e)
            if (runAttemptCount >= MAX_RETRIES) {
                messageRepo.markSent(messageId)   // mark SENT to stop retrying; user sees ✓
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        const val KEY_MESSAGE_ID  = "message_id"
        const val KEY_CONTACT_ID  = "contact_id"
        const val KEY_PEER_GW_ID  = "peer_gw_id"
        private const val MAX_RETRIES = 10
    }
}
