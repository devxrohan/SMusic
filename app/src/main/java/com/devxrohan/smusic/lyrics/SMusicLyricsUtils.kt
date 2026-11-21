/*
 * SMusic Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.devxrohan.smusic.lyrics

import android.text.format.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.devxrohan.smusic.betterlyrics.TTMLParser
import org.json.JSONArray
import org.json.JSONObject


data class LyricsRomanizationPreferences(
    val romanizeJapanese: Boolean,
    val romanizeKorean: Boolean,

    ) {
    val isEnabled: Boolean
        get() = romanizeJapanese || romanizeKorean
}

@Suppress("RegExpRedundantEscape")
object SMusicLyricsUtils {
    val LINE_REGEX = "((\\[\\d\\d:\\d\\d\\.\\d{2,3}\\] ?)+)(.+)".toRegex()
    val TIME_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\]".toRegex()

    // Parse enhanced-LRC word timestamps so timing tags never appear in rendered lyrics.
    private val WORD_TAG_REGEX = "<(\\d{1,3}):(\\d{2})(?:[.,](\\d{1,3}))?>".toRegex()
    private val ENHANCED_LINE_REGEX = Regex("^\\[(\\d{1,3}):(\\d{2})[.:](\\d{2,3})](.*)$")
    private val ENHANCED_WORD_REGEX = Regex("<(\\d{1,3}):(\\d{2})[.:](\\d{2,3})>([^<]*)")

    private fun wordTagToMillis(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
        val fraction = match.groupValues[3]
        val millis = when (fraction.length) {
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            3 -> fraction.toLong()
            else -> 0L
        }
        return minutes * 60_000L + seconds * 1_000L + millis
    }

    /** True when a (already line-timestamp-stripped) lyric line still carries inline word tags. */
    fun hasInlineWordTags(text: String): Boolean = WORD_TAG_REGEX.containsMatchIn(text)

    /** Parses enhanced-LRC display text and optional per-word timestamps. */
    fun stripInlineWordTags(text: String, lineStartMs: Long): Pair<String, List<WordTimestamp>> {
        if (!hasInlineWordTags(text)) return text to emptyList()

        val matches = WORD_TAG_REGEX.findAll(text).toList()
        val builder = StringBuilder()
        val words = mutableListOf<WordTimestamp>()
        var cursor = 0
        var pendingStart: Long? = null
        var pendingWordStart = -1

        for (match in matches) {
            // Plain text between the previous tag and this one belongs to the
            // word that started at pendingStart.
            val segment = text.substring(cursor, match.range.first)
            if (pendingStart != null && segment.isNotEmpty()) {
                val trimmed = segment.trim()
                if (trimmed.isNotEmpty()) {
                    words += WordTimestamp(
                        text = trimmed,
                        startTime = pendingStart / 1000.0,
                        endTime = wordTagToMillis(match) / 1000.0,
                    )
                }
            }
            builder.append(segment)
            pendingStart = wordTagToMillis(match)
            pendingWordStart = builder.length
            cursor = match.range.last + 1
        }
        val tail = text.substring(cursor)
        builder.append(tail)
        if (pendingStart != null && tail.trim().isNotEmpty()) {
            words += WordTimestamp(
                text = tail.trim(),
                startTime = pendingStart / 1000.0,
                endTime = (pendingStart + tail.trim().length * 90L).coerceAtLeast(pendingStart + 1L) / 1000.0,
            )
        }

        val cleanText = builder.toString().trim().replace(Regex("\\s+"), " ")
        return cleanText to words
    }

    private val KANA_ROMAJI_MAP: Map<String, String> = mapOf(
        // Digraphs (Yōon - combinations like kya, sho)
        "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo",
        "シャ" to "sha", "シュ" to "shu", "ショ" to "sho",
        "チャ" to "cha", "チュ" to "chu", "チョ" to "cho",
        "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
        "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
        "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
        "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
        "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
        "ジャ" to "ja", "ジュ" to "ju", "ジョ" to "jo",
        "ヂャ" to "ja", "ヂュ" to "ju", "ヂョ" to "jo", // ヂ variants, also commonly 'ja', 'ju', 'jo'
        "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
        "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo",

        // Basic Katakana Characters
        "ア" to "a", "イ" to "i", "ウ" to "u", "エ" to "e", "オ" to "o",
        "カ" to "ka", "キ" to "ki", "ク" to "ku", "ケ" to "ke", "コ" to "ko",
        "サ" to "sa", "シ" to "shi", "ス" to "su", "セ" to "se", "ソ" to "so",
        "タ" to "ta", "チ" to "chi", "ツ" to "tsu", "テ" to "te", "ト" to "to",
        "ナ" to "na", "ニ" to "ni", "ヌ" to "nu", "ネ" to "ne", "ノ" to "no",
        "ハ" to "ha", "ヒ" to "hi", "フ" to "fu", "ヘ" to "he", "ホ" to "ho",
        "マ" to "ma", "ミ" to "mi", "ム" to "mu", "メ" to "me", "モ" to "mo",
        "ヤ" to "ya", "ユ" to "yu", "ヨ" to "yo",
        "ラ" to "ra", "リ" to "ri", "ル" to "ru", "レ" to "re", "ロ" to "ro",
        "ワ" to "wa", "ヲ" to "o", // ヲ is pronounced 'o'
        "ン" to "n",

        // Dakuten (voiced consonants)
        "ガ" to "ga", "ギ" to "gi", "グ" to "gu", "ゲ" to "ge", "ゴ" to "go",
        "ザ" to "za", "ジ" to "ji", "ズ" to "zu", "ゼ" to "ze", "ゾ" to "zo",
        "ダ" to "da", "ヂ" to "ji", "ヅ" to "zu", "デ" to "de", "ド" to "do", // ヂ and ヅ are often 'ji' and 'zu'

        // Handakuten (p-sounds for 'h' group) / Dakuten for 'h' group
        "バ" to "ba", "ビ" to "bi", "ブ" to "bu", "ベ" to "be", "ボ" to "bo", // Dakuten for ハ행 (ha-row)
        "パ" to "pa", "ピ" to "pi", "プ" to "pu", "ペ" to "pe", "ポ" to "po", // Handakuten for ハ행 (ha-row)

        // Chōonpu (long vowel mark) - removed as per original logic
        "ー" to ""
    )

    private val HANGUL_ROMAJA_MAP: Map<String, Map<String, String>> = mapOf(
        "cho" to mapOf(
            "ᄀ" to "g",  "ᄁ" to "kk", "ᄂ" to "n",  "ᄃ" to "d", 
            "ᄄ" to "tt", "ᄅ" to "r",  "ᄆ" to "m",  "ᄇ" to "b",
            "ᄈ" to "pp", "ᄉ" to "s",  "ᄊ" to "ss", "ᄋ" to "",
            "ᄌ" to "j",  "ᄍ" to "jj", "ᄎ" to "ch", "ᄏ" to "k",
            "ᄐ" to "t",  "ᄑ" to "p",  "ᄒ" to "h"
        ),
        "jung" to mapOf(
            "ᅡ" to "a",  "ᅢ" to "ae", "ᅣ" to "ya",  "ᅤ" to "yae", 
            "ᅥ" to "eo", "ᅦ" to "e",  "ᅧ" to "yeo", "ᅨ" to "ye", 
            "ᅩ" to "o",  "ᅪ" to "wa", "ᅫ" to "wae", "ᅬ" to "oe",
            "ᅭ" to "yo", "ᅮ" to "u",  "ᅯ" to "wo",  "ᅰ" to "we",
            "ᅱ" to "wi", "ᅲ" to "yu", "ᅳ" to "eu",  "ᅴ" to "eui",
            "ᅵ" to "i"
        ),
        "jong" to mapOf(
            "ᆨ" to "k",     "ᆨᄋ" to "g",   "ᆨᄂ" to "ngn", "ᆨᄅ" to "ngn", "ᆨᄆ" to "ngm", "ᆨᄒ" to "kh",
            "ᆩ" to "kk",    "ᆩᄋ" to "kg",  "ᆩᄂ" to "ngn", "ᆩᄅ" to "ngn", "ᆩᄆ" to "ngm", "ᆩᄒ" to "kh",
            "ᆪ" to "k",     "ᆪᄋ" to "ks",  "ᆪᄂ" to "ngn", "ᆪᄅ" to "ngn", "ᆪᄆ" to "ngm", "ᆪᄒ" to "kch",
            "ᆫ" to "n",     "ᆫᄅ" to "ll",  "ᆬ" to "n",     "ᆬᄋ" to "nj",  "ᆬᄂ" to "nn",  "ᆬᄅ" to "nn",
            "ᆬᄆ" to "nm",  "ᆬㅎ" to "nch", "ᆭ" to "n",     "ᆭᄋ" to "nh",  "ᆭᄅ" to "nn",  "ᆮ" to "t",
            "ᆮᄋ" to "d",   "ᆮᄂ" to "nn",  "ᆮᄅ" to "nn",  "ᆮᄆ" to "nm",  "ᆮᄒ" to "th",  "ᆯ" to "l",
            "ᆯᄋ" to "r",   "ᆯᄂ" to "ll",  "ᆯᄅ" to "ll",  "ᆰ" to "k",     "ᆰᄋ" to "lg",  "ᆰᄂ" to "ngn",
            "ᆰᄅ" to "ngn", "ᆰᄆ" to "ngm", "ᆰᄒ" to "lkh", "ᆱ" to "m",     "ᆱᄋ" to "lm",  "ᆱᄂ" to "mn",
            "ᆱᄅ" to "mn",  "ᆱᄆ" to "mm",  "ᆱᄒ" to "lmh", "ᆲ" to "p",     "ᆲᄋ" to "lb",  "ᆲᄂ" to "mn",
            "ᆲᄅ" to "mn",  "ᆲᄆ" to "mm",  "ᆲᄒ" to "lph", "ᆳ" to "t",     "ᆳᄋ" to "ls",  "ᆳᄂ" to "nn",
            "ᆳᄅ" to "nn",  "ᆳᄆ" to "nm",  "ᆳᄒ" to "lsh", "ᆴ" to "t",     "ᆴᄋ" to "lt",  "ᆴᄂ" to "nn",
            "ᆴᄅ" to "nn",  "ᆴᄆ" to "nm",  "ᆴᄒ" to "lth", "ᆵ" to "p",     "ᆵᄋ" to "lp",  "ᆵᄂ" to "mn",
            "ᆵᄅ" to "mn",  "ᆵᄆ" to "mm",  "ᆵᄒ" to "lph", "ᆶ" to "l",     "ᆶᄋ" to "lh",  "ᆶᄂ" to "ll",
            "ᆶᄅ" to "ll",  "ᆶᄆ" to "lm",  "ᆶᄒ" to "lh",  "ᆷ" to "m",     "ᆷᄅ" to "mn",  "ᆸ" to "p",
            "ᆸᄋ" to "b",   "ᆸᄂ" to "mn",  "ᆸᄅ" to "mn",  "ᆸᄆ" to "mm",  "ᆸᄒ" to "ph",  "ᆹ" to "p",
            "ᆹᄋ" to "ps",  "ᆹᄂ" to "mn",  "ᆹᄅ" to "mn",  "ᆹᄆ" to "mm",  "ᆹᄒ" to "psh", "ᆺ" to "t",
            "ᆺᄋ" to "s",   "ᆺᄂ" to "nn",  "ᆺᄅ" to "nn",  "ᆺᄆ" to "nm",  "ᆺᄒ" to "sh",  "ᆻ" to "t",
            "ᆻᄋ" to "ss",  "ᆻᄂ" to "tn",  "ᆻᄅ" to "tn",  "ᆻᄆ" to "nm",  "ᆻᄒ" to "th",  "ᆼ" to "ng",
            "ᆽ" to "t",     "ᆽᄋ" to "j",   "ᆽᄂ" to "nn",  "ᆽᄅ" to "nn",  "ᆽᄆ" to "nm",  "ᆽᄒ" to "ch",
            "ᆾ" to "t",     "ᆾᄋ" to "ch",  "ᆾᄂ" to "nn",  "ᆾᄅ" to "nn",  "ᆾᄆ" to "nm",  "ᆾᄒ" to "ch",
            "ᆿ" to "k",     "ᆿᄋ" to "k",   "ᆿᄂ" to "ngn", "ᆿᄅ" to "ngn", "ᆿᄆ" to "ngm", "ᆿᄒ" to "kh",
            "ᇀ" to "t",     "ᇀᄋ" to "t",   "ᇀᄂ" to "nn",  "ᇀᄅ" to "nn",  "ᇀᄆ" to "nm",  "ᇀᄒ" to "th",
            "ᇁ" to "p",     "ᇁᄋ" to "p",   "ᇁᄂ" to "mn",  "ᇁᄅ" to "mn",  "ᇁᄆ" to "mm",  "ᇁᄒ" to "ph",
            "ᇂ" to "t",     "ᇂᄋ" to "h",   "ᇂᄂ" to "nn",  "ᇂᄅ" to "nn",  "ᇂᄆ" to "mm",  "ᇂᄒ" to "t",
            "ᇂᄀ" to "k",
        )
    )

    private fun normalizeTtmlSource(lyrics: String): String = lyrics.trim()
        .removePrefix("\uFEFF")
        .replace("\\u003C", "<", ignoreCase = true)
        .replace("\\u003E", ">", ignoreCase = true)
        .replace("\\<", "<")
        .replace("\\>", ">")
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&apos;", "'", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)

    fun isTtml(lyrics: String): Boolean {
        val normalized = normalizeTtmlSource(lyrics)
        return normalized.contains("<tt", ignoreCase = true) ||
            normalized.contains("http://www.w3.org/ns/ttml", ignoreCase = true) ||
            normalized.contains("<p", ignoreCase = true) && normalized.contains("<span", ignoreCase = true) &&
                normalized.contains("begin=", ignoreCase = true)
    }

    fun parseTtml(lyrics: String, durationSeconds: Int? = null): List<LyricsEntry> {
        val normalized = normalizeTtmlSource(lyrics)
        val parsedLines = TTMLParser.parseTTML(normalized)
        if (parsedLines.isEmpty()) {
            // Providers occasionally return escaped or partially untimed TTML. Never
            // let the raw XML leak into the lyrics UI: salvage each paragraph as text.
            val paragraphRegex = Regex("""(?is)<p\b([^>]*)>(.*?)</p>""")
            val fallback = paragraphRegex.findAll(normalized).mapNotNull { match ->
                val attrs = match.groupValues[1]
                val inner = match.groupValues[2]
                val text = inner.replace(Regex("""(?is)<br\s*/?>"""), " ")
                    .replace(Regex("""(?is)<[^>]+>"""), "")
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&quot;", "\"").replace("&apos;", "'")
                    .replace(Regex("""\s+"""), " ").trim()
                if (text.isBlank()) return@mapNotNull null
                val beginValue = Regex("""(?:begin|start)\s*=\s*['\"]([^'\"]+)['\"]""", RegexOption.IGNORE_CASE)
                    .find(attrs)?.groupValues?.get(1)
                val begin = beginValue?.let { value ->
                    parseClockTimestamp(value) ?: value.toDoubleOrNull()?.let { (it * 1000).toLong() }
                }
                LyricsEntry(begin ?: -1L, text)
            }.toList()
            if (fallback.isNotEmpty()) return fallback.sortedBy { if (it.time < 0) Long.MAX_VALUE else it.time }
            // Last-resort salvage for malformed XML with paragraph tags removed.
            val plain = normalized.replace(Regex("""(?is)<head\b.*?</head>"""), " ")
                .replace(Regex("""(?is)<[^>]+>"""), " ")
                .replace(Regex("""\s+"""), " ").trim()
            return if (plain.isBlank()) emptyList() else listOf(LyricsEntry(-1L, plain))
        }
        val scale = 1.0

        return parsedLines.map { line ->
            val words =
                line.words
                    .filter { it.text.isNotEmpty() }
                    .map { word ->
                        WordTimestamp(
                            text = word.text,
                            startTime = word.startTime * scale,
                            endTime = word.endTime * scale,
                            isBackground = word.isBackground,
                            syllables = word.syllables.map { part ->
                                SyllableTimestamp(
                                    startTime = part.startTime,
                                    endTime = part.endTime,
                                    startChar = part.startChar,
                                    endChar = part.endChar,
                                )
                            },
                        )
                    }.takeIf { it.isNotEmpty() }

            val startMs = (line.startTime * scale * 1000.0).toLong()
            val endMs = (line.endTime * scale * 1000.0).toLong()
            LyricsEntry(
                time = startMs,
                text = line.text,
                words = words,
                agent = line.agent,
                sungUntilMs = endMs.takeIf { it > startMs },
            )
        }.sorted().withInstrumentalGapMarkers()
    }

    /** Insert silence from explicit vocal-end metadata, never from a large inter-line gap alone. */
    private fun List<LyricsEntry>.withInstrumentalGapMarkers(): List<LyricsEntry> {
        if (size < 2) return this
        val result = ArrayList<LyricsEntry>(size + 4)
        forEachIndexed { index, line ->
            result += line
            val next = getOrNull(index + 1) ?: return@forEachIndexed
            val vocalEnd = line.sungUntilMs
                ?: line.words?.lastOrNull()?.endTime?.let { (it * 1000.0).toLong() }
                ?: return@forEachIndexed
            if (vocalEnd > line.time && next.time - vocalEnd >= 4_000L) {
                result += LyricsEntry(
                    time = vocalEnd,
                    text = "",
                    sungUntilMs = vocalEnd,
                )
            }
        }
        return result.sorted()
    }

    /** Detects common structured timed lyric JSON responses (array or wrapped object). */
    fun isStructuredTimedLyricsJson(lyrics: String): Boolean {
        val trimmed = lyrics.trimStart().removePrefix("\uFEFF")
        if (!(trimmed.startsWith("[") || trimmed.startsWith("{"))) return false
        return trimmed.contains("\"time\"", ignoreCase = true) ||
            trimmed.contains("\"timestamp\"", ignoreCase = true) ||
            trimmed.contains("\"startTime\"", ignoreCase = true) ||
            trimmed.contains("\"start_time\"", ignoreCase = true) ||
            trimmed.contains("\"start\"", ignoreCase = true) ||
            trimmed.contains("\"syllabus\"", ignoreCase = true)
    }

    /** Parse line-timed JSON wrappers with duration, syllable and word-level timestamp variants. */
    fun parseStructuredTimedLyricsJson(lyrics: String): List<LyricsEntry> {
        if (!isStructuredTimedLyricsJson(lyrics)) return emptyList()
        return runCatching {
            val raw = lyrics.trim().removePrefix("\uFEFF")
            val rootArray = when {
                raw.startsWith("[") -> JSONArray(raw)
                else -> {
                    val obj = JSONObject(raw)
                    val keys = sequenceOf("lyrics", "lines", "content", "data", "results", "subtitles", "items")
                    val directArray = keys.mapNotNull { key -> obj.optJSONArray(key) }.firstOrNull()
                    val nestedObjectArray = keys.mapNotNull { key -> obj.optJSONObject(key) }
                        .asSequence()
                        .mapNotNull { nested ->
                            sequenceOf("lyrics", "lines", "content", "data", "results", "items")
                                .mapNotNull { key -> nested.optJSONArray(key) }.firstOrNull()
                        }.firstOrNull()
                    val nestedJsonString = keys.mapNotNull { key -> obj.optString(key, null) }
                        .firstOrNull { it.trimStart().startsWith("[") }
                    when {
                        directArray != null -> directArray
                        nestedObjectArray != null -> nestedObjectArray
                        nestedJsonString != null -> JSONArray(nestedJsonString)
                        obj.has("text") || obj.has("content") -> JSONArray().apply { put(obj) }
                        else -> JSONArray()
                    }
                }
            }
            buildList {
                for (i in 0 until rootArray.length()) {
                    val line = rootArray.optJSONObject(i) ?: continue
                    val text = line.optString("text", line.optString("content", line.optString("line", "")))
                        .replace(Regex("<[^>]+>"), "").trim()
                    if (text.isEmpty()) continue
                    val timeValue = when {
                        line.has("time") -> line.opt("time")
                        line.has("timestamp") -> line.opt("timestamp")
                        line.has("startTime") -> line.opt("startTime")
                        line.has("start_time") -> line.opt("start_time")
                        line.has("start") -> line.opt("start")
                        else -> null
                    }
                    val timeMs = parseJsonTimeToMillis(timeValue) ?: continue
                    val wordArray = sequenceOf("syllabus", "words", "wordTimings", "word_timestamps", "tokens")
                        .mapNotNull { key -> line.optJSONArray(key) }.firstOrNull()
                    val words = mutableListOf<WordTimestamp>()
                    if (wordArray != null) {
                        for (j in 0 until wordArray.length()) {
                            val word = wordArray.optJSONObject(j) ?: continue
                            val wordText = word.optString("text", word.optString("word", ""))
                            if (wordText.isEmpty()) continue
                            val wordStartValue = when {
                                word.has("time") -> word.opt("time")
                                word.has("startTime") -> word.opt("startTime")
                                word.has("start") -> word.opt("start")
                                else -> null
                            }
                            val wordStartMs = parseJsonTimeToMillis(wordStartValue) ?: continue
                            val durationValue = when {
                                word.has("duration") -> word.opt("duration")
                                word.has("endTime") -> null
                                word.has("end") -> null
                                else -> null
                            }
                            val explicitEnd = when {
                                word.has("endTime") -> parseJsonTimeToMillis(word.opt("endTime"))
                                word.has("end") -> parseJsonTimeToMillis(word.opt("end"))
                                else -> null
                            }
                            val wordDuration = parseJsonTimeToMillis(durationValue) ?: 0L
                            val wordEndMs = (explicitEnd ?: (wordStartMs + wordDuration)).coerceAtLeast(wordStartMs)
                            words += WordTimestamp(wordText, wordStartMs / 1000.0, wordEndMs / 1000.0,
                                isBackground = word.optBoolean("isBackground", false))
                        }
                    }
                    add(LyricsEntry(time = timeMs, text = text, words = words.takeIf { it.isNotEmpty() },
                        agent = line.optString("agent", line.optString("singer", "")).takeIf { it.isNotBlank() }))
                }
            }.sorted()
        }.getOrElse { emptyList() }
    }

    private fun parseJsonTimeToMillis(value: Any?): Long? = when (value) {
        null, JSONObject.NULL -> null
        is Number -> value.toDouble().let { n ->
            if (!n.isFinite() || n < 0) null else n.toLong()
        }
        is String -> {
            val v = value.trim()
            parseClockTimestamp(v) ?: v.toDoubleOrNull()?.let { n ->
                if (!n.isFinite() || n < 0) null else if (v.contains('.') && n < 100_000) (n * 1000).toLong() else n.toLong()
            }
        }
        else -> null
    }

    fun isRecognizedTimedLyrics(lyrics: String): Boolean {
        val source = lyrics.trim().removePrefix("\uFEFF")
        return isTtml(source) || isStructuredTimedLyricsJson(source) ||
            source.contains("-->") || (ENHANCED_LINE_REGEX.containsMatchIn(source) && ENHANCED_WORD_REGEX.containsMatchIn(source)) || source.lines().any { line ->
                Regex("^\\s*(?:\\[\\d{1,3}:\\d{2}(?:[.,]\\d{1,3})?\\])+\\s*.+$").matches(line) ||
                    Regex("^\\s*\\[?\\d{1,2}:\\d{2}(?::\\d{2})?[.,]\\d{1,3}\\]?\\s+.+$").matches(line)
            }
    }

    private fun parseEnhancedLrc(source: String): List<LyricsEntry> {
        data class Row(val timeMs: Long, val matches: List<MatchResult>, val plain: String)

        fun stamp(minutes: String, seconds: String, fraction: String): Long {
            val fractionMs = if (fraction.length == 3) fraction.toLong() else fraction.toLong() * 10L
            return minutes.toLong() * 60_000L + seconds.toLong() * 1_000L + fractionMs
        }

        val rows = source.lineSequence().mapNotNull { raw ->
            val match = ENHANCED_LINE_REGEX.matchEntire(raw.trim()) ?: return@mapNotNull null
            Row(
                timeMs = stamp(match.groupValues[1], match.groupValues[2], match.groupValues[3]),
                matches = ENHANCED_WORD_REGEX.findAll(match.groupValues[4]).toList(),
                plain = match.groupValues[4].trim(),
            )
        }.sortedBy { it.timeMs }.toList()
        if (rows.none { it.matches.isNotEmpty() }) return emptyList()

        return rows.mapIndexedNotNull { index, row ->
            if (row.matches.isEmpty()) {
                val plain = decodeLyricEntities(row.plain)
                return@mapIndexedNotNull if (plain.isBlank()) null else LyricsEntry(row.timeMs, plain)
            }
            val lineEnd = rows.getOrNull(index + 1)?.timeMs
                ?: (stamp(row.matches.last().groupValues[1], row.matches.last().groupValues[2], row.matches.last().groupValues[3]) + 800L)
            val runs = row.matches.mapIndexed { runIndex, match ->
                val start = stamp(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                val end = row.matches.getOrNull(runIndex + 1)?.let {
                    stamp(it.groupValues[1], it.groupValues[2], it.groupValues[3])
                } ?: lineEnd
                Triple(start, end, decodeLyricEntities(match.groupValues[4]))
            }
            val words = enhancedWordsFromRuns(runs)
            if (words.isEmpty()) return@mapIndexedNotNull null
            LyricsEntry(
                time = minOf(row.timeMs, words.first().startTime.let { (it * 1000.0).toLong() }),
                text = words.joinToString(" ") { it.text },
                words = words,
            )
        }.sorted()
    }

    private fun enhancedWordsFromRuns(runs: List<Triple<Long, Long, String>>): List<WordTimestamp> {
        val glue = runs.any { (_, _, raw) -> raw.any(Char::isWhitespace) }
        val words = mutableListOf<WordTimestamp>()
        val current = StringBuilder()
        val syllables = mutableListOf<SyllableTimestamp>()
        var start = 0L
        var end = 0L

        fun flush() {
            if (current.isNotEmpty()) {
                val value = current.toString()
                val normalized = if (syllables.size < 2) emptyList() else syllables.mapIndexed { index, part ->
                    part.copy(
                        startChar = if (index == 0) 0 else part.startChar,
                        endChar = syllables.getOrNull(index + 1)?.startChar ?: value.length,
                    )
                }
                words += WordTimestamp(value, start / 1000.0, end / 1000.0, syllables = normalized)
            }
            current.setLength(0)
            syllables.clear()
        }

        for ((runStart, runEnd, raw) in runs) {
            val content = raw.trim()
            if (content.isEmpty()) {
                flush()
                continue
            }
            val joins = glue && current.isNotEmpty() && !raw.first().isWhitespace() &&
                !isUnspacedScript(current.last()) && !isUnspacedScript(content.first())
            if (!joins) flush()
            if (current.isEmpty()) {
                start = runStart
                end = runStart
            }
            val from = current.length
            current.append(content)
            val safeEnd = maxOf(runStart, runEnd)
            syllables += SyllableTimestamp(runStart / 1000.0, safeEnd / 1000.0, from, current.length)
            end = maxOf(end, safeEnd)
            if (!glue || raw.last().isWhitespace()) flush()
        }
        flush()
        return words
    }

    private fun isUnspacedScript(char: Char): Boolean =
        char in '\u3400'..'\u9FFF' || char in '\u3040'..'\u30FF' ||
            char in '\u3000'..'\u303F' || char in '\uFF00'..'\uFFEF' ||
            char in '\u0E00'..'\u0E7F'

    private fun decodeLyricEntities(text: String): String {
        if ('&' !in text) return text
        return text
            .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }

    /** Auto-detect common lyric payloads without changing the existing karaoke renderer. */
    fun parseAnyLyrics(lyrics: String): List<LyricsEntry> {
        val source = lyrics.trim().removePrefix("\uFEFF")
        if (source.isEmpty()) return emptyList()
        if (isTtml(source)) {
            parseTtml(source).takeIf { it.isNotEmpty() }?.let { return dedupeAdjacent(it) }
            return emptyList()
        }
        if (isStructuredTimedLyricsJson(source)) parseStructuredTimedLyricsJson(source).takeIf { it.isNotEmpty() }?.let { return dedupeAdjacent(it) }
        parseEnhancedLrc(source).takeIf { it.isNotEmpty() }?.let { return dedupeAdjacent(it) }

        // WebVTT and SubRip: cue timestamps may have multiple text lines.
        val cueRegex = Regex("(?m)^\\s*(?:(?:WEBVTT|NOTE)[^\\n]*\\n)?(?:\\d+\\s*\\n)?(\\d{1,2}:\\d{2}(?::\\d{2})?[.,]\\d{1,3})\\s*-->\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?[.,]\\d{1,3})[^\\n]*\\n((?:(?!\\s*\\n).*(?:\\n|$))*)")
        val cues = cueRegex.findAll(source).mapNotNull { match ->
            val start = parseClockTimestamp(match.groupValues[1]) ?: return@mapNotNull null
            val text = match.groupValues[3].lineSequence().joinToString(" ") { it.trim() }
                .replace(Regex("<[^>]+>"), "").trim()
            if (text.isBlank()) null else LyricsEntry(start, text)
        }.toList()
        if (cues.isNotEmpty()) return dedupeAdjacent(cues.sorted())

        // Flexible LRC timestamps: [1:02.3], [01:02], [01:02.34], and multiple stamps per line.
        // Lines may additionally carry word-level <mm:ss.xxx> tags (enhanced LRC); those must
        // never leak into the displayed text, so they are stripped into word timings instead.
        val flexibleLrc = source.lines().flatMap { line ->
            val stamps = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.,](\\d{1,3}))?\\]").findAll(line).toList()
            if (stamps.isEmpty()) emptyList() else {
                val rawText = line.replace(Regex("\\[(\\d{1,3}):(\\d{2})(?:[.,](\\d{1,3}))?\\]"), "").trim()
                if (rawText.isBlank() || rawText.startsWith("ar:", true) || rawText.startsWith("ti:", true) ||
                    rawText.startsWith("al:", true) || rawText.startsWith("by:", true) || rawText.startsWith("offset:", true)) emptyList()
                else stamps.mapNotNull { stamp ->
                    val minutes = stamp.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                    val seconds = stamp.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                    val fraction = stamp.groupValues[3]
                    val millis = when (fraction.length) { 1 -> fraction.toLong() * 100; 2 -> fraction.toLong() * 10; 3 -> fraction.toLong(); else -> 0L }
                    val lineStart = minutes * 60_000L + seconds * 1_000L + millis
                    val (cleanText, words) = stripInlineWordTags(rawText, lineStart)
                    if (cleanText.isBlank()) null
                    else LyricsEntry(lineStart, cleanText, words = words.takeIf { it.isNotEmpty() })
                }
            }
        }
        if (flexibleLrc.isNotEmpty()) return dedupeAdjacent(flexibleLrc.sorted())

        val lrc = parseLyrics(source)
        if (lrc.isNotEmpty()) return dedupeAdjacent(lrc)

        // Timestamp-per-line formats such as `00:12.340 text` or `00:00:12,340 text`.
        val timestamped = source.lines().mapNotNull { line ->
            val m = Regex("^\\s*\\[?(\\d{1,2}:\\d{2}(?::\\d{2})?[.,]\\d{1,3})\\]?\\s+(.+?)\\s*$").matchEntire(line)
                ?: return@mapNotNull null
            val time = parseClockTimestamp(m.groupValues[1]) ?: return@mapNotNull null
            LyricsEntry(time, m.groupValues[2].trim())
        }
        if (timestamped.isNotEmpty()) return dedupeAdjacent(timestamped.sorted())

        // Preserve plain text as untimed entries; callers can retain their existing fallback timing.
        return source.lines().map { it.trim() }.filter { it.isNotEmpty() }
            .mapIndexed { index, line -> LyricsEntry(index * 2500L, line) }
    }

    private fun parseClockTimestamp(value: String): Long? {
        val normalized = value.replace(',', '.')
        val parts = normalized.split(':')
        if (parts.size !in 2..3) return null
        val secondsPart = parts.last().toDoubleOrNull() ?: return null
        val minutes = parts[parts.size - 2].toLongOrNull() ?: return null
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        return (hours * 3600_000L + minutes * 60_000L + (secondsPart * 1000).toLong()).coerceAtLeast(0L)
    }

    fun parseLyrics(lyrics: String): List<LyricsEntry> {
        val lines = lyrics.lines()
        val result = mutableListOf<LyricsEntry>()

        for (line in lines) {
            val entries = parseLine(line)
            if (entries != null) {
                result.addAll(entries)
            }
        }
        return result.sorted()
    }

    private fun parseLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) {
            return null
        }
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        val rawText = matchResult.groupValues[3]
        val timeMatchResults = TIME_REGEX.findAll(times)

        return timeMatchResults
            .map { timeMatchResult ->
                val min = timeMatchResult.groupValues[1].toLong()
                val sec = timeMatchResult.groupValues[2].toLong()
                val milString = timeMatchResult.groupValues[3]
                var mil = milString.toLong()
                if (milString.length == 2) {
                    mil *= 10
                }
                val time = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil
                // Enhanced LRC puts word-level <mm:ss.xxx> tags right after the line
                // timestamp; strip them here too so they never render as raw text.
                val (cleanText, words) = stripInlineWordTags(rawText, time)
                LyricsEntry(time, cleanText, words = words.takeIf { it.isNotEmpty() })
            }.filter { it.text.isNotBlank() }.toList()
    }

    /** Deduplicates adjacent entries only when their timestamp and text both match. */
    private fun dedupeAdjacent(entries: List<LyricsEntry>): List<LyricsEntry> {
        if (entries.size < 2) return entries
        val result = mutableListOf<LyricsEntry>()
        for (entry in entries) {
            val last = result.lastOrNull()
            if (last != null && last.time == entry.time && last.text == entry.text) continue
            result += entry
        }
        return result
    }

    fun findCurrentLineIndex(
        lines: List<LyricsEntry>,
        position: Long,
        leadMs: Long = 0L,
    ): Int {
        if (lines.isEmpty()) return -1

        val target = position + leadMs
        var low = 0
        var high = lines.lastIndex

        while (low <= high) {
            val mid = (low + high).ushr(1)
            val midTime = lines[mid].time

            if (midTime < target) {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return high.coerceIn(0, lines.lastIndex)
    }

    /** Romanizes Katakana using a shared character map and an incremental string builder. */
    fun katakanaToRomaji(katakana: String?): String {
        if (katakana.isNullOrEmpty()) return ""

        val romajiBuilder = StringBuilder(katakana.length) // Initial capacity
        var i = 0
        val n = katakana.length
        while (i < n) {
            var consumed = false
            // Prioritize 2-character sequences from the map (e.g., "キャ" before "キ")
            if (i + 1 < n) {
                val twoCharCandidate = katakana.substring(i, i + 2)
                val mappedTwoChar = KANA_ROMAJI_MAP[twoCharCandidate]
                if (mappedTwoChar != null) {
                    romajiBuilder.append(mappedTwoChar)
                    i += 2
                    consumed = true
                }
            }

            if (!consumed) {
                // If no 2-character sequence matched, try 1-character
                val oneCharCandidate = katakana[i].toString()
                val mappedOneChar = KANA_ROMAJI_MAP[oneCharCandidate]
                if (mappedOneChar != null) {
                    romajiBuilder.append(mappedOneChar)
                } else {
                    // If the character is not in Katakana map, append it as is.
                    romajiBuilder.append(oneCharCandidate)
                }
                i += 1
            }
        }
        return romajiBuilder.toString().lowercase()
    }

    /** Runs Japanese tokenization and Katakana romanization on the Default dispatcher. */
    suspend fun romanizeJapanese(text: String): String = withContext(Dispatchers.Default) { text }

    /** Romanizes Katakana using a shared character map and an incremental string builder. */
    fun katakanaToRomaji(katakana: String?, nextKatakana: String? = null): String {
        if (katakana.isNullOrEmpty()) return ""

        val romajiBuilder = StringBuilder(katakana.length) // Initial capacity
        var i = 0
        val n = katakana.length
        while (i < n) {
            var consumed = false
            // Prioritize 2-character sequences from the map (e.g., "キャ" before "キ")
            if (i + 1 < n) {
                val twoCharCandidate = katakana.substring(i, i + 2)
                val mappedTwoChar = KANA_ROMAJI_MAP[twoCharCandidate]
                if (mappedTwoChar != null) {
                    romajiBuilder.append(mappedTwoChar)
                    i += 2
                    consumed = true
                }
            }

            // Handle sokuon (ッ) - gemination
            if (!consumed && katakana[i] == 'ッ') {
                val nextCharToDouble = nextKatakana?.getOrNull(0)
                if (nextCharToDouble != null) {
                    val nextCharRomaji = KANA_ROMAJI_MAP[nextCharToDouble.toString()]?.getOrNull(0)?.toString()
                        ?: nextCharToDouble.toString()
                    romajiBuilder.append(nextCharRomaji.lowercase().trim())
                }
                // Sokuon itself doesn't have a direct romaji representation other than geminating the next consonant.
                // We just consume 'ッ' and let the next character (if any within the current token) be processed normally.
                i += 1 // Consume the 'ッ'
                consumed = true
            }

            if (!consumed) {
                // If no 2-character sequence matched, try 1-character
                val oneCharCandidate = katakana[i].toString()
                val mappedOneChar = KANA_ROMAJI_MAP[oneCharCandidate]
                if (mappedOneChar != null) {
                    romajiBuilder.append(mappedOneChar)
                } else {
                    // If the character is not in Katakana map, append it as is.
                    romajiBuilder.append(oneCharCandidate)
                }
                i += 1
            }
        }
        return romajiBuilder.toString().lowercase()
    }

    suspend fun romanizeKorean(text: String): String = withContext(Dispatchers.Default) {
        val romajaBuilder = StringBuilder()
        var prevFinal: String? = null

        for (i in text.indices) {
            val char = text[i]

            if (char in '\uAC00'..'\uD7A3') {
                val syllableIndex = char.code - 0xAC00
                
                val choIndex = syllableIndex / (21 * 28)
                val jungIndex = (syllableIndex % (21 * 28)) / 28
                val jongIndex = syllableIndex % 28

                val choChar = (0x1100 + choIndex).toChar().toString()
                val jungChar = (0x1161 + jungIndex).toChar().toString()
                val jongChar = if (jongIndex == 0) null else (0x11A7 + jongIndex).toChar().toString()

                if (prevFinal != null) {
                    val contextKey = prevFinal + choChar
                    val jong = HANGUL_ROMAJA_MAP["jong"]?.get(contextKey)
                        ?: HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal)
                        ?: prevFinal
                    romajaBuilder.append(jong)
                }

                val cho = HANGUL_ROMAJA_MAP["cho"]?.get(choChar) ?: choChar
                val jung = HANGUL_ROMAJA_MAP["jung"]?.get(jungChar) ?: jungChar
                romajaBuilder.append(cho).append(jung)

                prevFinal = jongChar
            } else {
                if (prevFinal != null) {
                    val jong = HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
                    romajaBuilder.append(jong)
                    prevFinal = null
                }
                romajaBuilder.append(char)
            }
        }

        if (prevFinal != null) {
            val jong = HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
            romajaBuilder.append(jong)
        }

        romajaBuilder.toString()
    }

    /** Detect Japanese script characters with an early-exit scan. */
    fun isJapanese(text: String): Boolean {
        return text.any { char ->
            (char in '\u3040'..'\u309F') || // Hiragana
            (char in '\u30A0'..'\u30FF') || // CJK ideographs are shared by Chinese and Japanese; script ranges cannot distinguish them.
            (char in '\u4E00'..'\u9FFF')
        }
    }

    /**
     * Checks if the given text contains any Korean characters (Hangul Syllables, Jamo, etc.).
     */
    fun isKorean(text: String): Boolean {
        return text.any { char ->
            (char in '\uAC00'..'\uD7A3') // Hangul Syllables
        }
    }
        
    /** Detects CJK unified ideographs, which may also appear in Japanese text. */
    fun isChinese(text: String): Boolean {
        if (text.isEmpty()) return false

        val cjkCharCount = text.count { char -> char in '\u4E00'..'\u9FFF' }
        val hiraganaKatakanaCount = text.count { char -> (char in '\u3040'..'\u309F') || (char in '\u30A0'..'\u30FF') }

        // Heuristic: If CJK characters are present and there are very few or no Hiragana/Katakana,
        // it's more likely to be Chinese.
        // The threshold (e.g., 0.1) can be adjusted based on desired sensitivity.
        return cjkCharCount > 0 && (hiraganaKatakanaCount.toDouble() / text.length.toDouble()) < 0.1
    }
}
