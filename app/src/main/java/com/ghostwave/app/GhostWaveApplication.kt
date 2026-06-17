package com.ghostwave.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ghostwave.app.call.WebRtcManager
import com.ghostwave.app.data.DisappearingMessageManager
import com.ghostwave.app.messaging.DataChannelManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import net.sqlcipher.database.SQLiteDatabase
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Boot sequence:
 *  1. SQLCipher native library — must load before any Room DB opens
 *  2. WorkManager (manual init so Hilt can inject Workers)
 *  3. Disappearing-message sweeper (scheduled via WorkManager)
 *  4. WebRTC + DataChannel wiring — deferred until promo gate passes
 *     (see [onPromoUnlocked])
 *
 * Signal/IPFS/Calls are intentionally NOT initialised here.
 * They start only after the promo gate is verified.
 */
@HiltAndroidApp
class GhostWaveApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory:          HiltWorkerFactory
    @Inject lateinit var disappearingMsgManager: DisappearingMessageManager
    @Inject lateinit var webRtcManager:          WebRtcManager
    @Inject lateinit var dataChannelManager:     DataChannelManager

    /**
     * Global coroutine exception handler.
     * All ViewModelScope and application-level coroutines should use this
     * so uncaught exceptions are logged (and in future: Crashlytics-reported)
     * rather than silently crashing the process.
     */
    val globalExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught coroutine exception", throwable)
        // TODO(release): report to Crashlytics / Sentry here
    }

    override fun onCreate() {
        super.onCreate()

        // Step 1: Load SQLCipher native .so — must happen before any Room DB
        // instance is created anywhere in the process.
        SQLiteDatabase.loadLibs(this)

        // Step 2: Schedule periodic disappearing-message sweeper.
        // ExistingPeriodicWorkPolicy.KEEP is a no-op if already enqueued.
        disappearingMsgManager.scheduleExpirySweep()

        // WebRTC and DataChannel are wired in onPromoUnlocked(), not here,
        // because they must not initialise before the access gate is passed.
    }

    /**
     * Called by [com.ghostwave.app.ui.promo.PromoCodeViewModel] immediately
     * after the promo gate is successfully verified for the first time, AND
     * by [com.ghostwave.app.MainActivity] on every subsequent launch once the
     * device is already unlocked.
     *
     * Idempotent — safe to call multiple times.
     */
    fun onPromoUnlocked() {
        if (!webRtcManager.isInitialized) {
            webRtcManager.initialize()
            webRtcManager.dataChannelManager = dataChannelManager
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR
            )
            .build()

    companion object {
        private const val TAG = "GhostWaveApp"
    }
}
