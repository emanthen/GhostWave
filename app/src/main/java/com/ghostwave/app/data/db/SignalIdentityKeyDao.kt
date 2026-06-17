package com.ghostwave.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ghostwave.app.data.model.SignalIdentityKey

@Dao
interface SignalIdentityKeyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveIdentityKey(key: SignalIdentityKey)   // sync — called from Signal store interface

    @Query("SELECT * FROM signal_identity_keys WHERE gwId = :gwId")
    fun getIdentityKey(gwId: String): SignalIdentityKey?

    @Query("SELECT * FROM signal_identity_keys")
    fun getAllIdentityKeys(): List<SignalIdentityKey>

    @Query("UPDATE signal_identity_keys SET trustLevel = :level WHERE gwId = :gwId")
    suspend fun updateTrustLevel(gwId: String, level: Int)

    @Query("DELETE FROM signal_identity_keys WHERE gwId = :gwId")
    suspend fun deleteIdentityKey(gwId: String)
}
