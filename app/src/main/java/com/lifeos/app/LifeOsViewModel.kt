package com.lifeos.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The brain of M2. Holds all the state that must survive screen rotation and recomposition, and
 * keeps the heavy work (downloading the model, running Gemma) OFF the main thread so the UI never
 * freezes.
 *
 * Why a ViewModel here, when M1 kept everything inside the composable with `remember`?
 * Because this state is long-lived and expensive: a 555 MB download and a loaded LLM. If we kept
 * it in the composable, every rotation would throw it away and restart the download. A ViewModel
 * outlives those UI rebuilds, so we download once and load the model once.
 *
 * The UI observes two things:
 *   - [modelState]   → is the model still downloading / ready / broken?  (drives the banner)
 *   - [captureState] → idle, thinking, got-a-result, or error            (drives the result card)
 */
class LifeOsViewModel(app: Application) : AndroidViewModel(app) {

    private val modelManager = ModelManager(app)

    // Built lazily once the model file is Ready. Reused for every capture.
    private var engine: GemmaEngine? = null

    private val _modelState = MutableStateFlow<ModelStatus>(ModelStatus.Checking)
    val modelState: StateFlow<ModelStatus> = _modelState.asStateFlow()

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    init {
        // Kick off model check/download as soon as the screen opens.
        viewModelScope.launch {
            modelManager.ensureModel().collect { status ->
                _modelState.value = status
            }
        }
    }

    /**
     * Called when the user taps Capture with a frozen OCR snapshot. Loads the engine if needed,
     * then runs extraction on a background thread and publishes the result.
     */
    fun onCapture(ocrSnapshot: String) {
        if (ocrSnapshot.isBlank()) return
        if (_modelState.value != ModelStatus.Ready) {
            _captureState.value = CaptureState.Error("The AI model isn't ready yet.")
            return
        }

        _captureState.value = CaptureState.Extracting
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val e = engine ?: GemmaEngine.create(getApplication(), modelManager.modelPath())
                        .also { engine = it }
                    e.extract(ocrSnapshot)
                }
                _captureState.value = CaptureState.Result(result)
            } catch (t: Throwable) {
                Log.e("LifeOS-VM", "Capture/extract failed", t)
                _captureState.value = CaptureState.Error(t.message ?: "Something went wrong.")
            }
        }
    }

    /** Return to live scanning. */
    fun scanAgain() {
        _captureState.value = CaptureState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        engine?.close() // release the big native LLM object
        engine = null
    }
}

/** What the capture flow is doing right now, for the UI to react to. */
sealed interface CaptureState {
    data object Idle : CaptureState                       // live scanning, nothing captured
    data object Extracting : CaptureState                 // Gemma is thinking
    data class Result(val event: LifeEvent) : CaptureState
    data class Error(val message: String) : CaptureState
}
