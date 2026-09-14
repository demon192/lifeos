package com.lifeos.app

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Runs ML Kit OCR on a single still photo (the frozen capture) and returns the recognized text.
 *
 * Why a separate path from the live [TextAnalyzer]?
 * A still, well-framed, full-resolution photo OCRs far more accurately than the shaky live camera
 * frames M1 used — and accurate OCR text is the single biggest lever on Gemma's extraction quality.
 * Garbage text in = garbage fields out. So from M2's capture flow on, extraction reads the *frozen*
 * photo, not the live stream.
 *
 * BLOCKING: [recognizeBlocking] uses Tasks.await, so it must be called OFF the main thread. The
 * ViewModel calls it inside Dispatchers.IO.
 */
object StillImageOcr {

    // One recognizer, reused. DEFAULT_OPTIONS = the on-device Latin-script model (same as M1).
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * OCR [bitmap] and return all recognized text (may be blank if nothing readable was found).
     * The bitmap is already rotated upright by the caller, so we pass rotation 0.
     */
    fun recognizeBlocking(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = Tasks.await(recognizer.process(image))
        return result.text
    }
}
