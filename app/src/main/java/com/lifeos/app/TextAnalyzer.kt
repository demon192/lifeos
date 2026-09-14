package com.lifeos.app

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Runs ML Kit's on-device text recognition on each camera frame and reports the text it finds.
 *
 * CameraX hands us frames one at a time (as [ImageProxy]). We wrap each frame in the shape
 * ML Kit wants ([InputImage]), ask ML Kit to read it, and pass the result back through
 * [onTextFound]. Everything here runs on-device — no internet, no API key.
 *
 * @param onTextFound called with the recognized text after each frame (empty string if none).
 */
class TextAnalyzer(
    private val onTextFound: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    // The recognizer is reusable — build it once, not per frame.
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // We must close each frame when done, or the camera stalls. @SuppressLint is needed
    // because we touch imageProxy.image directly (CameraX flags that as experimental).
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // rotationDegrees tells ML Kit how the phone is held so text isn't read sideways.
        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                onTextFound(visionText.text)
            }
            .addOnFailureListener {
                // Reading failed for this frame — just skip it, the next frame will try again.
                onTextFound("")
            }
            .addOnCompleteListener {
                // ALWAYS close the frame (success or failure) so the next one can arrive.
                imageProxy.close()
            }
    }
}
