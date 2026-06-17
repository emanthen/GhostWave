package com.ghostwave.app.util

import java.security.MessageDigest

/**
 * Derives and validates GhostWave IDs.
 *
 * Format: GW-XXXX-XXXX  (where X is an uppercase hex digit)
 *
 * Derivation:
 *   SHA-256( identityPublicKey.serialize() )
 *   → take bytes [0..1] → uppercase hex → segment 1 (4 chars)
 *   → take bytes [2..3] → uppercase hex → segment 2 (4 chars)
 *   → "GW-{seg1}-{seg2}"
 *
 * Properties:
 *   - Deterministic: same key always yields the same ID
 *   - One-way: cannot recover the key from the ID alone
 *   - Human-friendly: 11 characters, easy to read aloud for manual entry
 *   - Collision space: 2^32 ≈ 4 billion unique IDs — adequate for a P2P mesh
 *     where identity is ultimately verified via Safety Numbers, not just the ID
 *
 * Note: GW-IDs are identifiers, not authenticators. Always verify a contact's
 * identity via Safety Numbers (FingerprintUtil) after the initial QR exchange.
 */
object GhostWaveIdUtil {

    private val GW_ID_REGEX = Regex("^GW-[0-9A-F]{4}-[0-9A-F]{4}$")

    /**
     * Derives a GW-ID from the raw bytes of a Curve25519 public key.
     *
     * @param publicKeyBytes  The serialized identity public key bytes
     *                        (from IdentityKey.serialize()).
     */
    fun deriveFromPublicKey(publicKeyBytes: ByteArray): String {
        require(publicKeyBytes.isNotEmpty()) { "Public key bytes must not be empty" }

        val hash = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)

        val seg1 = hash[0].toHex() + hash[1].toHex()
        val seg2 = hash[2].toHex() + hash[3].toHex()

        return "GW-$seg1-$seg2"
    }

    /**
     * Returns true if [id] matches the GW-XXXX-XXXX format exactly.
     * Does NOT verify that the ID belongs to any known contact.
     */
    fun isValid(id: String): Boolean = GW_ID_REGEX.matches(id)

    /**
     * Normalises user-typed input to the canonical GW-ID format.
     * Accepts: "GW1234ABCD", "gw-1234-abcd", "1234ABCD", "GW-1234-ABCD"
     * Returns null if the sanitised string cannot form a valid GW-ID.
     */
    fun normalise(raw: String): String? {
        // Strip all non-alphanumeric chars, uppercase
        val stripped = raw.replace(Regex("[^A-Za-z0-9]"), "").uppercase()

        // Accept with or without the "GW" prefix
        val hex = when {
            stripped.startsWith("GW") && stripped.length == 10 -> stripped.drop(2)
            stripped.length == 8                                -> stripped
            else                                                -> return null
        }

        // Must be exactly 8 uppercase hex chars
        if (!hex.matches(Regex("[0-9A-F]{8}"))) return null

        return "GW-${hex.take(4)}-${hex.drop(4)}"
    }

    private fun Byte.toHex(): String = "%02X".format(this)
}
