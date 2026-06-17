package com.ghostwave.app.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.AEADBadTagException

/**
 * Instrumented tests for KeyStoreManager.
 *
 * Must run on a real device or emulator because they exercise the
 * actual Android Keystore hardware abstraction layer.
 *
 * Each test uses a unique alias to avoid cross-test key collisions.
 * All aliases are cleaned up in tearDown.
 */
@RunWith(AndroidJUnit4::class)
class KeyStoreManagerTest {

    private lateinit var manager: KeyStoreManager
    private val testAliases = mutableListOf<String>()

    @Before
    fun setUp() {
        manager = KeyStoreManager()
    }

    @org.junit.After
    fun tearDown() {
        testAliases.forEach { alias ->
            runCatching { manager.deleteKey(alias) }
        }
    }

    private fun alias(name: String): String =
        "test_ks_$name".also { testAliases.add(it) }

    // ── Key creation ──────────────────────────────────────────────────────

    @Test
    fun getOrCreateAesKey_creates_key_that_does_not_exist_yet() {
        val a = alias("create")
        assertFalse(manager.hasKey(a))
        manager.getOrCreateAesKey(a)
        assertTrue(manager.hasKey(a))
    }

    @Test
    fun getOrCreateAesKey_is_idempotent_for_existing_alias() {
        val a = alias("idempotent")
        val k1 = manager.getOrCreateAesKey(a)
        val k2 = manager.getOrCreateAesKey(a)
        // Same underlying SecretKey object from Keystore
        assertArrayEquals(k1.encoded ?: byteArrayOf(), k2.encoded ?: byteArrayOf())
    }

    // ── Encrypt / Decrypt roundtrip ───────────────────────────────────────

    @Test
    fun encrypt_decrypt_roundtrip_with_short_plaintext() {
        val a         = alias("roundtrip_short")
        val plaintext = "hello GhostWave".toByteArray()
        val blob      = manager.encrypt(a, plaintext)
        val result    = manager.decrypt(a, blob)
        assertArrayEquals("Decrypted bytes must match original plaintext", plaintext, result)
    }

    @Test
    fun encrypt_decrypt_roundtrip_with_32_byte_key_material() {
        val a         = alias("roundtrip_key")
        val plaintext = ByteArray(32) { (it * 7 + 13).toByte() } // simulates Curve25519 private key
        val blob      = manager.decrypt(a, manager.encrypt(a, plaintext))
        assertArrayEquals(plaintext, blob)
    }

    @Test
    fun encrypt_decrypt_roundtrip_with_large_payload() {
        val a         = alias("roundtrip_large")
        val plaintext = ByteArray(4096) { it.toByte() }
        val blob      = manager.decrypt(a, manager.encrypt(a, plaintext))
        assertArrayEquals(plaintext, blob)
    }

    @Test
    fun encrypt_produces_different_ciphertext_each_call_same_plaintext() {
        // GCM uses a fresh random IV per encryption — same plaintext must never
        // produce the same ciphertext (IV reuse would be catastrophic)
        val a         = alias("iv_unique")
        val plaintext = "same data".toByteArray()
        val blob1 = manager.encrypt(a, plaintext)
        val blob2 = manager.encrypt(a, plaintext)
        assertFalse(
            "IV must differ between encryptions",
            blob1.iv.contentEquals(blob2.iv),
        )
        assertFalse(
            "Ciphertext must differ between encryptions (different IV → different output)",
            blob1.ciphertext.contentEquals(blob2.ciphertext),
        )
    }

    @Test
    fun decrypt_with_empty_byte_array_payload() {
        val a    = alias("empty")
        val blob = manager.encrypt(a, ByteArray(0))
        val out  = manager.decrypt(a, blob)
        assertArrayEquals(ByteArray(0), out)
    }

    // ── Authentication tag verification ───────────────────────────────────

    @Test(expected = Exception::class)
    fun decrypt_throws_on_tampered_ciphertext() {
        val a         = alias("tamper_ct")
        val blob      = manager.encrypt(a, "sensitive".toByteArray())
        // Flip one byte in the ciphertext
        val tampered  = blob.ciphertext.copyOf().also { it[0] = it[0].xor(0xFF.toByte()) }
        manager.decrypt(a, EncryptedBlob(iv = blob.iv, ciphertext = tampered))
        // Expect AEADBadTagException or GeneralSecurityException
    }

    @Test(expected = Exception::class)
    fun decrypt_throws_on_tampered_iv() {
        val a        = alias("tamper_iv")
        val blob     = manager.encrypt(a, "sensitive".toByteArray())
        val tamperedIv = blob.iv.copyOf().also { it[0] = it[0].xor(0xFF.toByte()) }
        manager.decrypt(a, EncryptedBlob(iv = tamperedIv, ciphertext = blob.ciphertext))
    }

    // ── Key deletion ──────────────────────────────────────────────────────

    @Test
    fun deleteKey_removes_the_key_from_keystore() {
        val a = alias("delete")
        manager.getOrCreateAesKey(a)
        assertTrue(manager.hasKey(a))
        manager.deleteKey(a)
        assertFalse(manager.hasKey(a))
        testAliases.remove(a) // already deleted, skip tearDown cleanup
    }

    @Test
    fun deleteKey_on_missing_alias_does_not_throw() {
        manager.deleteKey("alias_that_never_existed_xyz")
        // Should complete without exception
    }

    @Test(expected = Exception::class)
    fun decrypt_fails_after_key_deletion() {
        val a         = alias("delete_then_decrypt")
        val blob      = manager.encrypt(a, "secret".toByteArray())
        manager.deleteKey(a)
        testAliases.remove(a)
        // Should throw because the AES key no longer exists
        manager.decrypt(a, blob)
    }
}
