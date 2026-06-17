package com.ghostwave.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ContactTest {

    private fun contact(gwId: String = "GW-AAAA-BBBB"): Contact = Contact(
        id              = UUID.randomUUID().toString(),
        ghostWaveId     = gwId,
        displayName     = "Alice",
        publicKeyBase64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        addedAt         = 1_000_000L,
    )

    @Test
    fun `default contact is not blocked`() {
        assertFalse(contact().isBlocked)
    }

    @Test
    fun `default contact is not verified`() {
        assertFalse(contact().isVerified)
    }

    @Test
    fun `default disappearing messages duration is null (off)`() {
        assertNull(contact().disappearingMessagesDurationSecs)
    }

    @Test
    fun `default unread count is zero`() {
        assertEquals(0, contact().unreadCount)
    }

    @Test
    fun `default muted is false`() {
        assertFalse(contact().isMuted)
    }

    @Test
    fun `blocking a contact sets isBlocked`() {
        val blocked = contact().copy(isBlocked = true)
        assertTrue(blocked.isBlocked)
    }

    @Test
    fun `verifying a contact sets isVerified`() {
        val verified = contact().copy(isVerified = true)
        assertTrue(verified.isVerified)
    }

    @Test
    fun `disappearing messages 1 hour = 3600 seconds`() {
        val c = contact().copy(disappearingMessagesDurationSecs = 3600L)
        assertEquals(3600L, c.disappearingMessagesDurationSecs)
    }

    @Test
    fun `two contacts with different GW-IDs are not equal`() {
        val a = contact("GW-AAAA-BBBB")
        val b = contact("GW-CCCC-DDDD")
        assertTrue(a.ghostWaveId != b.ghostWaveId)
    }

    @Test
    fun `muting contact sets isMuted and muteUntil`() {
        val muteEnd = System.currentTimeMillis() + 86_400_000L
        val muted   = contact().copy(isMuted = true, muteUntil = muteEnd)
        assertTrue(muted.isMuted)
        assertEquals(muteEnd, muted.muteUntil)
    }
}
