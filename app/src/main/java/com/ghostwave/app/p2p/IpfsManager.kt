package com.ghostwave.app.p2p

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ghostwave.app.crypto.MediaEncryptor
import com.ghostwave.app.crypto.MediaKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IpfsManager"

/**
 * Manages encrypted file upload and retrieval via IPFS.
 *
 * Uses a public IPFS HTTP gateway + Kubo RPC API for now.
 * In production this is replaced by a local Helia (JS IPFS) node running
 * inside a WebView / Capacitor bridge, or a bundled Go-IPFS binary.
 *
 * Encryption protocol (per-file, from MediaEncryptor):
 *   1. Generate random AES-256 key + 12-byte IV
 *   2. Encrypt file bytes with AES-256-GCM → ciphertext
 *   3. Upload ciphertext to IPFS → receive CID
 *   4. Send (CID, keyBase64, ivBase64) via Signal-encrypted message
 *   5. Recipient fetches CID → decrypts with received key+IV
 *
 * NEVER store plaintext files on IPFS. The CID is public; only the
 * Signal-encrypted key+IV pair in the message gives access.
 */
@Singleton
class IpfsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaEncryptor: MediaEncryptor,
) {
    // Local Kubo node endpoint (running on-device via binary or Helia bridge)
    private val apiBase = "http://127.0.0.1:5001/api/v0"
    private val gatewayBase = "http://127.0.0.1:8080/ipfs"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Encrypts the content at [uri] and uploads the ciphertext to IPFS.
     * Returns the [IpfsUploadResult] containing (CID, MediaKey).
     * The caller must send (CID, key, IV) to the recipient via a Signal message.
     */
    suspend fun uploadEncrypted(uri: Uri, mimeType: String): IpfsUploadResult =
        withContext(Dispatchers.IO) {
            val mediaKey = mediaEncryptor.generateMediaKey()

            // Encrypt → buffer
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: error("Cannot open input stream for $uri")
            val cipherBuffer = ByteArrayOutputStream()
            inputStream.use { input ->
                mediaEncryptor.encrypt(input, cipherBuffer, mediaKey)
            }
            val cipherBytes = cipherBuffer.toByteArray()

            // Upload to IPFS
            val cid = uploadBytesToIpfs(cipherBytes, mimeType)

            Log.i(TAG, "Uploaded ${cipherBytes.size} bytes → CID=$cid")
            IpfsUploadResult(
                cid          = cid,
                mediaKey     = mediaKey,
                mimeType     = mimeType,
                sizeBytes    = cipherBytes.size.toLong(),
            )
        }

    /**
     * Downloads and decrypts an IPFS-hosted ciphertext.
     * Returns the decrypted plaintext bytes.
     * @throws javax.crypto.AEADBadTagException if the ciphertext was tampered.
     */
    suspend fun downloadAndDecrypt(cid: String, mediaKey: MediaKey): ByteArray =
        withContext(Dispatchers.IO) {
            val cipherBytes = downloadFromIpfs(cid)
            val plainBuffer = ByteArrayOutputStream()
            cipherBytes.inputStream().use { input ->
                mediaEncryptor.decrypt(input, plainBuffer, mediaKey)
            }
            plainBuffer.toByteArray()
        }

    // ── Internal HTTP helpers ─────────────────────────────────────────────

    private fun uploadBytesToIpfs(bytes: ByteArray, mimeType: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", "encrypted",
                bytes.toRequestBody(mimeType.toMediaType()),
            )
            .build()

        val request = Request.Builder()
            .url("$apiBase/add?pin=true&quieter=true")
            .post(body)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) error("IPFS upload failed: ${response.code}")

        val json = JSONObject(response.body!!.string())
        return json.getString("Hash")
    }

    private fun downloadFromIpfs(cid: String): ByteArray {
        val request = Request.Builder()
            .url("$gatewayBase/$cid")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) error("IPFS download failed: ${response.code}")
        return response.body!!.bytes()
    }
}

data class IpfsUploadResult(
    val cid:       String,
    val mediaKey:  MediaKey,
    val mimeType:  String,
    val sizeBytes: Long,
)
