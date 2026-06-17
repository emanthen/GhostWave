package com.ghostwave.app.crypto

import android.util.Base64
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

private const val AES_GCM         = "AES/GCM/NoPadding"
private const val GCM_TAG_BITS    = 128
private const val KEY_SIZE_BYTES  = 32   // AES-256
private const val IV_SIZE_BYTES   = 12   // 96-bit GCM IV

/**
 * AES-256-GCM encryption/decryption for media files (photos, files shared via IPFS).
 *
 * Protocol:
 *   1. Generate random 32-byte key + 12-byte IV per file (SecureRandom).
 *   2. Encrypt file stream → write to IPFS (ciphertext + 16-byte auth tag appended by GCM).
 *   3. Send (IPFS CID, key, IV) via Signal-encrypted message to recipient.
 *   4. Recipient fetches ciphertext from IPFS, decrypts with received key+IV.
 *
 * Why GCM (authenticated encryption)?
 *   Without authentication, an attacker who controls IPFS could replace the
 *   ciphertext with a chosen-ciphertext attack payload. GCM's 16-byte auth tag
 *   detects any bit-flip in the ciphertext before any plaintext is released.
 */
@Singleton
class MediaEncryptor @Inject constructor() {

    /**
     * Generates a fresh random AES-256 key + IV pair for one file.
     * Must be called once per file — never reuse a (key, IV) pair.
     */
    fun generateMediaKey(): MediaKey {
        val key = ByteArray(KEY_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        val iv  = ByteArray(IV_SIZE_BYTES).also  { SecureRandom().nextBytes(it) }
        return MediaKey(
            keyBase64 = Base64.encodeToString(key, Base64.NO_WRAP),
            ivBase64  = Base64.encodeToString(iv,  Base64.NO_WRAP),
        )
    }

    /**
     * Encrypts [input] stream into [output] stream using [mediaKey].
     * The GCM auth tag is appended to the ciphertext by the JCE automatically.
     * Returns total bytes written (ciphertext size = plaintext size + 16).
     */
    fun encrypt(input: InputStream, output: OutputStream, mediaKey: MediaKey): Long {
        val key    = Base64.decode(mediaKey.keyBase64, Base64.NO_WRAP)
        val iv     = Base64.decode(mediaKey.ivBase64,  Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        key.fill(0)   // zero key bytes after cipher init
        var written = 0L
        CipherOutputStream(output, cipher).use { cos ->
            val buf = ByteArray(8192)
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                cos.write(buf, 0, n)
                written += n
            }
        }
        return written
    }

    /**
     * Decrypts [input] stream into [output] stream.
     * @throws javax.crypto.AEADBadTagException if ciphertext was tampered with.
     */
    fun decrypt(input: InputStream, output: OutputStream, mediaKey: MediaKey) {
        val key    = Base64.decode(mediaKey.keyBase64, Base64.NO_WRAP)
        val iv     = Base64.decode(mediaKey.ivBase64,  Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        key.fill(0)
        // GCM requires reading the entire ciphertext to verify the auth tag.
        // For large files we buffer into memory then decrypt — acceptable for
        // ≤50MB files per the spec. A streaming alternative (e.g. AES-CTR + separate HMAC)
        // can replace this in a future optimisation pass.
        val ciphertext = input.readBytes()
        output.write(cipher.doFinal(ciphertext))
    }
}

data class MediaKey(
    val keyBase64: String,
    val ivBase64:  String,
)
