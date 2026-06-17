package com.ghostwave.app.data

import android.content.Context
import androidx.work.*
import com.ghostwave.app.messaging.DisappearingMessageWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the disappearing-messages feature.
 *
 * A disappearing message has an [expiresAt] timestamp set at receipt/send time:
 *   expiresAt = now + contact.disappearingMessagesDurationSecs * 1000
 *
 * Deletion is done by [DisappearingMessageWorker] running every 15 minutes.
 * This class schedules the worker and provides helpers for UI.
 */
@Singleton
class DisappearingMessageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactRepo: ContactRepository,
    private val messageRepo: MessageRepository,
) {
    /**
     * Schedules the periodic sweeper. Call on app launch and after boot.
     */
    fun scheduleExpirySweep() {
        val request = PeriodicWorkRequestBuilder<DisappearingMessageWorker>(
            15, TimeUnit.MINUTES,
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "disappearing_message_sweep",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Changes the disappearing-messages timer for a conversation.
     * Immediately broadcasts the new setting to the peer via the data channel
     * (so both sides delete on the same schedule).
     */
    suspend fun setDurationForContact(contactId: String, durationSecs: Long?) {
        contactRepo.setDisappearingDuration(contactId, durationSecs)
    }

    /** Returns a human-readable label for a disappearing-messages duration. */
    fun formatDuration(secs: Long?): String = when (secs) {
        null      -> "Off"
        3_600L    -> "1 hour"
        86_400L   -> "24 hours"
        604_800L  -> "1 week"
        2_592_000L-> "30 days"
        else      -> "${secs}s"
    }

    val timerOptions: List<Pair<Long?, String>> = listOf(
        null       to "Off",
        3_600L     to "1 hour",
        86_400L    to "24 hours",
        604_800L   to "1 week",
        2_592_000L to "30 days",
    )
}
