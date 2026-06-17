package com.ghostwave.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ghostwave.app.data.model.SignalSignedPreKey

@Dao
interface SignalSignedPreKeyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun storeSignedPreKey(key: SignalSignedPreKey)   // sync

    @Query("SELECT * FROM signal_signed_prekeys WHERE signedPreKeyId = :id")
    fun loadSignedPreKey(id: Int): SignalSignedPreKey?

    @Query("SELECT * FROM signal_signed_prekeys")
    fun loadAllSignedPreKeys(): List<SignalSignedPreKey>

    @Query("SELECT EXISTS(SELECT 1 FROM signal_signed_prekeys WHERE signedPreKeyId = :id)")
    fun containsSignedPreKey(id: Int): Boolean

    @Query("DELETE FROM signal_signed_prekeys WHERE signedPreKeyId = :id")
    fun removeSignedPreKey(id: Int)

    /** Returns signed prekeys older than [thresholdMillis] — used for rotation cleanup. */
    @Query("SELECT * FROM signal_signed_prekeys WHERE createdAt < :thresholdMillis")
    suspend fun getSignedPreKeysOlderThan(thresholdMillis: Long): List<SignalSignedPreKey>
}
