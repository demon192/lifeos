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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import java.util.concurrent.Executors

private const val TAG = "LifeOS-Camera"

// Shared colors for the overlays.
private val Ink = Color(0xFF0B0F14)
private val PanelBg = Color(0xCC0B0F14) // semi-transparent ink
private val Teal = Color(0xFF7FE3E1)

/**
 * The main LifeOS screen.
 *
 * M1: camera + live OCR (still here).
 * M2: a Capture button freezes the OCR text and Gemma turns it into a structured result card.
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
    // Latest live OCR text (M1 behaviour). This is the snapshot we freeze on Capture.
    var recognizedText by remember { mutableStateOf("") }

    val modelState by viewModel.modelState.collectAsState()
    val captureState by viewModel.captureState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        // The camera preview always runs underneath everything.
        CameraPreview(
            onTextFound = { text -> recognizedText = text },
            modifier = Modifier.fillMaxSize(),
        )

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
                is CaptureState.Idle -> LivePanel(
                    recognizedText = recognizedText,
                    modelReady = modelState == ModelStatus.Ready,
                    onCapture = { viewModel.onCapture(recognizedText) },
                )

                is CaptureState.Extracting -> ThinkingPanel()

                is CaptureState.Result -> ResultCard(
                    event = s.event,
                    onScanAgain = viewModel::scanAgain,
                )

                is CaptureState.Error -> ErrorPanel(
                    message = s.message,
                    onScanAgain = viewModel::scanAgain,
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

/** Idle state: live OCR readout + the Capture button. */
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
            text = "Reading (live, on-device):",
            color = Teal,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = recognizedText.ifBlank { "Point the camera at a bill…" },
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 140.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        )
        Button(
            onClick = onCapture,
            enabled = modelReady && recognizedText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (modelReady) "Capture & understand" else "Preparing AI…")
        }
    }
}

/** Extracting state: Gemma is thinking. */
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

/** Error state. Camera stays live behind it; user can just scan again. */
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
 * Wraps CameraX inside Compose (unchanged from M1): a live [Preview] plus an [ImageAnalysis]
 * stream feeding each frame to [TextAnalyzer] for OCR.
 */
@Composable
private fun CameraPreview(
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
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bind camera use-cases", e)
                }
            }, ContextCompat.getMainExecutor(context))

            previewView
        },
    )
}
