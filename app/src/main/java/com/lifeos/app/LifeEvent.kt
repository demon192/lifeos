package com.lifeos.app

import org.json.JSONObject

/**
 * A single thing LifeOS understood from a document. For M2 this is the "understanding" output:
 * we point at (say) an electricity bill, and Gemma turns the messy OCR text into these fields.
 *
 * Every field except [rawText] is nullable on purpose — Gemma won't always find everything, and
 * the brief says to degrade gracefully rather than invent data. [rawText] keeps the original OCR
 * snapshot so we can always show the user the source of an AI guess ("here's what I read").
 *
 * Values are kept as plain Strings for now (e.g. amount "1,240", dueDate "2026-09-20"). Turning
 * them into real numbers/dates comes later, when M3 needs to schedule a reminder.
 */
data class LifeEvent(
    val type: String? = null,       // e.g. "electricity_bill"
    val biller: String? = null,     // who it's from, e.g. "BSES Rajdhani"
    val amount: String? = null,     // amount due, e.g. "1,240"
    val dueDate: String? = null,    // ISO date if found, e.g. "2026-09-20"
    val rawText: String = "",       // the OCR text this was extracted from (the source)
) {
    /** True if Gemma found nothing useful — the UI uses this to show a "couldn't structure" message. */
    val isEmpty: Boolean
        get() = biller.isNullOrBlank() && amount.isNullOrBlank() && dueDate.isNullOrBlank()

    companion object {
        /**
         * Build a LifeEvent from Gemma's JSON reply. Small models often wrap JSON in extra prose,
         * so we pull out the first {...} block before parsing. Anything missing/malformed just
         * becomes null — we never throw, so a bad model reply can't crash the app.
         */
        fun fromModelJson(modelReply: String, rawText: String): LifeEvent {
            val json = extractFirstJsonObject(modelReply) ?: return LifeEvent(rawText = rawText)
            return try {
                val obj = JSONObject(json)
                LifeEvent(
                    type = obj.optStringOrNull("type"),
                    biller = obj.optStringOrNull("biller"),
                    amount = obj.optStringOrNull("amount"),
                    dueDate = obj.optStringOrNull("due_date"),
                    rawText = rawText,
                )
            } catch (e: Exception) {
                LifeEvent(rawText = rawText)
            }
        }

        /** Returns the substring from the first '{' to its matching '}', or null if none. */
        private fun extractFirstJsonObject(text: String): String? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            return if (start != -1 && end > start) text.substring(start, end + 1) else null
        }

        // org.json returns the literal string "null" sometimes; treat that + blanks as absent.
        private fun JSONObject.optStringOrNull(key: String): String? {
            if (!has(key) || isNull(key)) return null
            val v = optString(key).trim()
            return if (v.isEmpty() || v.equals("null", ignoreCase = true)) null else v
        }
    }
}
