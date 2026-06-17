package com.ghostwave.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Persisted record of a completed, missed, or failed call. */
@Entity(
    tableName = "call_records",
    foreignKeys = [
        ForeignKey(
            entity        = Contact::class,
            parentColumns = ["id"],
            childColumns  = ["contactId"],
            onDelete      = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("contactId"),
        Index("startedAt"),
    ],
)
data class CallRecord(
    @PrimaryKey val id: String,
    val contactId: String,
    val callType: CallType,
    val direction: CallDirection,
    val status: CallStatus,
    val startedAt: Long,                // epoch millis
    val endedAt: Long?     = null,
    val durationSeconds: Int = 0,
)

enum class CallType      { AUDIO, VIDEO }
enum class CallDirection { OUTGOING, INCOMING }
enum class CallStatus    { CALLING, RINGING, CONNECTED, COMPLETED, MISSED, DECLINED, FAILED }
