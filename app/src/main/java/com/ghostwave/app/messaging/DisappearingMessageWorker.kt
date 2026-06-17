package com.ghostwave.app.messaging

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.ghostwave.app.data.MessageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private const val TAG = "DisappearWorker"

/**
 * Periodic worker that hard-deletes messages whose [expiresAt] has passed.
 * Scheduled every 15 minutes by [BootReceiver] and on first app launch.
 */
@HiltWorker
class DisappearingMessageWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params:     WorkerParameters,
    private val messageRepo: MessageRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val deleted = messageRepo.deleteExpiredMessages()
        if (deleted > 0) Log.i(TAG, "Deleted $deleted expired messages")
        return Result.success()
    }
}
