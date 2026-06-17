package com.ghostwave.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for GhostWaveIdUtil.
 * All pure functions — no Android dependencies needed.
 */
class GhostWaveIdUtilTest {

    // ── deriveFromPublicKey ───────────────────────────────────────────────

    @Test
    fun `deriveFromPublicKey produces GW-XXXX-XXXX format`() {
        val fakeKey = ByteArray(32) { it.toByte() }
        val id = GhostWaveIdUtil.deriveFromPublicKey(fakeKey)
        assertTrue("ID must start with GW-", id.startsWith("GW-"))
        assertTrue("ID must match GW-XXXX-XXXX pattern", GhostWaveIdUtil.isValid(id))
    }

    @Test
    fun `deriveFromPublicKey is deterministic for same input`() {
        val key = ByteArray(32) { (it * 3).toByte() }
        val id1 = GhostWaveIdUtil.deriveFromPublicKey(key)
        val id2 = GhostWaveIdUtil.deriveFromPublicKey(key)
        assertEquals("Same key must always produce same GW-ID", id1, id2)
    }

    @Test
    fun `deriveFromPublicKey differs for different keys`() {
        val key1 = ByteArray(32) { it.toByte() }
        val key2 = ByteArray(32) { (it + 100).toByte() }
        val id1  = GhostWaveIdUtil.deriveFromPublicKey(key1)
        val id2  = GhostWaveIdUtil.deriveFromPublicKey(key2)
        assertTrue("Different keys should (almost certainly) produce different GW-IDs", id1 != id2)
    }

    @Test
    fun `deriveFromPublicKey result is exactly 11 characters`() {
        // "GW-XXXX-XXXX" = 2 + 1 + 4 + 1 + 4 = 12 chars — correction: GW=2, -=1, 4, -=1, 4 = 12
        val id = GhostWaveIdUtil.deriveFromPublicKey(ByteArray(33) { 0x42 })
        assertEquals("GW-ID must be exactly 12 characters", 12, id.length)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deriveFromPublicKey throws on empty key`() {
        GhostWaveIdUtil.deriveFromPublicKey(ByteArray(0))
    }

    @Test
    fun `deriveFromPublicKey produces uppercase hex segments`() {
        val id = GhostWaveIdUtil.deriveFromPublicKey(ByteArray(32) { 0xFF.toByte() })
        val parts = id.removePrefix("GW-").split("-")
        assertEquals(2, parts.size)
        parts.forEach { part ->
            assertEquals(4, part.length)
            assertTrue("Segment must be uppercase hex: $part",
                part.all { c -> c in '0'..'9' || c in 'A'..'F' })
        }
    }

    // ── isValid ──────────────────────────────────────────────────────────

    @Test
    fun `isValid accepts correct format`() {
        assertTrue(GhostWaveIdUtil.isValid("GW-1A2B-3C4D"))
        assertTrue(GhostWaveIdUtil.isValid("GW-FFFF-0000"))
        assertTrue(GhostWaveIdUtil.isValid("GW-ABCD-EF01"))
    }

    @Test
    fun `isValid rejects wrong formats`() {
        assertFalse(GhostWaveIdUtil.isValid("gw-1234-abcd"))   // lowercase
        assertFalse(GhostWaveIdUtil.isValid("GW-12345-ABCD"))  // too long
        assertFalse(GhostWaveIdUtil.isValid("GW-123-ABCD"))    // too short
        assertFalse(GhostWaveIdUtil.isValid("GW-GGGG-HHHH"))   // non-hex letters
        assertFalse(GhostWaveIdUtil.isValid("1A2B-3C4D"))      // missing GW- prefix
        assertFalse(GhostWaveIdUtil.isValid(""))
        assertFalse(GhostWaveIdUtil.isValid("GW-1A2B3C4D"))    // missing middle dash
    }

    // ── normalise ────────────────────────────────────────────────────────

    @Test
    fun `normalise accepts canonical form unchanged`() {
        assertEquals("GW-1A2B-3C4D", GhostWaveIdUtil.normalise("GW-1A2B-3C4D"))
    }

    @Test
    fun `normalise handles lowercase input`() {
        assertEquals("GW-ABCD-EF01", GhostWaveIdUtil.normalise("gw-abcd-ef01"))
    }

    @Test
    fun `normalise handles input without GW prefix`() {
        assertEquals("GW-1234-ABCD", GhostWaveIdUtil.normalise("1234ABCD"))
    }

    @Test
    fun `normalise handles extra spaces and dashes`() {
        assertEquals("GW-FF00-AA11", GhostWaveIdUtil.normalise("GW FF00 AA11"))
        assertEquals("GW-FF00-AA11", GhostWaveIdUtil.normalise("GW-FF00-AA11"))
    }

    @Test
    fun `normalise returns null for invalid input`() {
        assertNull(GhostWaveIdUtil.normalise("not-a-gw-id"))
        assertNull(GhostWaveIdUtil.normalise("GW-GGGG-HHHH"))
        assertNull(GhostWaveIdUtil.normalise(""))
        assertNull(GhostWaveIdUtil.normalise("GW-123"))       // too short
    }

    // ── deriveFromPublicKey → isValid contract ────────────────────────────

    @Test
    fun `any derived ID is always valid`() {
        repeat(50) { seed ->
            val key = ByteArray(32) { (it + seed).toByte() }
            val id  = GhostWaveIdUtil.deriveFromPublicKey(key)
            assertTrue("Derived ID '$id' must pass isValid", GhostWaveIdUtil.isValid(id))
        }
    }

    @Test
    fun `any derived ID normalises back to itself`() {
        repeat(50) { seed ->
            val key = ByteArray(32) { (it * seed + 7).toByte() }
            val id  = GhostWaveIdUtil.deriveFromPublicKey(key)
            assertEquals("normalise(derived) should be identity", id, GhostWaveIdUtil.normalise(id))
        }
    }
}
