package com.ghostwave.app.crypto

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair

/**
 * JVM unit tests for SignalProtocolManager.
 *
 * libsignal-android loads its native library on first use; these tests run
 * on the JVM via Robolectric or directly if the .so is on the Java library
 * path (which it is when the AAR is unpacked during unit test compilation).
 *
 * If libsignal's native bridge is unavailable in the test runner, these
 * tests should be moved to the androidTest source set as instrumented tests.
 */
class SignalProtocolManagerTest {

    private lateinit var manager: SignalProtocolManager

    @Before
    fun setUp() {
        manager = SignalProtocolManager()
    }

    // ── Identity key pair ─────────────────────────────────────────────────

    @Test
    fun `generateIdentityKeyPair returns non-null pair`() = runTest {
        val keyPair = manager.generateIdentityKeyPair()
        assertNotNull(keyPair)
        assertNotNull(keyPair.publicKey)
        assertNotNull(keyPair.privateKey)
    }

    @Test
    fun `generateIdentityKeyPair produces unique pairs each call`() = runTest {
        val kp1 = manager.generateIdentityKeyPair()
        val kp2 = manager.generateIdentityKeyPair()
        assertFalse(
            "Two generated key pairs should not share a public key",
            kp1.publicKey.serialize().contentEquals(kp2.publicKey.serialize()),
        )
    }

    @Test
    fun `identity key pair survives serialise-deserialise roundtrip`() = runTest {
        val original   = manager.generateIdentityKeyPair()
        val serialized = manager.serializeIdentityKeyPair(original)
        val restored   = manager.deserializeIdentityKeyPair(serialized)

        assertArrayEquals(
            "Public key must survive roundtrip",
            original.publicKey.serialize(),
            restored.publicKey.serialize(),
        )
        assertArrayEquals(
            "Private key must survive roundtrip",
            original.privateKey.serialize(),
            restored.privateKey.serialize(),
        )
    }

    @Test
    fun `serialized identity key pair is non-empty`() = runTest {
        val keyPair    = manager.generateIdentityKeyPair()
        val serialized = manager.serializeIdentityKeyPair(keyPair)
        assertTrue("Serialized key pair must not be empty", serialized.isNotEmpty())
        // Curve25519 private key is 32 bytes; serialized format includes public key + version
        assertTrue("Serialized key pair should be at least 64 bytes", serialized.size >= 64)
    }

    // ── Registration ID ───────────────────────────────────────────────────

    @Test
    fun `generateRegistrationId is in valid range`() = runTest {
        repeat(20) {
            val id = manager.generateRegistrationId()
            assertTrue("Registration ID must be >= 1", id >= 1)
            assertTrue("Registration ID must be <= 16380", id <= 16380)
        }
    }

    @Test
    fun `generateRegistrationId produces varied values`() = runTest {
        val ids = (1..20).map { manager.generateRegistrationId() }.toSet()
        // With 16380 possible values and 20 samples, collision probability is negligible
        assertTrue("Registration IDs should not all be identical", ids.size > 1)
    }

    // ── Prekeys ───────────────────────────────────────────────────────────

    @Test
    fun `generatePreKeys returns requested count`() = runTest {
        val preKeys = manager.generatePreKeys(startId = 1, count = 10)
        assertTrue("Should return exactly 10 prekeys", preKeys.size == 10)
    }

    @Test
    fun `generatePreKeys IDs are sequential from startId`() = runTest {
        val startId = 42
        val preKeys = manager.generatePreKeys(startId = startId, count = 5)
        preKeys.forEachIndexed { index, preKey ->
            val expectedId = startId + index
            assertTrue("Prekey at index $index should have ID $expectedId",
                preKey.id == expectedId)
        }
    }

    @Test
    fun `generatePreKeys each has unique public key`() = runTest {
        val preKeys    = manager.generatePreKeys(startId = 1, count = 20)
        val publicKeys = preKeys.map { it.keyPair.publicKey.serialize().toList() }.toSet()
        assertTrue("All prekey public keys must be unique", publicKeys.size == 20)
    }

    // ── Signed prekey ──────────────────────────────────────────────────────

    @Test
    fun `generateSignedPreKey returns non-null with valid signature`() = runTest {
        val identityKP = manager.generateIdentityKeyPair()
        val signedPK   = manager.generateSignedPreKey(identityKP, id = 1)

        assertNotNull(signedPK)
        assertNotNull(signedPK.keyPair)
        assertNotNull(signedPK.signature)
        assertTrue("Signature must be non-empty", signedPK.signature.isNotEmpty())
    }

    @Test
    fun `generateSignedPreKey signature verifies against identity public key`() = runTest {
        val identityKP = manager.generateIdentityKeyPair()
        val signedPK   = manager.generateSignedPreKey(identityKP, id = 1)

        // Verify: the signed prekey's public key bytes, signed with identity private key,
        // must verify against the identity public key.
        // In libsignal this is done via Curve.verifySignature
        val pubKeyBytes = signedPK.keyPair.publicKey.serialize()
        val signature   = signedPK.signature

        // If this throws, the signature is invalid
        identityKP.publicKey.publicKey.verifySignature(pubKeyBytes, signature)
        // reaching here means verification passed
    }

    @Test
    fun `two signed prekeys with different IDs have different key pairs`() = runTest {
        val identityKP = manager.generateIdentityKeyPair()
        val spk1       = manager.generateSignedPreKey(identityKP, id = 1)
        val spk2       = manager.generateSignedPreKey(identityKP, id = 2)

        assertFalse(
            "Different signed prekeys should have different public keys",
            spk1.keyPair.publicKey.serialize()
                .contentEquals(spk2.keyPair.publicKey.serialize()),
        )
    }

    // ── Serialisation invariant ───────────────────────────────────────────

    @Test
    fun `zeroing serialized bytes does not corrupt deserialized copy`() = runTest {
        val original   = manager.generateIdentityKeyPair()
        val serialized = manager.serializeIdentityKeyPair(original)
        val restored   = manager.deserializeIdentityKeyPair(serialized)

        // Zero the serialized buffer (as the real code does after encryption)
        serialized.fill(0)

        // The restored key pair was built from the bytes before zeroing
        assertNotNull(restored.publicKey)
        assertArrayEquals(
            original.publicKey.serialize(),
            restored.publicKey.serialize(),
        )
    }
}
