package com.devxrohan.smusic.lyrics

/** Shared parsing and playhead lookup used by every lyrics presentation. */
object LyricsUtils {
    /** Positive values delay highlights; invalid historical values use safe app defaults. */
    fun syncOffsetMillis(value: Float): Long =
        value.takeIf { it.isFinite() }?.coerceIn(-10_000f, 10_000f)?.toLong() ?: 0L

    fun parseLyrics(lyrics: String): List<LyricsEntry> =
        SMusicLyricsUtils.parseAnyLyrics(lyrics)

    /** The active row is the last lyric whose start timestamp has passed. */
    fun findCurrentLineIndex(
        lines: List<LyricsEntry>,
        position: Long,
    ): Int = SMusicLyricsUtils.findCurrentLineIndex(lines, position, 0L)
}
