package com.javimetallab.papercript.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Text recognition, used only to read a printed password while encrypting.
 *
 * Reading a card code does not go through here: that is the QR scanner's job,
 * which is reliable.
 *
 * The model ships inside the APK, so nothing goes over the network.
 */
object TextExtract {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): Text = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }

    /**
     * Returns the recognized lines so the user picks which one is the password.
     * No attempt is made to guess a single one: in practice the text is
     * surrounded by other lines (a service name, a contract number) and guessing
     * automatically fails more often than it helps.
     *
     * Sorted with the most password-looking ones first.
     */
    fun passwordCandidates(text: Text): List<String> =
        text.textBlocks
            .flatMap { block -> block.lines }
            .map { it.text.trim() }
            .filter { it.isNotEmpty() && it.length <= 128 }
            .distinct()
            .sortedByDescending(::passwordScore)

    /**
     * A "this looks like a password" heuristic: variety of character classes and
     * a sensible length. It only affects ordering, never discards anything.
     */
    private fun passwordScore(candidate: String): Int {
        if (candidate.contains(' ')) return -1

        var score = 0
        if (candidate.any { it.isDigit() }) score += 2
        if (candidate.any { it.isUpperCase() }) score += 2
        if (candidate.any { it.isLowerCase() }) score += 1
        if (candidate.any { !it.isLetterOrDigit() }) score += 3
        score += when (candidate.length) {
            in 12..64 -> 4
            in 8..11 -> 3
            in 6..7 -> 1
            else -> 0
        }
        return score
    }
}
