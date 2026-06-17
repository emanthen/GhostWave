package com.ghostwave.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ghostwave.app.data.model.SignalSession

@Dao
interface SignalSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun storeSession(session: SignalSession)  // sync — called from Signal store interface

    @Query("SELECT * FROM signal_sessions WHERE compositeId = :compositeId")
    fun loadSession(compositeId: String): SignalSession?

    @Query("SELECT EXISTS(SELECT 1 FROM signal_sessions WHERE compositeId = :compositeId)")
    fun containsSession(compositeId: String): Boolean

    @Query("DELETE FROM signal_sessions WHERE compositeId = :compositeId")
    fun deleteSession(compositeId: String)

    @Query("DELETE FROM signal_sessions WHERE gwId = :gwId")
    fun deleteAllSessionsForGwId(gwId: String)

    /** Returns device IDs for all sessions with a given peer (multi-device placeholder). */
    @Query("SELECT deviceId FROM signal_sessions WHERE gwId = :gwId AND deviceId != 1")
    fun getSubDeviceIds(gwId: String): List<Int>
}
