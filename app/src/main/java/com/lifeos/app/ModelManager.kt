package com.lifeos.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gets the Gemma model file onto the phone and tells the UI where it stands.
 *
 * The model (~555 MB) is too big to ship inside the app, so on the very first run we download it
 * once into the app's private storage. After that it's on the device forever — the app then works
 * fully offline (airplane mode included), which is a core LifeOS selling point.
 *
 * Two ways the model can arrive:
 *   1. DEV shortcut: the presenter `adb push`es the file to /data/local/tmp/llm/ — we detect it
 *      there and skip the download entirely.
 *   2. NORMAL: we stream it from [MODEL_URL] (a public GitHub Release asset) with a progress %.
 *
 * See LIFEOS-BUILD-GUIDE.md §10 for how to produce the file and set [MODEL_URL].
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "LifeOS-Model"

        /** The exact model file we use: Gemma 3 1B, 4-bit quantized, MediaPipe .task format. */
        const val MODEL_FILE_NAME = "gemma3-1b-it-int4.task"

        /** Roughly the expected size (555 MB). Used to sanity-check a finished download. */
        private const val EXPECTED_MIN_BYTES = 400_000_000L

        /**
         * TODO(setup): paste the public download URL of the model here.
         * Recommended: upload gemma3-1b-it-int4.task as a GitHub Release asset on the lifeos repo,
         * then use its "…/releases/download/<tag>/gemma3-1b-it-int4.task" URL. See build guide §10.
         * Leave blank to rely solely on the adb-push dev shortcut.
         */
        const val MODEL_URL = ""

        /** Where the presenter can `adb push` the model for the dev shortcut. */
        private const val ADB_DEV_PATH = "/data/local/tmp/llm/$MODEL_FILE_NAME"
    }

    /** The final resting place of the model inside the app's private storage. */
    private val targetFile: File
        get() = File(context.filesDir, MODEL_FILE_NAME)

    /** Absolute path MediaPipe should load once the model is ready. */
    fun modelPath(): String {
        // Prefer the adb-pushed dev copy if present, otherwise our downloaded copy.
        val adb = File(ADB_DEV_PATH)
        return if (adb.exists() && adb.length() > EXPECTED_MIN_BYTES) adb.absolutePath
        else targetFile.absolutePath
    }

    /**
     * Ensures the model is present, emitting status as it goes. Collect this from a coroutine.
     * Emits: Checking → (Ready) OR (Downloading% … → Ready) OR Failed.
     */
    fun ensureModel(): Flow<ModelStatus> = flow {
        emit(ModelStatus.Checking)

        // 1) Already available (downloaded before, or adb-pushed)? Then we're done.
        val adb = File(ADB_DEV_PATH)
        if (adb.exists() && adb.length() > EXPECTED_MIN_BYTES) {
            Log.i(TAG, "Using adb-pushed model at $ADB_DEV_PATH")
            emit(ModelStatus.Ready); return@flow
        }
        if (targetFile.exists() && targetFile.length() > EXPECTED_MIN_BYTES) {
            Log.i(TAG, "Model already downloaded at ${targetFile.absolutePath}")
            emit(ModelStatus.Ready); return@flow
        }

        // 2) Need to download — but only if a URL was configured.
        if (MODEL_URL.isBlank()) {
            emit(
                ModelStatus.Failed(
                    "No model on device and no download URL set. See build guide §10 " +
                        "(set MODEL_URL) or adb push the model to $ADB_DEV_PATH."
                )
            )
            return@flow
        }

        try {
            downloadWithProgress { emit(it) }
            emit(ModelStatus.Ready)
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            emit(ModelStatus.Failed("Download failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO) // all file/network work happens off the main thread

    /**
     * Streams [MODEL_URL] to a temp file, reporting Downloading(percent), then atomically renames
     * to the final file so a half-finished download is never mistaken for a complete one.
     */
    private suspend fun downloadWithProgress(emit: suspend (ModelStatus) -> Unit) {
        val tmp = File(context.filesDir, "$MODEL_FILE_NAME.part")
        if (tmp.exists()) tmp.delete()

        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = true // GitHub release assets redirect to a CDN
        }
        connection.connect()
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Server returned HTTP ${connection.responseCode}")
        }

        val total = connection.contentLengthLong // may be -1 if unknown
        var downloaded = 0L
        var lastPercent = -1

        connection.inputStream.use { input ->
            tmp.outputStream().use { output ->
                val buffer = ByteArray(1 shl 16) // 64 KB chunks
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) {
                        val percent = ((downloaded * 100) / total).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            emit(ModelStatus.Downloading(percent))
                        }
                    }
                }
            }
        }
        connection.disconnect()

        if (tmp.length() < EXPECTED_MIN_BYTES) {
            tmp.delete()
            throw IllegalStateException("Downloaded file too small (${tmp.length()} bytes) — corrupt?")
        }

        // Atomic swap into place.
        if (targetFile.exists()) targetFile.delete()
        if (!tmp.renameTo(targetFile)) {
            throw IllegalStateException("Could not move downloaded model into place")
        }
    }
}

/** Where the model stands, for the UI to react to. */
sealed interface ModelStatus {
    data object Checking : ModelStatus
    data class Downloading(val percent: Int) : ModelStatus
    data object Ready : ModelStatus
    data class Failed(val message: String) : ModelStatus
}
