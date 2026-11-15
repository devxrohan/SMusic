/*
 * SMusic Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.devxrohan.smusic.lyrics

import kotlinx.coroutines.flow.MutableStateFlow

data class SyllableTimestamp(
    val startTime: Double,
    val endTime: Double,
    val startChar: Int,
    val endChar: Int,
)

data class WordTimestamp(
    val text: String,
    val startTime: Double,
    val endTime: Double,
    val isBackground: Boolean = false,
    val syllables: List<SyllableTimestamp> = emptyList(),
)

data class LyricsEntry(
    val time: Long,
    val text: String,
    val words: List<WordTimestamp>? = null,
    val agent: String? = null,
    val romanizedTextFlow: MutableStateFlow<String?> = MutableStateFlow(null),
    val translatedTextFlow: MutableStateFlow<String?> = MutableStateFlow(null),
    /** Provider-declared end of the vocal line, when it is known. */
    val sungUntilMs: Long? = null,
) : Comparable<LyricsEntry> {
    override fun compareTo(other: LyricsEntry): Int = time.compareTo(other.time)

    companion object {
        val HEAD_LYRICS_ENTRY = LyricsEntry(0L, "")
    }
}
