package com.ghostwave.app.crypto

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.ecc.ECPublicKey

/**
 * Unit tests for FingerprintUtil (Safety Numbers).
 *
 * The hash-iteration logic is pure Kotlin/JVM — no Android API needed.
 * IdentityKey is mocked because we only need serialize() to return controlled bytes.
 */
class FingerprintUtilTest {

    private fun fakeIdentityKey(bytes: ByteArray): IdentityKey {
        val ecPubKey = mockk<ECPublicKey> {
            every { serialize() } returns bytes
        }
        return mockk<IdentityKey> {
            every { serialize() } returns bytes          // IdentityKey.serialize()
            every { publicKey }   returns ecPubKey
        }
    }

    // ── computeFingerprint ────────────────────────────────────────────────

    @Test
    fun `computeFingerprint returns 30-digit numeric string`() {
        val key = fakeIdentityKey(ByteArray(32) { 0x01 })
        val fp  = FingerprintUtil.computeFingerprint(key, "GW-1111-2222")
        assertEquals("Fingerprint must be exactly 30 chars", 30, fp.length)
        assertTrue("Fingerprint must be all digits", fp.all { it.isDigit() })
    }

    @Test
    fun `computeFingerprint is deterministic`() {
        val key = fakeIdentityKey(ByteArray(32) { 0xAB.toByte() })
        val id  = "GW-ABCD-EF01"
        val fp1 = FingerprintUtil.computeFingerprint(key, id)
        val fp2 = FingerprintUtil.computeFingerprint(key, id)
        assertEquals("Fingerprint must be deterministic", fp1, fp2)
    }

    @Test
    fun `computeFingerprint differs for different keys`() {
        val key1 = fakeIdentityKey(ByteArray(32) { 0x11.toByte() })
        val key2 = fakeIdentityKey(ByteArray(32) { 0x22.toByte() })
        val id   = "GW-AAAA-BBBB"
        assertNotEquals(
            "Different keys must produce different fingerprints",
            FingerprintUtil.computeFingerprint(key1, id),
            FingerprintUtil.computeFingerprint(key2, id),
        )
    }

    @Test
    fun `computeFingerprint differs for different stable IDs`() {
        val key = fakeIdentityKey(ByteArray(32) { 0x55.toByte() })
        val fp1 = FingerprintUtil.computeFingerprint(key, "GW-0000-0001")
        val fp2 = FingerprintUtil.computeFingerprint(key, "GW-0000-0002")
        assertNotEquals(
            "Different stable IDs must produce different fingerprints",
            fp1, fp2,
        )
    }

    // ── combinedSafetyNumber ─────────────────────────────────────────────

    @Test
    fun `combinedSafetyNumber returns 12 groups`() {
        val groups = FingerprintUtil.combinedSafetyNumber("0" * 30, "1" * 30)
        assertEquals(12, groups.size)
    }

    @Test
    fun `combinedSafetyNumber is commutative — same result regardless of order`() {
        val fp1 = "123456789012345678901234567890"
        val fp2 = "987654321098765432109876543210"
        val result1 = FingerprintUtil.combinedSafetyNumber(fp1, fp2)
        val result2 = FingerprintUtil.combinedSafetyNumber(fp2, fp1)
        assertEquals("Order of arguments must not matter", result1, result2)
    }

    @Test
    fun `each group in combinedSafetyNumber is exactly 5 digits`() {
        val groups = FingerprintUtil.combinedSafetyNumber("0" * 30, "9" * 30)
        groups.forEach { group ->
            assertEquals("Each group must be 5 chars", 5, group.length)
            assertTrue("Each group must be digits", group.all { it.isDigit() })
        }
    }

    // ── formatSafetyNumber ───────────────────────────────────────────────

    @Test
    fun `formatSafetyNumber produces two lines`() {
        val groups = List(12) { "12345" }
        val formatted = FingerprintUtil.formatSafetyNumber(groups)
        val lines = formatted.lines()
        assertEquals("Format must produce exactly 2 lines", 2, lines.size)
    }

    @Test
    fun `formatSafetyNumber each line has 6 groups separated by spaces`() {
        val groups    = (1..12).map { it.toString().padStart(5, '0') }
        val formatted = FingerprintUtil.formatSafetyNumber(groups)
        val lines     = formatted.lines()
        lines.forEach { line ->
            val parts = line.split(" ")
            assertEquals("Each line must have 6 groups", 6, parts.size)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `formatSafetyNumber throws on wrong group count`() {
        FingerprintUtil.formatSafetyNumber(List(11) { "12345" })
    }

    // helper
    private operator fun String.times(n: Int) = repeat(n)
}
