package com.ghostwave.app.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Re-enqueues offline message delivery after device reboot.
 *
 * WorkManager normally survives reboots via its own JobScheduler integration.
 * This receiver is a belt-and-suspenders fallback for devices where
 * WorkManager jobs do not persist across a cold reboot.
 *
 * It also schedules the periodic disappearing-message sweeper.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        Log.i("BootReceiver", "Boot completed — re-scheduling WorkManager jobs")

        val workManager = WorkManager.getInstance(context)

        // 1. Re-trigger delivery of any PENDING/FAILED outgoing messages.
        //    The individual per-message workers were already persisted by WorkManager;
        //    this bulk worker sweeps for any that slipped through.
        val deliverySweep = OneTimeWorkRequestBuilder<PendingMessageSweepWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(15, TimeUnit.SECONDS)   // give the network time to come up
            .build()

        workManager.enqueueUniqueWork(
            "boot_delivery_sweep",
            ExistingWorkPolicy.KEEP,
            deliverySweep,
        )

        // 2. Periodic disappearing-message sweeper (runs every 15 minutes).
        val expirySweep = PeriodicWorkRequestBuilder<DisappearingMessageWorker>(
            15, TimeUnit.MINUTES,
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "disappearing_message_sweep",
            ExistingPeriodicWorkPolicy.KEEP,
            expirySweep,
        )

        Log.i("BootReceiver", "WorkManager jobs scheduled")
    }
}
