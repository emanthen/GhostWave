package com.ghostwave.app.data.db

import androidx.room.TypeConverter
import com.ghostwave.app.data.model.CallDirection
import com.ghostwave.app.data.model.CallStatus
import com.ghostwave.app.data.model.CallType
import com.ghostwave.app.data.model.MessageDirection
import com.ghostwave.app.data.model.MessageStatus
import com.ghostwave.app.data.model.MessageType

/**
 * Room TypeConverters for all enum types used in GhostWave entities.
 *
 * Enums are stored as their name() strings rather than ordinals.
 * String storage survives reordering of enum entries without corrupting
 * existing rows — a common footgun with ordinal-based storage.
 */
class Converters {

    // ── MessageDirection ──────────────────────────────────────────────────
    @TypeConverter fun messageDirectionToString(d: MessageDirection): String = d.name
    @TypeConverter fun stringToMessageDirection(s: String): MessageDirection  =
        MessageDirection.valueOf(s)

    // ── MessageStatus ─────────────────────────────────────────────────────
    @TypeConverter fun messageStatusToString(s: MessageStatus): String = s.name
    @TypeConverter fun stringToMessageStatus(s: String): MessageStatus  =
        MessageStatus.valueOf(s)

    // ── MessageType ───────────────────────────────────────────────────────
    @TypeConverter fun messageTypeToString(t: MessageType): String = t.name
    @TypeConverter fun stringToMessageType(s: String): MessageType  =
        MessageType.valueOf(s)

    // ── CallType ──────────────────────────────────────────────────────────
    @TypeConverter fun callTypeToString(t: CallType): String = t.name
    @TypeConverter fun stringToCallType(s: String): CallType  =
        CallType.valueOf(s)

    // ── CallDirection ─────────────────────────────────────────────────────
    @TypeConverter fun callDirectionToString(d: CallDirection): String = d.name
    @TypeConverter fun stringToCallDirection(s: String): CallDirection  =
        CallDirection.valueOf(s)

    // ── CallStatus ────────────────────────────────────────────────────────
    @TypeConverter fun callStatusToString(s: CallStatus): String = s.name
    @TypeConverter fun stringToCallStatus(s: String): CallStatus  =
        CallStatus.valueOf(s)
}
