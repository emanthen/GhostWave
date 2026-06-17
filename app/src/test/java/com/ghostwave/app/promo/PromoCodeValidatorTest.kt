package com.ghostwave.app.promo

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TC-01 through TC-10 — PromoCodeValidator format, hash, normalize.
 * Pure unit tests — no Android context required.
 */
class PromoCodeValidatorTest {

    private lateinit var validator: PromoCodeValidator
    private lateinit var repository: PromoCodeRepository

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        every { repository.isCodeAlreadyUsed(any()) } returns false
        every { repository.getLockoutUntil() } returns 0L
        validator = PromoCodeValidator(repository)
    }

    // TC-01
    @Test fun `formatCheck valid uppercase code returns true`() {
        assertTrue(validator.formatCheck("GW-A3F7-9KMP-2WXZ"))
    }

    // TC-02
    @Test fun `formatCheck after normalize lowercase input returns true`() {
        val normalized = validator.normalize("gw-a3f7-9kmp-2wxz")
        assertTrue(validator.formatCheck(normalized))
    }

    // TC-03
    @Test fun `formatCheck too short code returns false`() {
        assertFalse(validator.formatCheck("GW-A3F7-9KMP"))
    }

    // TC-04
    @Test fun `formatCheck too long code returns false`() {
        assertFalse(validator.formatCheck("GW-A3F7-9KMP-2WXZ-XXXX"))
    }

    // TC-05
    @Test fun `formatCheck invalid characters in segment returns false`() {
        assertFalse(validator.formatCheck("GW-A3F7-9KMP-!@#\$"))
    }

    // TC-06
    @Test fun `formatCheck empty string returns false`() {
        assertFalse(validator.formatCheck(""))
    }

    // TC-07
    @Test fun `hash is deterministic same input produces same output`() {
        val h1 = validator.hash("GW-A3F7-9KMP-2WXZ")
        val h2 = validator.hash("GW-A3F7-9KMP-2WXZ")
        assertEquals(h1, h2)
    }

    // TC-08
    @Test fun `hash different inputs produce different outputs`() {
        val h1 = validator.hash("GW-A3F7-9KMP-2WXZ")
        val h2 = validator.hash("GW-A3F7-9KMP-2WX0")
        assertNotEquals(h1, h2)
    }

    // TC-09
    @Test fun `normalize lowercase with dashes returns uppercase with dashes`() {
        assertEquals("GW-A3F7-9KMP-2WXZ", validator.normalize("gw-a3f7-9kmp-2wxz"))
    }

    // TC-10
    @Test fun `normalize spaces instead of dashes returns dashed format`() {
        assertEquals("GW-A3F7-9KMP-2WXZ", validator.normalize("GW A3F7 9KMP 2WXZ"))
    }

    // Additional: hash output is 64 hex chars (SHA-256)
    @Test fun `hash output is 64 lowercase hex characters`() {
        val h = validator.hash("GW-A3F7-9KMP-2WXZ")
        assertEquals(64, h.length)
        assertTrue(h.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
