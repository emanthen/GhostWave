package com.ghostwave.app.promo

// ── Promo code type ────────────────────────────────────────────────────────────

sealed class PromoCodeType(val label: String) {
    /** Validated against the embedded SHA-256 hash list — offline, instant. */
    data object EMBEDDED : PromoCodeType("embedded")

    /** Validated via POST to api.ghostwave.app/v1/promo/validate. */
    data object SERVER : PromoCodeType("server")

    /** Reserved for future admin-generated codes — architecture only. */
    data object ADMIN : PromoCodeType("admin")

    companion object {
        fun fromLabel(label: String): PromoCodeType = when (label) {
            "embedded" -> EMBEDDED
            "server"   -> SERVER
            "admin"    -> ADMIN
            else       -> SERVER
        }
    }
}

// ── Validation result ──────────────────────────────────────────────────────────

sealed class PromoResult {
    /** Code verified — device is unlocked. */
    data class Success(
        val type:      PromoCodeType,
        val expiresAt: Long?,           // null = never expires
    ) : PromoResult()

    /** Raw input does not match GW-XXXX-XXXX-XXXX pattern. */
    data object InvalidFormat  : PromoResult()

    /** Code not in embedded list and server returned invalid. */
    data object InvalidCode    : PromoResult()

    /** This code hash was already stored on this device (single-use protection). */
    data object AlreadyUsed    : PromoResult()

    /** Server confirmed the code exists but its expiry has passed. */
    data object Expired        : PromoResult()

    /** Network request timed out or returned a non-2xx response. */
    data object NetworkError   : PromoResult()

    /** Too many failed attempts — device is in lockout. */
    data class RateLimited(val lockoutUntilMs: Long) : PromoResult()
}
