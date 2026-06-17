package com.ghostwave.app.crypto

import org.signal.libsignal.protocol.IdentityKey
import java.security.MessageDigest

/**
 * Generates Safety Numbers — the human-verifiable fingerprint that lets two
 * users confirm they are talking to each other and not a MITM.
 *
 * Algorithm (mirrors Signal's Numeric Fingerprint v1):
 * ──────────────────────────────────────────────────────────────────────────
 * 1. For each side (local, remote):
 *    fingerprint_input = SHA-512^5200( publicKey.serialize() + stableId.encodeToByteArray() )
 *    Take the first 30 bytes of the digest → 5 groups of 6 bytes.
 *    Each 6-byte chunk interpreted as big-endian uint64 mod 100000 → 5-digit string.
 *    Concatenate the 5 numbers → 30-digit string.
 *
 * 2. Sort the two 30-digit strings lexicographically (so both sides always
 *    produce the same combined fingerprint regardless of who is "local").
 *
 * 3. Display as 12 groups of 5 digits (first string's 6 groups + second's 6 groups).
 *
 * Note: the full display format is implemented in Step 5 when we have both
 * keys. The computation here is exposed as pure functions for testability.
 * ──────────────────────────────────────────────────────────────────────────
 */
object FingerprintUtil {

    private const val ITERATIONS   = 5200
    private const val CHUNK_BYTES  = 6
    private const val CHUNK_COUNT  = 5
    private const val MOD_DIVISOR  = 100_000L

    /**
     * Computes the 30-digit numeric fingerprint for one side of a conversation.
     *
     * @param identityKey  The party's long-term identity public key.
     * @param stableId     A stable identifier for this party (GhostWave ID string).
     *                     Using the GW-ID (derived from the public key hash) prevents
     *                     an attacker from re-using one party's key under a different ID.
     */
    fun computeFingerprint(identityKey: IdentityKey, stableId: String): String {
        val keyBytes = identityKey.serialize()
        val idBytes  = stableId.encodeToByteArray()

        // Iterative SHA-512: input = previous_digest || key || id
        val digest = MessageDigest.getInstance("SHA-512")

        // Seed with version byte (0x00) + key + id — matches Signal's format
        var input = byteArrayOf(0x00) + keyBytes + idBytes
        repeat(ITERATIONS) {
            digest.reset()
            digest.update(input)
            input = digest.digest() + keyBytes + idBytes
        }

        // Take the first 30 bytes of the final digest
        val resultBytes = input.take(CHUNK_BYTES * CHUNK_COUNT)

        return (0 until CHUNK_COUNT).joinToString("") { i ->
            val chunk  = resultBytes.subList(i * CHUNK_BYTES, (i + 1) * CHUNK_BYTES)
            val value  = chunk.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
            (value % MOD_DIVISOR).toString().padStart(5, '0')
        }
    }

    /**
     * Combines two 30-digit fingerprints into a single displayable Safety Number.
     * Returns a list of 12 five-digit strings (6 from local, 6 from remote).
     * Both sides always produce the same list because the pair is sorted.
     */
    fun combinedSafetyNumber(fp1: String, fp2: String): List<String> {
        val (first, second) = if (fp1 <= fp2) fp1 to fp2 else fp2 to fp1
        val combined = first + second  // 60 digits
        return combined.chunked(5)     // 12 groups of 5
    }

    /**
     * Formats combined safety number as displayed in the UI:
     * "12345 67890 11111 22222 33333 44444\n55555 66666 77777 88888 99999 00000"
     */
    fun formatSafetyNumber(groups: List<String>): String {
        require(groups.size == 12) { "Expected 12 groups, got ${groups.size}" }
        val firstRow  = groups.take(6).joinToString(" ")
        val secondRow = groups.drop(6).joinToString(" ")
        return "$firstRow\n$secondRow"
    }
}
