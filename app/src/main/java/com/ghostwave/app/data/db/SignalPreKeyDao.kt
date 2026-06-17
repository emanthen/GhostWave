package com.ghostwave.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ghostwave.app.data.model.SignalPreKey

@Dao
interface SignalPreKeyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun storePreKey(preKey: SignalPreKey)   // sync — Signal store interface

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun storePreKeys(preKeys: List<SignalPreKey>)

    @Query("SELECT * FROM signal_prekeys WHERE preKeyId = :id")
    fun loadPreKey(id: Int): SignalPreKey?

    @Query("SELECT EXISTS(SELECT 1 FROM signal_prekeys WHERE preKeyId = :id)")
    fun containsPreKey(id: Int): Boolean

    @Query("DELETE FROM signal_prekeys WHERE preKeyId = :id")
    fun removePreKey(id: Int)

    @Query("SELECT COUNT(*) FROM signal_prekeys")
    suspend fun getPreKeyCount(): Int

    /** Highest stored prekey ID — used to calculate the next batch startId. */
    @Query("SELECT MAX(preKeyId) FROM signal_prekeys")
    suspend fun getMaxPreKeyId(): Int?
}
