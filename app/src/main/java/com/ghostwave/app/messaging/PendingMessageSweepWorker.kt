package com.ghostwave.app.messaging

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.ghostwave.app.data.ContactRepository
import com.ghostwave.app.data.MessageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private const val TAG = "SweepWorker"

/**
 * Sweeps the DB for all PENDING/FAILED outgoing messages and re-enqueues
 * a [MessageDeliveryWorker] for each one. Used after boot or after connectivity
 * is restored.
 */
@HiltWorker
class PendingMessageSweepWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params:     WorkerParameters,
    private val messageRepo: MessageRepository,
    private val contactRepo: ContactRepository,
    private val queue:       OfflineMessageQueue,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val pending = messageRepo.getPendingOutbound()
        Log.i(TAG, "Sweeping ${pending.size} pending messages")

        pending.forEach { msg ->
            val contact = contactRepo.getContactById(msg.contactId) ?: return@forEach
            queue.enqueue(
                messageId = msg.id,
                contactId = msg.contactId,
                peerGwId  = contact.ghostWaveId,
            )
        }
        return Result.success()
    }
}
