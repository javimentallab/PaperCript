package com.javimetallab.papercript.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.ImageProxy

/**
 * Prepares the photo before handing it to the OCR.
 *
 * Input quality is the only thing we control, and with small text it decides
 * whether anything gets read at all.
 */
object ImagePrep {

    /** Turns the CameraX JPEG capture into an already upright bitmap. */
    fun toUprightBitmap(proxy: ImageProxy): Bitmap? {
        val buffer = proxy.planes.firstOrNull()?.buffer ?: return null
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

        val degrees = proxy.imageInfo.rotationDegrees
        if (degrees == 0) return decoded

        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated != decoded) decoded.recycle()
        return rotated
    }

    /**
     * Crops to the guide rectangle. The fractions are the same ones the UI draws
     * over the preview, and they line up with the image because the preview uses
     * FIT_CENTER: the whole frame is visible, uncropped by the camera.
     */
    fun cropToGuide(
        source: Bitmap,
        widthFraction: Float,
        heightFraction: Float
    ): Bitmap {
        val w = (source.width * widthFraction).toInt().coerceIn(1, source.width)
        val h = (source.height * heightFraction).toInt().coerceIn(1, source.height)
        val x = (source.width - w) / 2
        val y = (source.height - h) / 2
        return Bitmap.createBitmap(source, x, y, w, h)
    }

    /**
     * Greyscales and stretches contrast. Faint blue or grey ink on cream paper is
     * exactly what the recognizer reads worst; pushing it to black on white helps
     * more than any other adjustment.
     */
    fun enhance(source: Bitmap, contrast: Float = 1.8f): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val translate = (1f - contrast) * 128f

        val matrix = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        Canvas(out).drawBitmap(
            source,
            0f,
            0f,
            Paint().apply {
                isAntiAlias = true
                colorFilter = ColorMatrixColorFilter(matrix)
            }
        )
        return out
    }

    /**
     * Upscales small images. The recognizer needs a certain character height;
     * below roughly 1200 px of usable width it starts losing strokes.
     */
    fun upscaleIfSmall(source: Bitmap, minWidth: Int = 1400): Bitmap {
        if (source.width >= minWidth) return source
        val scale = minWidth.toFloat() / source.width
        return Bitmap.createScaledBitmap(
            source,
            minWidth,
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }
}
