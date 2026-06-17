package com.ghostwave.app.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR code generation (ZXing Java) and content extraction utilities.
 *
 * The QR payload for GhostWave is a JSON-encoded prekey bundle:
 *   { "gwId": "GW-AAAA-BBBB", "name": "Alice", "pubKey": "<base64>" }
 *
 * This lets a contact scan the QR code and immediately have:
 *  1. The peer's GW-ID for display
 *  2. Their identity public key for X3DH session initiation
 */
object QrCodeUtil {

    private const val QR_VERSION_PREFIX = "ghostwave:v1:"

    /**
     * Generates a white-on-black QR bitmap from [content].
     * Uses M-level error correction (recovers up to 15% of data) — balances
     * data density against logo overlay support in future branding iterations.
     *
     * @param foreground  Module colour (default: black for white-background display)
     * @param background  Background colour
     */
    fun generateQrBitmap(
        content:    String,
        sizePx:     Int   = 512,
        foreground: Int   = Color.BLACK,
        background: Int   = Color.WHITE,
    ): Bitmap? = try {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN          to 1,
            EncodeHintType.CHARACTER_SET   to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) foreground else background)
            }
        }
        bitmap
    } catch (_: WriterException) { null }

    /** Encodes a GhostWave contact payload into a versioned QR string. */
    fun encodeContactPayload(gwId: String, displayName: String, publicKeyBase64: String): String =
        "$QR_VERSION_PREFIX${gwId}|${displayName}|${publicKeyBase64}"

    /**
     * Parses a scanned QR string.
     * Returns null if the content is not a GhostWave QR code.
     */
    fun decodeContactPayload(raw: String): ContactPayload? {
        if (!raw.startsWith(QR_VERSION_PREFIX)) return null
        val body   = raw.removePrefix(QR_VERSION_PREFIX)
        val parts  = body.split("|")
        if (parts.size < 3) return null
        val gwId   = parts[0].trim()
        val name   = parts[1].trim()
        val pubKey = parts.drop(2).joinToString("|").trim() // pubKey may contain '='
        if (!GhostWaveIdUtil.isValid(gwId)) return null
        return ContactPayload(gwId, name, pubKey)
    }

    data class ContactPayload(
        val ghostWaveId:    String,
        val displayName:    String,
        val publicKeyBase64: String,
    )
}
