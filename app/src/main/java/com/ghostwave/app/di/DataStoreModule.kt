package com.ghostwave.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Single DataStore instance for the entire app — using the extension property
// approach is idiomatic Kotlin, but we use the factory form here so Hilt
// can manage the singleton lifecycle correctly.
private val Context.prefsDataStore by preferencesDataStore(name = "ghostwave_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    /**
     * Provides a singleton [DataStore<Preferences>] backed by the
     * "ghostwave_prefs" file in the app's internal data directory.
     *
     * The DataStore itself is NOT encrypted — that responsibility belongs to
     * the callers (IdentityRepository, SettingsRepository) which use
     * Keystore-backed AES-256-GCM to protect sensitive values before writing.
     *
     * Non-sensitive settings (display name, disappearing-message timer, etc.)
     * are stored in plaintext and are acceptable to have unencrypted because
     * they are also visible in the app's UI.
     */
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.prefsDataStore
}
