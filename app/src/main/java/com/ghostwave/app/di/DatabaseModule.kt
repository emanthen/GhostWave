package com.ghostwave.app.di

import android.content.Context
import androidx.room.Room
import com.ghostwave.app.data.DatabasePassphraseManager
import com.ghostwave.app.data.db.CallRecordDao
import com.ghostwave.app.data.db.ContactDao
import com.ghostwave.app.data.db.GhostWaveDatabase
import com.ghostwave.app.data.db.MessageDao
import com.ghostwave.app.data.db.SignalIdentityKeyDao
import com.ghostwave.app.data.db.SignalPreKeyDao
import com.ghostwave.app.data.db.SignalSessionDao
import com.ghostwave.app.data.db.SignalSignedPreKeyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Builds the SQLCipher-encrypted Room database.
     *
     * [runBlocking] is acceptable here because this runs once at DI graph
     * construction time (before any UI is visible) and on the thread that
     * initialises the SingletonComponent — not the main thread.
     *
     * Passphrase lifecycle:
     *   1. [DatabasePassphraseManager] decrypts the 32-byte passphrase from DataStore.
     *   2. SQLCipher's [SupportFactory] receives it (copies internally).
     *   3. We ZERO the local byte array immediately — it must not linger in heap.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        passphraseManager: DatabasePassphraseManager,
    ): GhostWaveDatabase {
        // Load native SQLCipher libs before creating the factory
        SQLiteDatabase.loadLibs(context)

        val passphrase = runBlocking { passphraseManager.getOrCreatePassphrase() }
        val factory    = SupportFactory(passphrase)
        passphrase.fill(0)   // SECURITY: zero plaintext immediately after handoff

        return Room.databaseBuilder(
            context,
            GhostWaveDatabase::class.java,
            GhostWaveDatabase.DATABASE_NAME,
        )
            .openHelperFactory(factory)
            // No fallbackToDestructiveMigration — all migrations must be explicit
            .build()
    }

    // ── DAO bindings ──────────────────────────────────────────────────────
    // Hilt can inject DAOs directly by providing them via their database instance.

    @Provides fun provideContactDao(db: GhostWaveDatabase):           ContactDao           = db.contactDao()
    @Provides fun provideMessageDao(db: GhostWaveDatabase):           MessageDao           = db.messageDao()
    @Provides fun provideCallRecordDao(db: GhostWaveDatabase):        CallRecordDao        = db.callRecordDao()
    @Provides fun provideSignalSessionDao(db: GhostWaveDatabase):     SignalSessionDao     = db.signalSessionDao()
    @Provides fun provideSignalPreKeyDao(db: GhostWaveDatabase):      SignalPreKeyDao      = db.signalPreKeyDao()
    @Provides fun provideSignalSignedPreKeyDao(db: GhostWaveDatabase): SignalSignedPreKeyDao = db.signalSignedPreKeyDao()
    @Provides fun provideSignalIdentityKeyDao(db: GhostWaveDatabase): SignalIdentityKeyDao = db.signalIdentityKeyDao()
}
