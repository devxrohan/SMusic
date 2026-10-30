package com.devxrohan.smusic.lyrics

import android.content.Context
import com.devxrohan.smusic.ai.AiTranslationService
import com.devxrohan.smusic.constants.AiTranslationLanguages
import com.devxrohan.smusic.db.entities.LyricsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import timber.log.Timber
import java.io.File

object LyricsTranslationHelper {
    sealed class TranslationStatus {
        object Idle : TranslationStatus()
        data class Translating(val logs: List<String> = emptyList()) : TranslationStatus()
        object Success : TranslationStatus()
        data class Error(val message: String) : TranslationStatus()
    }

    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status.asStateFlow()

    private val _hasActiveTranslations = MutableStateFlow(false)
    val hasActiveTranslations: StateFlow<Boolean> = _hasActiveTranslations.asStateFlow()

    private val _activeSongId = MutableStateFlow<String?>(null)
    val activeSongId: StateFlow<String?> = _activeSongId.asStateFlow()

    private val _currentLanguageCode = MutableStateFlow("hi-Latn")
    val currentLanguageCode: StateFlow<String> = _currentLanguageCode.asStateFlow()

    private val _translationVersion = MutableStateFlow(0)
    val translationVersion: StateFlow<Int> = _translationVersion.asStateFlow()

    private var translationJob: Job? = null
    private val memoryCache = mutableMapOf<String, List<String>>()

    /**
     * "auto" translates between Hindi and English: Hindi (Devanagari or romanised) lyrics become English,
     * English lyrics become Hindi, and any other language goes to English. Other codes are used as picked.
     */
    fun resolveTargetLanguage(code: String, lines: List<String>): String =
        if (code == "auto") LyricsLanguageDetector.autoTarget(lines) else code

    fun getCacheKey(lyricsText: String, lang: String): String =
        "lyrics-v2_${lyricsText.hashCode()}_$lang"

    fun getCacheFile(context: Context, songId: String, lang: String): File {
        val dir = File(context.filesDir, "lyrics_translations")
        if (!dir.exists()) dir.mkdirs()
        val safeSongId = songId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(dir, "${safeSongId}_${lang}_v2.json")
    }

    fun hasCachedTranslation(context: Context, songId: String, lang: String): Boolean {
        if (songId.isBlank()) return false
        val file = getCacheFile(context, songId, lang)
        return file.exists() && file.length() > 2
    }

    fun parseLyricsToEntries(lyrics: String?): List<LyricsEntry> {
        if (lyrics.isNullOrBlank() || lyrics == LyricsEntity.LYRICS_NOT_FOUND) return emptyList()
        return when {
            SMusicLyricsUtils.isRecognizedTimedLyrics(lyrics) -> SMusicLyricsUtils.parseAnyLyrics(lyrics)
            else -> lyrics.lines().filter { it.isNotBlank() }.mapIndexed { index, line ->
                LyricsEntry(time = -1L, text = line.trim())
            }
        }
    }

    private var activeLyricsList: List<LyricsEntry>? = null

    fun registerLyrics(lyrics: List<LyricsEntry>) {
        activeLyricsList = lyrics
    }

    fun unregisterLyrics(lyrics: List<LyricsEntry>) {
        if (activeLyricsList === lyrics) {
            activeLyricsList = null
        }
    }

    fun isTranslating(): Boolean = _status.value is TranslationStatus.Translating

    fun cancelTranslation() {
        translationJob?.cancel()
        translationJob = null
        _status.value = TranslationStatus.Idle
    }

    fun resetStatus() {
        _status.value = TranslationStatus.Idle
    }

    fun onSongChanged(newSongId: String) {
        if (_activeSongId.value != newSongId) {
            cancelTranslation()
            _activeSongId.value = newSongId
            _hasActiveTranslations.value = false
            _status.value = TranslationStatus.Idle
            activeLyricsList?.forEach { it.translatedTextFlow.value = null }
            _translationVersion.value += 1
        }
    }

    fun clearTranslations(lyrics: List<LyricsEntry>? = null, context: Context? = null, songId: String? = null) {
        cancelTranslation()
        val targetLyrics = lyrics ?: activeLyricsList
        targetLyrics?.forEach { it.translatedTextFlow.value = null }
        _activeSongId.value = null
        _hasActiveTranslations.value = false
        _status.value = TranslationStatus.Idle

        if (context != null && !songId.isNullOrBlank()) {
            runCatching {
                val dir = File(context.filesDir, "lyrics_translations")
                val safeSongId = songId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                dir.listFiles { _, name -> name.startsWith("${safeSongId}_") }?.forEach { it.delete() }
            }
        }
        memoryCache.clear()
        _translationVersion.value += 1
    }

    fun loadTranslationsFromCache(
        lyrics: List<LyricsEntry>,
        context: Context,
        songId: String,
        targetLanguageCode: String
    ): Boolean {
        val requestedLanguageCode = targetLanguageCode
        registerLyrics(lyrics)
        val nonEmptyEntries = lyrics.mapIndexedNotNull { index, entry ->
            if (entry.text.isNotBlank()) index to entry else null
        }
        if (nonEmptyEntries.isEmpty()) {
            _hasActiveTranslations.value = false
            return false
        }
        val targetLanguageCode = resolveTargetLanguage(requestedLanguageCode, nonEmptyEntries.map { it.second.text })
        _currentLanguageCode.value = targetLanguageCode

        val fullText = nonEmptyEntries.joinToString("\n") { it.second.text }
        val memKey = getCacheKey(fullText, targetLanguageCode)

        // 1. Check memory cache
        val cachedMem = memoryCache[memKey]
        if (cachedMem != null && cachedMem.size == nonEmptyEntries.size && cachedMem.all { it.isNotBlank() }) {
            nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                lyrics[originalIndex].translatedTextFlow.value = cachedMem.getOrNull(idx)
            }
            _activeSongId.value = songId
            _hasActiveTranslations.value = true
            _translationVersion.value += 1
            return true
        }

        // 2. Check disk cache
        if (songId.isNotBlank()) {
            val file = getCacheFile(context, songId, targetLanguageCode)
            if (file.exists()) {
                val list = runCatching {
                    val jsonArray = JSONArray(file.readText())
                    (0 until jsonArray.length()).map { jsonArray.optString(it) }
                }.getOrNull()

                if (list != null && list.size == nonEmptyEntries.size && list.all { it.isNotBlank() }) {
                    memoryCache[memKey] = list
                    nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                        lyrics[originalIndex].translatedTextFlow.value = list.getOrNull(idx)
                    }
                    _activeSongId.value = songId
                    _hasActiveTranslations.value = true
                    _translationVersion.value += 1
                    return true
                }
            }
        }

        _hasActiveTranslations.value = false
        return false
    }

    fun translateLyrics(
        lyrics: List<LyricsEntry>? = null,
        rawLyrics: String? = null,
        targetLanguageCode: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String = "Literal",
        customPrompt: String? = null,
        provider: String = "OpenRouter",
        context: Context,
        songId: String = "",
        scope: CoroutineScope
    ) {
        cancelTranslation()
        val requestedLanguageCode = targetLanguageCode
        _status.value = TranslationStatus.Translating(listOf("Initializing AI translation engine..."))

        val targetEntries = if (!lyrics.isNullOrEmpty()) {
            lyrics
        } else if (!activeLyricsList.isNullOrEmpty()) {
            activeLyricsList!!
        } else if (!rawLyrics.isNullOrBlank()) {
            parseLyricsToEntries(rawLyrics)
        } else {
            emptyList()
        }
        registerLyrics(targetEntries)

        val nonEmptyEntries = targetEntries.mapIndexedNotNull { index, entry ->
            if (entry.text.isNotBlank()) index to entry else null
        }

        if (nonEmptyEntries.isEmpty()) {
            _status.value = TranslationStatus.Error("No lyrics available to translate.")
            return
        }

        val linesToTranslate = nonEmptyEntries.map { it.second.text }
        val targetLanguageCode = resolveTargetLanguage(requestedLanguageCode, linesToTranslate)
        _currentLanguageCode.value = targetLanguageCode
        val targetLanguageName = AiTranslationLanguages[targetLanguageCode] ?: targetLanguageCode
        if (requestedLanguageCode == "auto") {
            _status.value = TranslationStatus.Translating(listOf("Auto: translating to $targetLanguageName..."))
        }

        translationJob = scope.launch(Dispatchers.IO) {
            val result = AiTranslationService.translate(
                lines = linesToTranslate,
                targetLanguageCode = targetLanguageCode,
                targetLanguageName = targetLanguageName,
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                mode = mode,
                customPrompt = customPrompt,
                provider = provider,
                onLog = { logMsg ->
                    val cur = _status.value
                    if (cur is TranslationStatus.Translating) {
                        _status.value = cur.copy(logs = cur.logs + logMsg)
                    }
                }
            )

            withContext(Dispatchers.Main) {
                result.onSuccess { translatedLines ->
                    nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                        if (idx < translatedLines.size && originalIndex < targetEntries.size) {
                            targetEntries[originalIndex].translatedTextFlow.value = translatedLines[idx]
                        }
                    }

                    // Save to memory cache
                    val fullText = linesToTranslate.joinToString("\n")
                    val memKey = getCacheKey(fullText, targetLanguageCode)
                    memoryCache[memKey] = translatedLines

                    // Save to disk cache
                    if (songId.isNotBlank()) {
                        runCatching {
                            val file = getCacheFile(context, songId, targetLanguageCode)
                            val jsonArray = JSONArray()
                            translatedLines.forEach { jsonArray.put(it) }
                            file.writeText(jsonArray.toString())
                        }
                    }

                    _activeSongId.value = songId
                    _hasActiveTranslations.value = true
                    _status.value = TranslationStatus.Success
                    _translationVersion.value += 1
                }.onFailure { error ->
                    Timber.e(error, "Lyrics translation failed")
                    _hasActiveTranslations.value = false
                    _status.value = TranslationStatus.Error(error.message ?: "Translation error occurred.")
                }
            }
        }
    }
}


/** Tiny offline language sniffer for the "Auto (Hindi / English)" translation mode. */
object LyricsLanguageDetector {
    enum class Lang { HINDI, ENGLISH, OTHER }

    private val hinglish = setOf(
        "hai", "hain", "nahi", "nahin", "tum", "tu", "tera", "teri", "tere", "mera", "meri", "mere", "dil", "pyaar", "pyar",
        "ishq", "mohabbat", "ke", "ki", "ka", "ko", "se", "mein", "main", "mai", "aur", "kya", "kyun", "kyu", "yeh", "ye",
        "woh", "wo", "jaan", "zindagi", "sanam", "sajna", "bas", "jo", "tak", "bhi", "hum", "humein", "tujhe", "mujhe",
        "kaise", "kabhi", "abhi", "raat", "din", "aankhen", "nazar", "yaad", "saath", "phir", "mat", "chal", "aaja", "jaa",
        "khuda", "rab", "tujh", "mujh", "humko", "tumhe", "tumhari", "meinu", "ho", "hoon", "tha", "thi", "kar", "karna",
        "dekho", "sun", "suno", "baby", "dilbar", "yaar", "jahan", "waqt", "pal", "lamha", "sapne", "sach", "jhoot"
    )
    private val english = setOf(
        "the", "you", "and", "i", "to", "my", "me", "in", "it", "is", "of", "on", "that", "your", "love", "baby", "we",
        "this", "with", "for", "be", "are", "was", "all", "can", "but", "not", "don't", "just", "like", "so", "oh", "when",
        "what", "know", "got", "let", "go", "up", "down", "now", "way", "feel", "never", "say", "tell", "night", "heart"
    )

    fun detect(lines: List<String>): Lang {
        val text = lines.joinToString(" ").take(4000)
        var devanagari = 0; var latin = 0; var otherLetters = 0
        for (c in text) {
            when {
                c in '\u0900'..'\u097F' -> devanagari++
                c in 'a'..'z' || c in 'A'..'Z' -> latin++
                c.isLetter() -> otherLetters++
            }
        }
        val letters = devanagari + latin + otherLetters
        if (letters == 0) return Lang.OTHER
        if (devanagari.toDouble() / letters >= 0.3) return Lang.HINDI
        if (latin.toDouble() / letters < 0.6) return Lang.OTHER
        val tokens = text.lowercase().split(Regex("[^a-z']+")).filter { it.isNotBlank() }
        if (tokens.size < 4) return Lang.OTHER
        val hi = tokens.count { it in hinglish }
        val en = tokens.count { it in english }
        return when {
            hi >= 4 && hi.toDouble() / tokens.size >= 0.12 && hi >= en -> Lang.HINDI
            en.toDouble() / tokens.size >= 0.15 -> Lang.ENGLISH
            else -> Lang.OTHER
        }
    }

    /** Hindi -> English, English -> Hindi, everything else -> English. */
    fun autoTarget(lines: List<String>): String = when (detect(lines)) {
        Lang.HINDI -> "en"
        Lang.ENGLISH -> "hi"
        Lang.OTHER -> "en"
    }
}
