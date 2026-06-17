package com.ghostwave.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ghostwave.app.data.model.CallRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallRecord(record: CallRecord)

    @Query("UPDATE call_records SET status = :status, endedAt = :endedAt, durationSeconds = :durationSecs WHERE id = :id")
    suspend fun updateCallEnd(id: String, status: String, endedAt: Long, durationSecs: Int)

    @Query("SELECT * FROM call_records WHERE contactId = :contactId ORDER BY startedAt DESC")
    fun observeCallRecordsForContact(contactId: String): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecentCalls(limit: Int = 50): Flow<List<CallRecord>>

    @Query("SELECT COUNT(*) FROM call_records WHERE status = 'MISSED' AND direction = 'INCOMING'")
    fun observeMissedCallCount(): Flow<Int>

    @Query("DELETE FROM call_records WHERE contactId = :contactId")
    suspend fun deleteCallRecordsForContact(contactId: String)
}
