package com.lifeos.app

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions

/**
 * Runs Gemma 3 1B on-device (via MediaPipe LLM Inference) and turns a blob of OCR text into a
 * structured [LifeEvent].
 *
 * Create ONE of these once the model file is ready, reuse it for every capture, and [close] it
 * when done — the underlying [LlmInference] is a large native object that holds GPU/NPU resources.
 *
 * IMPORTANT: creating the engine and running [extract] are both SLOW and BLOCKING. Never call them
 * on the main thread — the ViewModel runs them on Dispatchers.IO.
 */
class GemmaEngine private constructor(private val llm: LlmInference) {

    companion object {
        private const val TAG = "LifeOS-Gemma"

        // ── Inference knobs ─────────────────────────────────────────────────────────────────
        // maxTokens is the MOST LIKELY thing to need tuning: the MediaPipe docs note Gemma3-1B
        // requires this to fit the model's built-in context size. If createFromOptions() throws
        // about token/context size on the phone, adjust this first. 1280 matches the common
        // Gemma3-1B .task context.
        private const val MAX_TOKENS = 1280
        // topK limits sampling; a smallish value keeps extraction focused. We only set the
        // options that the tasks-genai 0.10.x LlmInferenceOptions builder reliably exposes
        // (model path, max tokens, max topK). If extraction comes out too "creative", the next
        // step is to switch to the LlmInferenceSession API and set a low temperature there.
        private const val TOP_K = 40

        /**
         * Loads the model from [modelPath] and builds the engine. Blocking — call off-main.
         * Throws if the model is missing/incompatible; the caller turns that into a UI error.
         */
        fun create(context: Context, modelPath: String): GemmaEngine {
            Log.i(TAG, "Loading Gemma from $modelPath")
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .setMaxTopK(TOP_K)
                .build()
            return GemmaEngine(LlmInference.createFromOptions(context, options))
        }
    }

    /**
     * Ask Gemma to pull the key fields out of an OCR'd bill. Blocking — call off-main.
     * Always returns a [LifeEvent] (empty if the model produced nothing usable); never throws
     * for a bad model reply, so the UI can degrade gracefully per the brief's guardrails.
     */
    fun extract(ocrText: String): LifeEvent {
        val prompt = buildPrompt(ocrText)
        return try {
            val reply = llm.generateResponse(prompt)
            Log.d(TAG, "Model reply: $reply")
            LifeEvent.fromModelJson(reply, rawText = ocrText)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            LifeEvent(rawText = ocrText) // empty result, keep the source text
        }
    }

    fun close() = llm.close()

    /**
     * The extraction prompt. Kept tight and explicit because a 1B model follows short, concrete
     * instructions far better than long ones. We ask for ONLY JSON, give the exact keys, and show
     * one tiny example so the shape is unambiguous.
     */
    private fun buildPrompt(ocrText: String): String = """
        You read a scanned bill, invoice, or receipt and extract key facts.
        Return ONLY a JSON object — no explanation, no markdown — with exactly these keys:
        "type"     : short label like "electricity_bill" or "restaurant_receipt", or null
        "biller"   : the company/business NAME issuing it (usually the bold title at the very top), or null
        "amount"   : the FINAL total to pay. Look for "Total Payable", "Amount Due", or
                     "Current Payable Amount" — ignore individual line items. Keep decimals,
                     no currency symbol. Or null.
        "due_date" : payment due date as YYYY-MM-DD. Look for "Due Date" or "Bill Due Date".
                     If none is stated (e.g. an already-paid receipt), use null. Never use an
                     issue, print, or transaction date as the due date.

        Rules: use null when a value is not stated. Never guess. The two examples show the JSON
        FORMAT only — read the ACTUAL text below and do NOT copy the example values.

        Example 1: {"type":"electricity_bill","biller":"Adani Electricity","amount":"845.50","due_date":"2026-10-05"}
        Example 2: {"type":"restaurant_receipt","biller":"Cafe Central","amount":"54.50","due_date":null}

        Text:
        ""${'"'}
        $ocrText
        ""${'"'}

        JSON:
    """.trimIndent()
}
