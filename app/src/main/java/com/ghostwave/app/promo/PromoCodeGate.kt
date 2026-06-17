package com.ghostwave.app.promo

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point for the promo gate check.
 *
 * Called from:
 *   - [com.ghostwave.app.MainActivity.onCreate] — determines start destination
 *   - [com.ghostwave.app.MainActivity.onResume]  — re-checks after backgrounding
 *
 * Returns true (show gate) if ANY of:
 *   1. The unlock record is missing (fresh install, or cleared storage)
 *   2. The HMAC integrity token fails — indicates tampering with prefs file
 *
 * This class is intentionally thin. All state lives in [PromoCodeRepository].
 */
@Singleton
class PromoCodeGate @Inject constructor(
    private val repository: PromoCodeRepository,
) {
    /**
     * Returns true if the promo gate should be shown.
     * Internally calls [PromoCodeRepository.isUnlocked] which also runs
     * the HMAC integrity check and clears tampered state before returning.
     */
    fun shouldShowGate(): Boolean = !repository.isUnlocked()

    /**
     * Re-runs the integrity check.
     * Returns false (and wipes unlock state) if tampering is detected.
     * Called on [com.ghostwave.app.MainActivity.onResume].
     */
    fun checkIntegrity(): Boolean = repository.checkIntegrity()
}
