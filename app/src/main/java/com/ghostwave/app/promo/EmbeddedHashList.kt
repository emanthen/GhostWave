package com.ghostwave.app.promo

/**
 * Compile-time list of SHA-256 hashes of valid embedded promo codes.
 *
 * SECURITY:
 *   - Contains ONLY hashes — NEVER raw codes.
 *   - The class name is intentionally generic; R8 will obfuscate it further
 *     per the proguard-rules.pro entry for this class.
 *   - To add a code for beta distribution: compute SHA-256 of the
 *     normalized code (uppercase, with dashes) and add the hex digest here.
 *
 * Example (DO NOT ship real hashes in a public repo):
 *   "GW-A3F7-9KMP-2WXZ" → sha256("GW-A3F7-9KMP-2WXZ") = <hex>
 *
 * Generate with: echo -n "GW-XXXX-XXXX-XXXX" | sha256sum
 */
internal object EmbeddedHashList {

    /**
     * Set of lowercase hex SHA-256 digests of valid embedded codes.
     *
     * These are checked before the server, so embedded codes work fully offline.
     * Each code is single-use per device (enforced by PromoCodeRepository).
     *
     * Replace these placeholder hashes with real beta codes before shipping.
     */
    val HASHES: Set<String> = setOf(
        // Beta wave 1 — replace with real hashes
        "b94d27b9934d3e08a52e52d7da7dabfac484efe04294e576e3b5a05d5e647e08",   // placeholder
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",   // placeholder
    )

    fun contains(codeHash: String): Boolean = HASHES.contains(codeHash.lowercase())
}
