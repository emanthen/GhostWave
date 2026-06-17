package com.ghostwave.app.promo

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TC-11 through TC-19 — PromoCodeRepository state management.
 *
 * Note: EncryptedSharedPreferences cannot be instantiated in JVM unit tests
 * (requires Android Keystore). We test repository logic by injecting a plain
 * SharedPreferences mock and verifying the correct keys are read/written.
 * HMAC verification is tested with a mocked KeyStoreManager.
 */
class PromoCodeRepositoryTest {

    // We test via a thin wrapper that accepts a SharedPreferences injection
    // rather than the full EncryptedSharedPreferences (Android-only).
    // The production constructor builds EncryptedSharedPreferences; tests
    // use the internal constructor with a plain mock.

    private val prefs       = mockk<SharedPreferences>(relaxed = true)
    private val editor      = mockk<SharedPreferences.Editor>(relaxed = true)
    private val keyStoreMgr = mockk<com.ghostwave.app.crypto.KeyStoreManager>(relaxed = true)
    private lateinit var repo: PromoCodeRepository

    private val storedValues = mutableMapOf<String, Any?>()

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

        // HMAC: mock returns a fixed token, verification always passes unless we override
        every { keyStoreMgr.hmacSha256(any(), any()) } returns "deadbeef"
        every { keyStoreMgr.verifyHmac(any(), any(), any()) } returns true

        repo = PromoCodeRepository.createForTest(prefs, keyStoreMgr)
    }

    // TC-11
    @Test fun `isUnlocked returns false on fresh install`() {
        assertFalse(repo.isUnlocked())
    }

    // TC-12
    @Test fun `isUnlocked returns true after storeUnlock`() {
        repo.storeUnlock("abc123hash", PromoCodeType.EMBEDDED)
        assertTrue(repo.isUnlocked())
    }

    // TC-13
    @Test fun `isUnlocked returns false after clearUnlock`() {
        repo.storeUnlock("abc123hash", PromoCodeType.EMBEDDED)
        repo.clearUnlock()
        assertFalse(repo.isUnlocked())
    }

    // TC-14
    @Test fun `recordFailedAttempt increments counter`() {
        assertEquals(1, repo.recordFailedAttempt())
        assertEquals(2, repo.recordFailedAttempt())
    }

    // TC-15
    @Test fun `after 5 failed attempts lockout is set in the future`() {
        repeat(5) { repo.recordFailedAttempt() }
        assertTrue(repo.getLockoutUntil() > System.currentTimeMillis())
    }

    // TC-16
    @Test fun `isCodeAlreadyUsed false before use true after storeUnlock`() {
        val hash = "testhash123"
        assertFalse(repo.isCodeAlreadyUsed(hash))
        repo.storeUnlock(hash, PromoCodeType.EMBEDDED)
        assertTrue(repo.isCodeAlreadyUsed(hash))
    }

    // TC-17
    @Test fun `checkIntegrity returns false when timestamp is tampered`() {
        repo.storeUnlock("somehash", PromoCodeType.SERVER)
        // Simulate tamper: HMAC verification returns false
        every { keyStoreMgr.verifyHmac(any(), any(), any()) } returns false
        assertFalse(repo.checkIntegrity())
        // Tamper detection clears state
        assertFalse(repo.isUnlocked())
    }

    // TC-18
    @Test fun `checkIntegrity returns false when code hash is tampered`() {
        repo.storeUnlock("originalhash", PromoCodeType.EMBEDDED)
        every { keyStoreMgr.verifyHmac(any(), any(), any()) } returns false
        assertFalse(repo.checkIntegrity())
    }

    // TC-19
    @Test fun `checkIntegrity returns true immediately after storeUnlock`() {
        repo.storeUnlock("validhash", PromoCodeType.EMBEDDED)
        // verifyHmac is mocked to return true in setUp
        assertTrue(repo.checkIntegrity())
    }
}
