package com.ghostwave.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Root Hilt module.
 *
 * Steps 2–16 add providers for:
 *   SignalProtocolManager, KeyStoreManager, GhostWaveDatabase,
 *   WebRTCManager, IpfsManager, MessageRepository, etc.
 *
 * This file deliberately stays minimal so each step can add
 * exactly the bindings it introduces.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Exposes application context for injection into singletons
     * that need it (e.g. database builder, DataStore).
     * Hilt already provides @ApplicationContext automatically, but
     * this explicit binding makes it clear what is available.
     */
    @Provides
    @Singleton
    fun provideApplicationContext(
        @ApplicationContext context: Context,
    ): Context = context
}
