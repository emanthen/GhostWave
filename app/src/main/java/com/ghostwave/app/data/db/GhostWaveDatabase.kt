package com.ghostwave.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ghostwave.app.data.model.CallRecord
import com.ghostwave.app.data.model.Contact
import com.ghostwave.app.data.model.Message
import com.ghostwave.app.data.model.SignalIdentityKey
import com.ghostwave.app.data.model.SignalPreKey
import com.ghostwave.app.data.model.SignalSession
import com.ghostwave.app.data.model.SignalSignedPreKey

/**
 * GhostWave's single Room database, encrypted at rest with SQLCipher AES-256.
 *
 * Encryption setup:
 *   The database file is opened via net.sqlcipher.database.SupportFactory,
 *   which is created in [DatabaseModule] with a passphrase derived from the
 *   Android Keystore (see [DatabasePassphraseManager]).
 *
 *   This means:
 *     - The SQLite file on disk is fully AES-256-CBC encrypted
 *     - The passphrase never touches SharedPreferences or DataStore unencrypted
 *     - Even with physical device access (non-rooted), messages are unreadable
 *
 * Migration policy:
 *   destructiveMigrationFrom is NOT used — all migrations are additive.
 *   Each new step that adds columns or tables provides an explicit Migration object
 *   registered in DatabaseModule. Schema version is incremented with each change.
 *
 * Version history:
 *   1 → initial schema (contacts, messages, call_records, signal_* tables)
 */
@Database(
    entities = [
        Contact::class,
        Message::class,
        CallRecord::class,
        SignalSession::class,
        SignalPreKey::class,
        SignalSignedPreKey::class,
        SignalIdentityKey::class,
    ],
    version  = 1,
    exportSchema = true,     // generates JSON schema files for migration verification
)
@TypeConverters(Converters::class)
abstract class GhostWaveDatabase : RoomDatabase() {

    abstract fun contactDao():           ContactDao
    abstract fun messageDao():           MessageDao
    abstract fun callRecordDao():        CallRecordDao
    abstract fun signalSessionDao():     SignalSessionDao
    abstract fun signalPreKeyDao():      SignalPreKeyDao
    abstract fun signalSignedPreKeyDao(): SignalSignedPreKeyDao
    abstract fun signalIdentityKeyDao(): SignalIdentityKeyDao

    companion object {
        const val DATABASE_NAME = "ghostwave.db"
    }
}
