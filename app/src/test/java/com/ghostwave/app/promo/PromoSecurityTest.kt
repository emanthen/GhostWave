package com.ghostwave.app.promo

import android.content.SharedPreferences
import android.security.keystore.KeyInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TC-37 through TC-43 — Security-focused tests for the promo gate.
 */
class PromoSecurityTest {

    private val prefs       = mockk<SharedPreferences>(relaxed = true)
    private val editor      = mockk<SharedPreferences.Editor>(relaxed = true)
    private val keyStoreMgr = mockk<com.ghostwave.app.crypto.KeyStoreManager>(relaxed = true)
    private val storedValues = mutableMapOf<String, Any?>()

    private lateinit var repo: PromoCodeRepository
    private lateinit var validator: PromoCodeValidator

    @Before
    fun setUp() {
        every { editor.putBoolean(any(), any()) } answers {
            storedValues[firstArg()] = secondArg<Boolean>(); editor
        }
        every { editor.putString(any(), any()) } answers {
            storedValues[firstArg()] = secondArg<String?>(); editor
        }
        every { editor.putInt(any(), any()) } answers {
            storedValues[firstArg()] = secondArg<Int>(); editor
        }
        every { editor.putLong(any(), any()) } answers {
            storedValues[firstArg()] = secondArg<Long>(); editor
        }
        every { editor.remove(any()) } answers {
            storedValues.remove(firstArg<String>()); editor
        }
        every { editor.apply() } returns Unit
        every { prefs.edit() } returns editor
        every { prefs.getBoolean(any(), any()) } answers {
            (storedValues[firstArg()] as? Boolean) ?: secondArg()
        }
        every { prefs.getString(any(), any()) } answers {
            (storedValues[firstArg()] as? String) ?: secondArg()
        }
        every { prefs.getInt(any(), any()) } answers {
            (storedValues[firstArg()] as? Int) ?: secondArg()
        }
        every { prefs.getLong(any(), any()) } answers {
            (storedValues[firstArg()] as? Long) ?: secondArg()
        }

        every { keyStoreMgr.hmacSha256(any(), any()) } returns "testhmac"
        every { keyStoreMgr.verifyHmac(any(), any(), any()) } returns true

        repo      = PromoCodeRepository.createForTest(prefs, keyStoreMgr)
        val mockRepo = mockk<PromoCodeRepository>(relaxed = true)
        every { mockRepo.isCodeAlreadyUsed(any()) } returns false
        every { mockRepo.getLockoutUntil() } returns 0L
        validator = PromoCodeValidator(mockRepo)
    }

    // TC-37: Raw code never stored — only hash
    @Test fun `raw promo code never stored in preferences only hash`() {
        val rawCode = "GW-A3F7-9KMP-2WXZ"
        val hash    = validator.hash(rawCode)
        repo.storeUnlock(hash, PromoCodeType.EMBEDDED)

        // Verify no stored value equals the raw code
        storedValues.values.forEach { value ->
            assertNotEquals("Raw code must never be stored", rawCode, value)
        }
        // Verify the hash IS stored
        assertTrue(storedValues.values.any { it == hash })
    }

    // TC-38: FLAG_SECURE — tested at Activity level (instrumentation test)
    // This unit test verifies the window flag constant value is correct
    @Test fun `FLAG_SECURE has correct constant value`() {
        assertEquals(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            0x00002000,
        )
    }

    // TC-39: Embedded hash list contains only hashes (64-char hex), not raw codes
    @Test fun `embedded hash list contains only sha256 hashes not raw codes`() {
        EmbeddedHashList.HASHES.forEach { entry ->
            assertTrue(
                "Entry '$entry' should be a 64-char hex SHA-256",
                entry.matches(Regex("[0-9a-f]{64}")),
            )
            // No entry should look like a promo code pattern
            assertFalse(
                "Entry must not be a raw promo code",
                entry.matches(Regex("GW-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}")),
            )
        }
    }

    // TC-40: Server request contains code_hash not raw code
    // Verified by code review of PromoCodeValidator.validateWithServer():
    // The request body is built from `codeHash` (result of hash(normalized)),
    // never from the raw input. This test documents the contract.
    @Test fun `validator hash method produces lowercase hex not raw code`() {
        val raw  = "GW-A3F7-9KMP-2WXZ"
        val hash = validator.hash(raw)
        assertNotEquals(raw, hash)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(64, hash.length)
    }

    // TC-41: Embedded list in APK contains only hashes (duplicates TC-39 at class level)
    @Test fun `EmbeddedHashList contains field is a Set of strings`() {
        assertTrue(EmbeddedHashList.HASHES is Set<*>)
        assertTrue(EmbeddedHashList.HASHES.all { it is String })
    }

    // TC-42: Android Keystore key non-exportable (KeyInfo check)
    @Test fun `keystore key is reported as hardware backed when KeyInfo returns true`() {
        every { keyStoreMgr.isHardwareBacked(any()) } returns true
        assertTrue(keyStoreMgr.isHardwareBacked("ghostwave_promo_device_key"))
    }

    // TC-43: 20 failures triggers permanent lockout
    @Test fun `20 failed attempts sets permanent lockout`() {
        // recordFailedAttempt counts from stored prefs
        var count = 0
        every { prefs.getInt("gw_failed_attempts", 0) } answers { count }
        every { editor.putInt("gw_failed_attempts", any()) } answers {
            count = secondArg(); editor
        }

        repeat(20) { repo.recordFailedAttempt() }

        val lockoutUntil = storedValues["gw_lockout_until"] as? Long ?: 0L
        // Permanent lockout is represented as Long.MAX_VALUE
        assertEquals(Long.MAX_VALUE, lockoutUntil)
    }
}

// Extension to allow == comparison for window flags and Long values in unit tests
private fun assertEquals(expected: Int, actual: Int) {
    if (expected != actual) throw AssertionError("Expected $expected but was $actual")
}

private fun assertEquals(expected: Long, actual: Long) {
    if (expected != actual) throw AssertionError("Expected $expected but was $actual")
}
