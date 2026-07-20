package com.javimetallab.papercript.qr

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.javimetallab.papercript.crypto.Base32
import java.io.ByteArrayOutputStream

/**
 * Builds and reads the contents of the QR codes on the printed sheet.
 *
 * The QR carries exactly what is printed as text: the label and the encrypted
 * code. Neither is secret on its own, which is the whole point of the format:
 * the paper can be lost without the passwords going with it, because without
 * the master password the code is worthless.
 */
object QrCodec {

    private const val PREFIX = "LBR1"
    private const val SEPARATOR = "|"

    data class Parsed(val label: String, val code: String)

    /**
     * Builds the QR payload.
     *
     * The code comes before the label on purpose. The label is free user text
     * and may contain the vertical bar used as delimiter; the code, being
     * Base32, never can. With the label last, splitting into three parts and
     * taking the remainder as the label needs no escaping at all.
     */
    fun payload(label: String, code: String): String =
        PREFIX + SEPARATOR + Base32.normalize(code) + SEPARATOR + label

    /** Returns null if the scanned QR does not come from this app. */
    fun parse(raw: String): Parsed? {
        val parts = raw.trim().split(SEPARATOR, limit = 3)
        if (parts.size != 3 || parts[0] != PREFIX) return null
        val code = parts[1].uppercase()
        if (code.isEmpty()) return null
        return Parsed(label = parts[2], code = code)
    }

    /**
     * Renders the QR.
     *
     * Error correction level Q (recovers up to 25% of a damaged symbol) because
     * these cards get cut out, folded and handled; at the low level a crease in
     * the wrong place leaves the QR unreadable.
     */
    fun bitmap(payload: String, sizePx: Int = 600): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            // Four-module quiet zone, which the spec requires for a reader to
            // locate the symbol at all.
            EncodeHintType.MARGIN to 4
        )

        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    /** The QR as a data URI, to embed in the HTML sent to the printer. */
    fun pngDataUri(payload: String, sizePx: Int = 600): String {
        val bitmap = bitmap(payload, sizePx)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        return "data:image/png;base64,$base64"
    }
}
