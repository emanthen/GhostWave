package com.ghostwave.app.promo

import android.util.Log
import com.ghostwave.app.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG              = "PromoCodeValidator"
private val CODE_REGEX             = Regex("^GW-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")
private const val CONNECT_TIMEOUT  = 10L   // seconds
private const val READ_TIMEOUT     = 10L

/**
 * Validates promo codes through three paths:
 *   1. Format check (pure regex, no network)
 *   2. Embedded hash list check (offline, instant)
 *   3. Server validation (HTTPS POST — hash only, NEVER raw code)
 *
 * Security invariants:
 *   - Raw code is NEVER sent to the server — only its SHA-256 hash.
 *   - If server is unreachable for a server-type code, we return
 *     [PromoResult.NetworkError] — there is NO offline fallback for
 *     server codes. The system fails closed, not open.
 */
@Singleton
class PromoCodeValidator @Inject constructor(
    private val repository: PromoCodeRepository,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .build()

    // ── Public API ────────────────────────────────────────────────────────────

    /** Strips spaces, upper-cases, inserts dashes at standard positions. */
    fun normalize(raw: String): String {
        val stripped = raw.uppercase().replace(" ", "").replace("-", "")
        return buildString {
            // GW-XXXX-XXXX-XXXX → prefix "GW" + 3 groups of 4
            if (stripped.startsWith("GW")) {
                append("GW")
                val body = stripped.removePrefix("GW")
                body.chunked(4).forEachIndexed { idx, chunk ->
                    append("-")
                    append(chunk)
                }
            } else {
                // Input didn't start with GW — return as-is so formatCheck catches it
                append(stripped)
            }
        }
    }

    /** Returns true iff the normalized code matches GW-XXXX-XXXX-XXXX exactly. */
    fun formatCheck(code: String): Boolean = CODE_REGEX.matches(code)

    /** SHA-256 of the normalized code, hex-encoded lowercase. */
    fun hash(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes  = digest.digest(code.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Full validation pipeline:
     *   normalize → formatCheck → dedup check → embedded → server
     *
     * This is a suspend function because the server leg uses OkHttp
     * (blocking I/O run on a coroutine dispatcher by the caller).
     */
    suspend fun validate(rawInput: String): PromoResult {
        val normalized = normalize(rawInput)

        if (!formatCheck(normalized)) return PromoResult.InvalidFormat

        val codeHash = hash(normalized)

        if (repository.isCodeAlreadyUsed(codeHash)) return PromoResult.AlreadyUsed

        // Check lockout before any further processing
        val lockoutUntil = repository.getLockoutUntil()
        if (lockoutUntil > System.currentTimeMillis()) {
            return PromoResult.RateLimited(lockoutUntil)
        }

        // Path 1 — embedded (offline, instant)
        if (EmbeddedHashList.contains(codeHash)) {
            return PromoResult.Success(
                type      = PromoCodeType.EMBEDDED,
                expiresAt = null,
            )
        }

        // Path 2 — server validation (HTTPS only, hash only)
        return validateWithServer(codeHash)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun validateWithServer(codeHash: String): PromoResult {
        val body = JSONObject().put("code_hash", codeHash).toString()
        val request = Request.Builder()
            .url("${BuildConfig.PROMO_API_BASE}/v1/promo/validate")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            http.newCall(request).execute().use { response ->
                when {
                    !response.isSuccessful -> {
                        Log.w(TAG, "Server returned ${response.code} for promo validate")
                        PromoResult.InvalidCode
                    }
                    else -> parseServerResponse(response.body?.string())
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Network error during promo validation", e)
            PromoResult.NetworkError
        }
    }

    private fun parseServerResponse(json: String?): PromoResult {
        if (json == null) return PromoResult.NetworkError
        return try {
            val obj       = JSONObject(json)
            val valid     = obj.optBoolean("valid", false)
            val typeLabel = obj.optString("type", "server")
            val expiresAt = obj.optLong("expires_at", -1L).takeIf { it > 0 }
            val nowSec    = System.currentTimeMillis() / 1000L

            when {
                !valid                          -> PromoResult.InvalidCode
                expiresAt != null && expiresAt < nowSec -> PromoResult.Expired
                else -> PromoResult.Success(
                    type      = PromoCodeType.fromLabel(typeLabel),
                    expiresAt = expiresAt?.let { it * 1000L },  // convert to millis
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse server response", e)
            PromoResult.NetworkError
        }
    }
}
