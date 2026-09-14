package com.lifeos.app

import android.Manifest
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors

private const val TAG = "LifeOS-Camera"

/**
 * Milestone 1 screen.
 *
 * Step 1: make sure we're allowed to use the camera (ask if not).
 * Step 2: show the live camera, run OCR on it, and print whatever text it sees.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermission.status.isGranted) {
        CameraContent(modifier = modifier)
    } else {
        // No permission yet — explain why and offer a button to grant it.
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "LifeOS needs the camera so it can read documents you point it at.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = { cameraPermission.launchPermissionRequest() },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Allow camera")
            }
        }
    }
}

/** Live camera + the text-so-far overlay. Only shown once permission is granted. */
@Composable
private fun CameraContent(modifier: Modifier = Modifier) {
    // The most recent text ML Kit has read. Updating this redraws the overlay automatically.
    var recognizedText by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        CameraPreview(
            onTextFound = { text -> recognizedText = text },
            modifier = Modifier.fillMaxSize(),
        )

        // The overlay: a dark panel pinned to the bottom showing whatever text is in view.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xCC0B0F14)) // semi-transparent ink
                .padding(16.dp),
        ) {
            Text(
                text = "Reading (live, on-device):",
                color = Color(0xFF7FE3E1),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = recognizedText.ifBlank { "Point the camera at some text…" },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
            )
        }
    }
}

/**
 * Wraps CameraX inside Compose.
 *
 * CameraX is an older-style Android "View", so we drop it into Compose with [AndroidView].
 * We wire up two things: a live [Preview] (what you see) and an [ImageAnalysis] stream that
 * feeds each frame to [TextAnalyzer] for OCR.
 */
@Composable
private fun CameraPreview(
    onTextFound: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // A background thread just for running OCR, so the camera preview never stutters.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    // Shut that thread down when this screen goes away.
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val previewView = PreviewView(context)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // 1) The live preview surface.
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // 2) The analysis stream. KEEP_ONLY_LATEST = if OCR is slow, drop old frames
                //    and always work on the newest one (keeps things responsive).
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(analysisExecutor, TextAnalyzer(onTextFound))
                    }

                try {
                    // Rebind from scratch, then attach both use-cases to the back camera.
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bind camera use-cases", e)
                }
            }, ContextCompat.getMainExecutor(context))

            previewView
        },
    )
}
