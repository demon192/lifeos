package com.lifeos.app

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private const val TAG = "LifeOS-Camera"

// Shared colors for the overlays.
private val Ink = Color(0xFF0B0F14)
private val PanelBg = Color(0xCC0B0F14) // semi-transparent ink
private val Teal = Color(0xFF7FE3E1)

/**
 * The main LifeOS screen.
 *
 * M1: camera + live OCR.
 * M2: a Capture button takes a still photo, you confirm it (Retake / Use), and only then does the
 *     app OCR that sharp still and run Gemma on it — turning the document into a structured card.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermission.status.isGranted) {
        CameraContent(modifier = modifier)
    } else {
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

@Composable
private fun CameraContent(
    modifier: Modifier = Modifier,
    viewModel: LifeOsViewModel = viewModel(),
) {
    val context = LocalContext.current

    // Live OCR readout (M1 feel) — purely informational now; extraction uses the frozen photo.
    var recognizedText by remember { mutableStateOf("") }

    // The frozen still photo awaiting Retake / Use. Null = live camera is showing.
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // CameraX still-capture use case. Built once here, bound inside CameraPreview, fired by Capture.
    val imageCapture = remember { ImageCapture.Builder().build() }

    val modelState by viewModel.modelState.collectAsState()
    val captureState by viewModel.captureState.collectAsState()

    // Go back to a fresh live scan (used by "Scan again"): drop the photo AND reset the flow state.
    val resetToLive = {
        capturedBitmap = null
        viewModel.scanAgain()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // The live camera preview always runs underneath everything.
        CameraPreview(
            imageCapture = imageCapture,
            onTextFound = { text -> recognizedText = text },
            modifier = Modifier.fillMaxSize(),
        )

        // Once a photo is frozen, show it on top of the live preview for the whole
        // confirm → extract → result flow, so the user always sees what was analysed.
        capturedBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Captured document",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Ink),
            )
        }

        // Top banner: model download / readiness status.
        ModelBanner(
            state = modelState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )

        // Bottom area changes with the capture flow.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            when (val s = captureState) {
                is CaptureState.Idle ->
                    if (capturedBitmap == null) {
                        // Live: frame the bill, then Capture takes a still photo.
                        LivePanel(
                            recognizedText = recognizedText,
                            modelReady = modelState == ModelStatus.Ready,
                            onCapture = {
                                takePhoto(
                                    imageCapture = imageCapture,
                                    executor = ContextCompat.getMainExecutor(context),
                                    onCaptured = { capturedBitmap = it },
                                )
                            },
                        )
                    } else {
                        // A photo is frozen: confirm it (Use) or discard it (Retake).
                        ConfirmPanel(
                            onRetake = { capturedBitmap = null },
                            onUse = { capturedBitmap?.let { viewModel.onPhotoConfirmed(it) } },
                        )
                    }

                is CaptureState.Extracting -> ThinkingPanel()

                is CaptureState.Result -> ResultCard(
                    event = s.event,
                    onScanAgain = resetToLive,
                )

                is CaptureState.Error -> ErrorPanel(
                    message = s.message,
                    onScanAgain = resetToLive,
                )
            }
        }
    }
}

/** Top banner shown while the model isn't ready yet. Hidden once Ready. */
@Composable
private fun ModelBanner(state: ModelStatus, modifier: Modifier = Modifier) {
    when (state) {
        ModelStatus.Ready -> Unit // nothing to show
        ModelStatus.Checking -> Banner(modifier, "Preparing the on-device AI…")
        is ModelStatus.Downloading -> Column(
            modifier = modifier
                .background(PanelBg)
                .padding(16.dp),
        ) {
            Text(
                "Downloading AI model (one time)… ${state.percent}%",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        is ModelStatus.Failed -> Column(
            modifier = modifier
                .background(PanelBg)
                .padding(16.dp),
        ) {
            Text("AI model not available", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
            Text(state.message, color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Banner(modifier: Modifier, text: String) {
    Row(
        modifier = modifier
            .background(PanelBg)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.height(18.dp))
        Spacer(Modifier.height(0.dp))
        Text("  $text", color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/** Idle/live state: small live OCR readout + the Capture button that takes a still photo. */
@Composable
private fun LivePanel(
    recognizedText: String,
    modelReady: Boolean,
    onCapture: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBg)
            .padding(16.dp),
    ) {
        Text(
            text = "Point at a bill, then Capture:",
            color = Teal,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = recognizedText.ifBlank { "Live preview…" },
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 100.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        )
        Button(
            onClick = onCapture,
            enabled = modelReady,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (modelReady) "Capture" else "Preparing AI…")
        }
    }
}

/** Confirm state: a still photo is frozen; keep it (Use) or discard it (Retake). */
@Composable
private fun ConfirmPanel(onRetake: () -> Unit, onUse: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBg)
            .padding(16.dp),
    ) {
        Text("Use this photo?", color = Teal, style = MaterialTheme.typography.labelMedium)
        Text(
            "Check the whole bill is sharp and readable — clear photos give far better results.",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) {
                Text("Retake")
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onUse, modifier = Modifier.weight(1f)) {
                Text("Use & understand")
            }
        }
    }
}

/** Extracting state: OCR + Gemma are working. */
@Composable
private fun ThinkingPanel() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            "Understanding the document…",
            color = Color.White,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Result state: the structured LifeEvent, clearly labelled as AI-extracted (verify!). */
@Composable
private fun ResultCard(event: LifeEvent, onScanAgain: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink)
            .padding(16.dp),
    ) {
        Text("Understood", color = Teal, fontWeight = FontWeight.Bold)

        if (event.isEmpty) {
            Text(
                "Couldn't pull structured details from this. Here's the raw text I read:",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                event.rawText.ifBlank { "(nothing)" },
                color = Color(0xFFB0BEC5),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp),
            )
        } else {
            Field("Biller", event.biller)
            Field("Amount due", event.amount)
            Field("Due date", event.dueDate)
            Text(
                "AI-extracted — please verify against the document.",
                color = Color(0xFFFFCC80),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        OutlinedButton(
            onClick = onScanAgain,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text("Scan again")
        }
    }
}

@Composable
private fun Field(label: String, value: String?) {
    Row(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            "$label: ",
            color = Teal,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            value ?: "— not found —",
            color = if (value != null) Color.White else Color(0xFF90A4AE),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Error state. The frozen photo stays behind it; user can just scan again. */
@Composable
private fun ErrorPanel(message: String, onScanAgain: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink)
            .padding(16.dp),
    ) {
        Text("Couldn't understand that", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
        Text(message, color = Color.White, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = onScanAgain,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text("Try again")
        }
    }
}

/**
 * Fire a single still capture. The result comes back on [executor]; we rotate it upright and hand
 * the bitmap back via [onCaptured]. Called from the Capture button.
 */
private fun takePhoto(
    imageCapture: ImageCapture,
    executor: Executor,
    onCaptured: (Bitmap) -> Unit,
) {
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = image.toUprightBitmap()
                image.close()
                onCaptured(bitmap)
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "takePicture failed", exc)
            }
        },
    )
}

/** Convert a captured [ImageProxy] to a Bitmap that is rotated the right way up. */
private fun ImageProxy.toUprightBitmap(): Bitmap {
    val raw = toBitmap()
    val degrees = imageInfo.rotationDegrees
    if (degrees == 0) return raw
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}

/**
 * Wraps CameraX inside Compose: a live [Preview], an [ImageAnalysis] stream feeding each frame to
 * [TextAnalyzer] for the live OCR readout, and the [imageCapture] use case for taking still photos.
 */
@Composable
private fun CameraPreview(
    imageCapture: ImageCapture,
    onTextFound: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
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

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(analysisExecutor, TextAnalyzer(onTextFound))
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                        imageCapture,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bind camera use-cases", e)
                }
            }, ContextCompat.getMainExecutor(context))

            previewView
        },
    )
}
