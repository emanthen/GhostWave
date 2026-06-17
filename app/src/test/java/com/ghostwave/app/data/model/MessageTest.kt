package com.ghostwave.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * JVM unit tests for Message entity defaults and invariants.
 * No Android runtime needed — data class logic only.
 */
class MessageTest {

    private fun outgoingText(body: String = "hello"): Message = Message(
        id          = UUID.randomUUID().toString(),
        contactId   = "contact-1",
        senderGwId  = "GW-AAAA-BBBB",
        body        = body,
        direction   = MessageDirection.OUTGOING,
        timestamp   = System.currentTimeMillis(),
    )

    private fun incomingText(): Message = Message(
        id          = UUID.randomUUID().toString(),
        contactId   = "contact-1",
        senderGwId  = "GW-CCCC-DDDD",
        body        = "world",
        direction   = MessageDirection.INCOMING,
        timestamp   = System.currentTimeMillis(),
    )

    // ── Defaults ──────────────────────────────────────────────────────────

    @Test
    fun `default status is PENDING`() {
        assertEquals(MessageStatus.PENDING, outgoingText().status)
    }

    @Test
    fun `default type is TEXT`() {
        assertEquals(MessageType.TEXT, outgoingText().messageType)
    }

    @Test
    fun `default reactions is empty JSON object`() {
        assertEquals("{}", outgoingText().reactionsJson)
    }

    @Test
    fun `default media fields are null`() {
        val msg = outgoingText()
        assertNull(msg.mediaIpfsCid)
        assertNull(msg.mediaKeyBase64)
        assertNull(msg.mediaIvBase64)
        assertNull(msg.mediaMimeType)
        assertNull(msg.mediaFileSizeBytes)
        assertNull(msg.mediaLocalPath)
    }

    @Test
    fun `default expiry is null (never expires)`() {
        assertNull(outgoingText().expiresAt)
    }

    @Test
    fun `default isDeleted is false`() {
        assertFalse(outgoingText().isDeleted)
    }

    // ── Direction ─────────────────────────────────────────────────────────

    @Test
    fun `outgoing message has OUTGOING direction`() {
        assertEquals(MessageDirection.OUTGOING, outgoingText().direction)
    }

    @Test
    fun `incoming message has INCOMING direction`() {
        assertEquals(MessageDirection.INCOMING, incomingText().direction)
    }

    // ── Copy and mutation ─────────────────────────────────────────────────

    @Test
    fun `copy preserves all fields not explicitly changed`() {
        val original = outgoingText("original")
        val updated  = original.copy(status = MessageStatus.SENT)
        assertEquals(original.id,       updated.id)
        assertEquals(original.body,     updated.body)
        assertEquals(original.contactId, updated.contactId)
        assertEquals(MessageStatus.SENT, updated.status)
    }

    @Test
    fun `soft-delete pattern clears body and sets isDeleted`() {
        val msg     = outgoingText("secret content")
        val deleted = msg.copy(isDeleted = true, body = "")
        assertTrue(deleted.isDeleted)
        assertTrue(deleted.body.isEmpty())
        assertEquals(msg.id, deleted.id)   // id preserved for reply threading
    }

    // ── Media message ─────────────────────────────────────────────────────

    @Test
    fun `image message has media fields set`() {
        val msg = outgoingText().copy(
            messageType        = MessageType.IMAGE,
            mediaIpfsCid       = "QmFakeHash",
            mediaKeyBase64     = "base64key==",
            mediaIvBase64      = "base64iv==",
            mediaMimeType      = "image/jpeg",
            mediaFileSizeBytes = 102_400L,
        )
        assertEquals(MessageType.IMAGE,  msg.messageType)
        assertEquals("QmFakeHash",       msg.mediaIpfsCid)
        assertEquals("base64key==",      msg.mediaKeyBase64)
        assertEquals("image/jpeg",       msg.mediaMimeType)
    }

    // ── MessageStatus ordering ────────────────────────────────────────────

    @Test
    fun `MessageStatus enum values are all distinct`() {
        val values = MessageStatus.values()
        assertEquals(values.size, values.map { it.name }.toSet().size)
    }

    @Test
    fun `MessageType enum values are all distinct`() {
        val values = MessageType.values()
        assertEquals(values.size, values.map { it.name }.toSet().size)
    }

    // ── Disappearing messages ─────────────────────────────────────────────

    @Test
    fun `expiry timestamp is after creation timestamp`() {
        val now     = System.currentTimeMillis()
        val oneHour = 3_600_000L
        val msg     = outgoingText().copy(timestamp = now, expiresAt = now + oneHour)
        assertTrue("expiresAt must be after timestamp",
            msg.expiresAt!! > msg.timestamp)
    }
}
