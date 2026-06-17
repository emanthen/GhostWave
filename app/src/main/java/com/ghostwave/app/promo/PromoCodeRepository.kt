package com.ghostwave.app.promo

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.ghostwave.app.BuildConfig
import com.ghostwave.app.crypto.KeyStoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG              = "PromoCodeRepository"
private const val PREFS_FILE       = "gw_promo_secure"

private const val KEY_UNLOCKED         = "gw_promo_unlocked"
private const val KEY_CODE_HASH        = "gw_promo_hash"
private const val KEY_TYPE             = "gw_promo_type"
private const val KEY_TIMESTAMP        = "gw_promo_ts"
private const val KEY_INTEGRITY_TOKEN  = "gw_integrity_token"
private const val KEY_FAILED_ATTEMPTS  = "gw_failed_attempts"
private const val KEY_LOCKOUT_UNTIL    = "gw_lockout_until"
private const val KEY_USED_HASHES      = "gw_used_hashes"

private const val LOCKOUT_THRESHOLD_1  = 5
private const val LOCKOUT_THRESHOLD_2  = 10
private const val LOCKOUT_PERMANENT    = 20
private const val LOCKOUT_DURATION_1   = 15 * 60 * 1000L
private const val LOCKOUT_DURATION_2   = 60 * 60 * 1000L

/**
 * Persistent promo gate state stored in EncryptedSharedPreferences (AES-256-GCM via Tink).
 *
 * Two constructors:
 *  - [@Inject] production constructor: builds EncryptedSharedPreferences via Hilt
 *  - [createForTest] factory: accepts a plain SharedPreferences for JVM unit tests
 *    (EncryptedSharedPreferences requires Android Keystore — unavailable in JVM tests)
 */
@Singleton
class PromoCodeRepository private constructor(
    private val prefs:          SharedPreferences,
    private val keyStoreManager: KeyStoreManager,
) {

    @Inject constructor(
        @ApplicationContext context: Context,
        keyStoreManager: KeyStoreManager,
    ) : this(
        prefs           = buildEncryptedPrefs(context),
        keyStoreManager = keyStoreManager,
    )

    companion object {
        /** Test-only factory — bypasses EncryptedSharedPreferences. */
        fun createForTest(
            prefs:          SharedPreferences,
            keyStoreManager: KeyStoreManager,
        ): PromoCodeRepository = PromoCodeRepository(prefs, keyStoreManager)

        private fun buildEncryptedPrefs(context: Context): SharedPreferences {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            return EncryptedSharedPreferences.create(
                PREFS_FILE,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }

    // ── Unlock state ──────────────────────────────────────────────────────────

    fun isUnlocked(): Boolean {
        if (!prefs.getBoolean(KEY_UNLOCKED, false)) return false
        return checkIntegrity()
    }

    fun storeUnlock(codeHash: String, type: PromoCodeType) {
        val ts    = System.currentTimeMillis().toString()
        val token = computeIntegrityToken("true", codeHash, type.label, ts)
        prefs.edit()
            .putBoolean(KEY_UNLOCKED,       true)
            .putString(KEY_CODE_HASH,        codeHash)
            .putString(KEY_TYPE,             type.label)
            .putString(KEY_TIMESTAMP,        ts)
            .putString(KEY_INTEGRITY_TOKEN,  token)
            .apply()
        markCodeUsed(codeHash)
        resetFailedAttempts()
    }

    fun clearUnlock() {
        prefs.edit()
            .remove(KEY_UNLOCKED)
            .remove(KEY_CODE_HASH)
            .remove(KEY_TYPE)
            .remove(KEY_TIMESTAMP)
            .remove(KEY_INTEGRITY_TOKEN)
            .apply()
    }

    // ── Integrity ─────────────────────────────────────────────────────────────

    fun checkIntegrity(): Boolean {
        val unlocked  = prefs.getBoolean(KEY_UNLOCKED, false).toString()
        val codeHash  = prefs.getString(KEY_CODE_HASH, "") ?: ""
        val typeLabel = prefs.getString(KEY_TYPE, "") ?: ""
        val ts        = prefs.getString(KEY_TIMESTAMP, "") ?: ""
        val stored    = prefs.getString(KEY_INTEGRITY_TOKEN, "") ?: ""

        if (stored.isEmpty() || codeHash.isEmpty()) {
            clearUnlock()
            return false
        }

        val valid = keyStoreManager.verifyHmac(
            alias       = BuildConfig.PROMO_DEVICE_KEY_ALIAS,
            data        = integrityData(unlocked, codeHash, typeLabel, ts),
            expectedHex = stored,
        )
        if (!valid) {
            Log.w(TAG, "Integrity check failed — clearing promo unlock state")
            clearUnlock()
        }
        return valid
    }

    // ── Rate limiting ─────────────────────────────────────────────────────────

    fun recordFailedAttempt(): Int {
        val count = getFailedAttemptCount() + 1
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, count).apply()
        val lockoutDuration = when {
            count >= LOCKOUT_PERMANENT   -> Long.MAX_VALUE
            count >= LOCKOUT_THRESHOLD_2 -> LOCKOUT_DURATION_2
            count >= LOCKOUT_THRESHOLD_1 -> LOCKOUT_DURATION_1
            else                         -> 0L
        }
        if (lockoutDuration > 0L) {
            val until = if (lockoutDuration == Long.MAX_VALUE) Long.MAX_VALUE
                        else System.currentTimeMillis() + lockoutDuration
            setLockout(until)
        }
        return count
    }

    fun getFailedAttemptCount(): Int = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)

    fun getLockoutUntil(): Long = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)

    fun setLockout(until: Long) {
        prefs.edit().putLong(KEY_LOCKOUT_UNTIL, until).apply()
    }

    // ── Used codes ────────────────────────────────────────────────────────────

    fun isCodeAlreadyUsed(codeHash: String): Boolean {
        val used = prefs.getString(KEY_USED_HASHES, "") ?: ""
        return used.split("|").contains(codeHash)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun markCodeUsed(codeHash: String) {
        val existing = prefs.getString(KEY_USED_HASHES, "") ?: ""
        val updated  = if (existing.isEmpty()) codeHash else "$existing|$codeHash"
        prefs.edit().putString(KEY_USED_HASHES, updated).apply()
    }

    private fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    private fun integrityData(u: String, h: String, t: String, ts: String) = "$u|$h|$t|$ts"

    private fun computeIntegrityToken(u: String, h: String, t: String, ts: String): String =
        keyStoreManager.hmacSha256(BuildConfig.PROMO_DEVICE_KEY_ALIAS, integrityData(u, h, t, ts))
}
