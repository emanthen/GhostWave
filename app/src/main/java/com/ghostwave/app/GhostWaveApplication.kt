package com.ghostwave.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ghostwave.app.call.WebRtcManager
import com.ghostwave.app.data.DisappearingMessageManager
import com.ghostwave.app.messaging.DataChannelManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class — bootstraps Hilt DI and WorkManager.
 *
 * WorkManager is initialised manually (not via the auto-init startup library)
 * so that Hilt can inject into Worker subclasses via HiltWorkerFactory.
 */
@HiltAndroidApp
class GhostWaveApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory:          HiltWorkerFactory
    @Inject lateinit var disappearingMsgManager: DisappearingMessageManager
    @Inject lateinit var webRtcManager:          WebRtcManager
    @Inject lateinit var dataChannelManager:     DataChannelManager

    override fun onCreate() {
        super.onCreate()

        // Initialise WebRTC PeerConnectionFactory (one-time global setup).
        webRtcManager.initialize()

        // Wire the circular reference: WebRtcManager dispatches incoming data
        // channel frames to DataChannelManager, but can't inject it directly
        // without a cycle. We set it here once both singletons are ready.
        webRtcManager.dataChannelManager = dataChannelManager

        // Schedule the periodic disappearing-message sweeper.
        // ExistingPeriodicWorkPolicy.KEEP means this is a no-op if already scheduled.
        disappearingMsgManager.scheduleExpirySweep()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG
                else android.util.Log.ERROR
            )
            .build()
}
