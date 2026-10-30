package com.devxrohan.smusic.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

object AiTranslationService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun translate(
        lines: List<String>,
        targetLanguageCode: String,
        targetLanguageName: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
        customPrompt: String? = null,
        provider: String = "OpenRouter",
        maxRetries: Int = 3,
        onLog: ((String) -> Unit)? = null
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) {
            return@withContext Result.failure(Exception("Input lines are empty"))
        }

        val effectiveApiKey = apiKey.trim()
        if (effectiveApiKey.isBlank() && provider != "Custom" && provider != "Puter" && provider != "Google") {
            return@withContext Result.failure(Exception("API Key is required. Please set it in Settings > AI Integration."))
        }

        val lineCount = lines.size
        val inputText = JSONArray(lines).toString()

        onLog?.invoke("Preparing translation for $lineCount lines to $targetLanguageName...")

        // Handle DeepL provider
        if (provider.equals("DeepL", ignoreCase = true)) {
            return@withContext translateWithDeepL(
                lines = lines,
                targetLangCode = targetLanguageCode,
                apiKey = effectiveApiKey,
                onLog = onLog
            )
        }

        // Free, no-key translation approach: Google's free, unauthenticated
        // "dict-chrome-ex" endpoint. No account, no API key, nothing to configure.
        if (provider.equals("Google", ignoreCase = true)) {
            return@withContext translateWithGoogleFree(
                lines = lines,
                targetLangCode = targetLanguageCode,
                onLog = onLog
            )
        }

        // Standard OpenAI-compatible format (OpenRouter, OpenAI, Groq, Gemini, Claude-proxy, Custom)
        val isHinglish = targetLanguageCode == "hi-Latn" || targetLanguageName.contains("Hinglish", ignoreCase = true)

        val systemPrompt = buildString {
            append("You are an expert, award-winning music lyrics translator. Your task is to translate song lyrics line-by-line.\n")
            append("CRITICAL OUTPUT RULES:\n")
            append("1. Output MUST be ONLY a valid JSON array of strings: [\"line 1\", \"line 2\", ...]\n")
            append("2. Output EXACTLY $lineCount items in the array (one output line per input line).\n")
            append("3. NEVER merge two input lines into one output item. Keep each lyric line separate and in the same order.\n")
            append("4. If an input line is empty or purely whitespace, the output array item MUST be empty string \"\".\n")
            append("5. Maintain the rhythm, poetic flow, and emotional feeling of the song lyrics.\n")
            append("6. DO NOT wrap with markdown, DO NOT write any explanations, intro or outro text.\n")
            if (isHinglish) {
                append("7. For HINGLISH: You MUST translate into natural, conversational Hindi using ONLY English/Latin alphabet letters (e.g., 'Tum mere paas ho', 'Dil ki dhadkan', 'Zindagi ek safar hai'). NEVER use Devanagari script for Hinglish.\n")
            }
        }

        val userPrompt = buildString {
            if (isHinglish) {
                append("Translate the following $lineCount song lyrics lines into conversational HINGLISH (Hindi written with standard English/Latin alphabet letters).\n")
                append("Make it poetic, singable, and easy to read phonetically. Do not use Devanagari script.\n")
            } else when (mode) {
                "Romanized" -> {
                    append("Romanize/transliterate the following $lineCount lines into basic Latin alphabet characters (a-z, A-Z) without special diacritics.\n")
                }
                "Transcribed" -> {
                    append("Transcribe the phonetic sound of the following $lineCount lines into $targetLanguageName script.\n")
                }
                else -> {
                    append("Translate the following $lineCount song lyrics lines into $targetLanguageName.\n")
                    append("Preserve the rhyme, rhythm, and artistic meaning suitable for singing.\n")
                }
            }

            if (!customPrompt.isNullOrBlank()) {
                append("\nUser Custom Instructions:\n$customPrompt\n")
            }

            append("\nOriginal Lyrics are provided as a JSON array of $lineCount separate lines. Translate each array item independently, preserve the exact order, and do not combine lines:\n")
            append(inputText)
            append("\n\nReturn EXACTLY a JSON array of $lineCount non-empty translated strings, one string for each input item.")
        }

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        }

        val jsonBody = JSONObject().apply {
            if (model.isNotBlank()) {
                put("model", model.trim())
            }
            put("messages", messages)
            put("temperature", 0.3)
            put("max_tokens", (lineCount * 80).coerceAtLeast(1024))
        }

        val effectiveBaseUrl = resolveEndpoint(provider, baseUrl)

        var attempt = 0
        while (attempt < maxRetries) {
            try {
                onLog?.invoke("Connecting to $provider (Attempt ${attempt + 1})...")

                val requestBuilder = Request.Builder()
                    .url(effectiveBaseUrl)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://github.com/devxrohan/SMusic")
                    .addHeader("X-Title", "SMusic")

                if (effectiveApiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $effectiveApiKey")
                }

                val request = requestBuilder.post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
                val response = client.newCall(request).execute()
                val bodyText = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    if (response.code >= 500) {
                        attempt++
                        delay(1000L * attempt)
                        continue
                    }
                    val err = runCatching {
                        JSONObject(bodyText).optJSONObject("error")?.optString("message")
                    }.getOrNull() ?: "HTTP ${response.code}: ${response.message}"
                    return@withContext Result.failure(Exception("AI request failed: $err"))
                }

                val parsedLines = parseJsonResponse(bodyText, lineCount)
                if (parsedLines != null) {
                    onLog?.invoke("Successfully translated $lineCount lines")
                    return@withContext Result.success(parsedLines)
                } else {
                    onLog?.invoke("Retrying parsing...")
                }
            } catch (e: Exception) {
                Timber.w(e, "AI translation attempt $attempt failed")
                if (attempt == maxRetries - 1) {
                    return@withContext Result.failure(e)
                }
            }
            attempt++
            delay(1000L * attempt)
        }

        Result.failure(Exception("Failed to obtain translation from AI after $maxRetries attempts."))
    }

    private fun resolveEndpoint(provider: String, customBaseUrl: String): String {
        return when (provider) {
            "OpenRouter" -> "https://openrouter.ai/api/v1/chat/completions"
            "OpenAI" -> "https://api.openai.com/v1/chat/completions"
            "Perplexity" -> "https://api.perplexity.ai/chat/completions"
            "Claude" -> "https://api.anthropic.com/v1/messages"
            "Gemini" -> "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
            "XAi" -> "https://api.x.ai/v1/chat/completions"
            "Mistral" -> "https://api.mistral.ai/v1/chat/completions"
            "Nvidia" -> "https://integrate.api.nvidia.com/v1/chat/completions"
            "OrcaRouter" -> "https://api.orcarouter.com/v1/chat/completions"
            "Groq" -> "https://api.groq.com/openai/v1/chat/completions"
            "Puter" -> "https://api.puter.com/v1/chat/completions"
            "DeepL" -> "https://api.deepl.com/v2/translate"
            else -> customBaseUrl.ifBlank { "https://openrouter.ai/api/v1/chat/completions" }
        }
    }

    private fun parseJsonResponse(responseBody: String, expectedCount: Int): List<String>? {
        return runCatching {
            val root = JSONObject(responseBody)
            val choices = root.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null
            val message = choices.getJSONObject(0).optJSONObject("message") ?: return null
            var content = message.optString("content").trim()

            // Some OpenAI-compatible providers wrap JSON in markdown fences.
            content = content
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            fun parseArray(value: String): List<String>? = runCatching {
                val array = JSONArray(value)
                (0 until array.length()).map { array.optString(it) }
            }.getOrNull()

            var lines = parseArray(content)
            if (lines == null) {
                val start = content.indexOf('[')
                val end = content.lastIndexOf(']')
                if (start >= 0 && end > start) lines = parseArray(content.substring(start, end + 1))
            }

            // Be tolerant when a provider returns one JSON item containing embedded newlines,
            // but never silently pad a paragraph with empty translations for the remaining lines.
            if (lines != null && lines.size == 1 && expectedCount > 1) {
                val embedded = lines.single().replace("\r\n", "\n").split('\n')
                    .map { it.trim() }
                if (embedded.size == expectedCount && embedded.all { it.isNotEmpty() }) lines = embedded
            }

            if (lines == null) {
                val plainLines = content.replace("\r\n", "\n").lines()
                    .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                    .filter { it.isNotBlank() }
                lines = plainLines.takeIf { it.size == expectedCount }
            }

            // All inputs passed here are non-empty lyric lines, so every output item must also
            // contain a translation. Reject the common broken response ["entire paragraph", "", ...].
            lines?.takeIf { it.size == expectedCount && it.all { line -> line.isNotBlank() } }
        }.getOrNull()
    }

    // ── Free, no-key translation (the free, no-key translation approach) ──
    // Google's own web client ("dict-chrome-ex") requires no API key. Lines are
    // batched and separated by private-use marker characters so one request can
    // translate many lyric lines while keeping them in order.
    private const val GOOGLE_FREE_ENDPOINT = "https://translate.googleapis.com/translate_a/single"
    private const val GOOGLE_FREE_MAX_BATCH_CHARS = 3500
    private val googleMarkerRegex = Regex("\uE000[^\uE001]*\uE001")

    private data class GoogleSlot(val index: Int, val text: String)
    private data class GoogleBatch(val slots: List<GoogleSlot>, val payload: String)

    private fun googleMarker(index: Int): String = "\uE000${index.toString().padStart(4, '0')}\uE001"

    private fun buildGoogleBatches(lines: List<String>): List<GoogleBatch> {
        val slots = lines.mapIndexed { index, text -> GoogleSlot(index, text) }
        val result = mutableListOf<GoogleBatch>()
        var current = mutableListOf<GoogleSlot>()
        var length = 0

        fun flush() {
            if (current.isEmpty()) return
            val payload = buildString {
                current.forEachIndexed { i, slot ->
                    if (i > 0) append('\n').append(googleMarker(slot.index)).append('\n')
                    append(slot.text)
                }
            }
            result += GoogleBatch(current.toList(), payload)
            current = mutableListOf()
            length = 0
        }

        slots.forEach { slot ->
            val added = slot.text.length + if (current.isEmpty()) 0 else 8
            if (current.isNotEmpty() && length + added > GOOGLE_FREE_MAX_BATCH_CHARS) flush()
            current += slot
            length += added
        }
        flush()
        return result
    }

    /** segmentIndex 0 = translated text (dt=t), 3 = romanized text (dt=rm). */
    private fun requestGoogleBatch(batch: GoogleBatch, target: String, dt: String, segmentIndex: Int): List<String>? {
        val body = FormBody.Builder()
            .add("client", "dict-chrome-ex")
            .add("sl", "auto")
            .add("tl", target)
            .add("dt", dt)
            .add("q", batch.payload)
            .build()
        val request = Request.Builder()
            .url(GOOGLE_FREE_ENDPOINT)
            .header("User-Agent", "SMusic/1.0")
            .header("Accept", "application/json")
            .post(body)
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            Timber.w(e, "Free Google Translate request failed")
            return null
        }
        val responseBody = response.use {
            if (!it.isSuccessful) return null
            it.body?.string()
        } ?: return null

        return runCatching {
            val root = JSONArray(responseBody)
            val segments = root.getJSONArray(0)
            val joined = buildString {
                for (i in 0 until segments.length()) {
                    val segment = segments.optJSONArray(i) ?: continue
                    append(segment.optString(segmentIndex, ""))
                }
            }
            val parts = joined.split(googleMarkerRegex).map { it.trim() }
            parts.takeIf { it.size == batch.slots.size }
        }.getOrNull()
    }

    private fun translateWithGoogleFree(
        lines: List<String>,
        targetLangCode: String,
        onLog: ((String) -> Unit)?
    ): Result<List<String>> {
        return runCatching {
            onLog?.invoke("Contacting free Google Translate...")

            // "xx-Latn" is this app's own code for "write it phonetically in the
            // Latin alphabet" (Hinglish and similar). Google's free endpoint has a
            // dedicated transliteration mode (dt=rm) for exactly that, rather than
            // translating the meaning first.
            val isLatinTarget = targetLangCode.endsWith("-Latn", ignoreCase = true)
            val target = targetLangCode.substringBefore("-Latn").ifBlank { "en" }
            val dt = if (isLatinTarget) "rm" else "t"
            val segmentIndex = if (isLatinTarget) 3 else 0

            val batches = buildGoogleBatches(lines)
            val resolved = arrayOfNulls<String>(lines.size)
            for (batch in batches) {
                val answer = requestGoogleBatch(batch, target, dt, segmentIndex)
                    ?: error("Free Google Translate did not return a usable response")
                batch.slots.forEachIndexed { i, slot ->
                    resolved[slot.index] = answer.getOrNull(i)?.takeIf { it.isNotBlank() }
                }
            }

            val output = lines.indices.map { idx -> resolved[idx] ?: lines[idx] }
            if (output.none { it.isNotBlank() }) {
                error("Free Google Translate returned no translated text")
            }
            onLog?.invoke("Successfully translated ${lines.size} lines")
            output
        }.onFailure { e -> Timber.w(e, "Free Google Translate failed") }
    }

    private fun translateWithDeepL(
        lines: List<String>,
        targetLangCode: String,
        apiKey: String,
        onLog: ((String) -> Unit)?
    ): Result<List<String>> {
        return runCatching {
            onLog?.invoke("Contacting DeepL API...")
            val isFree = apiKey.endsWith(":fx")
            val endpoint = if (isFree) "https://api-free.deepl.com/v2/translate" else "https://api.deepl.com/v2/translate"

            val lang = targetLangCode.uppercase().take(2)
            val payload = JSONObject().apply {
                put("text", JSONArray(lines))
                put("target_lang", lang)
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "DeepL-Auth-Key $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("DeepL error ${response.code}: $text")
            }

            val root = JSONObject(text)
            val translations = root.getJSONArray("translations")
            val result = (0 until translations.length()).map {
                translations.getJSONObject(it).getString("text")
            }
            result
        }
    }
}
