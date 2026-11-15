package com.devxrohan.smusic.lyrics

/** Shared parsing and playhead lookup used by every lyrics presentation. */
object LyricsUtils {
    fun parseLyrics(lyrics: String): List<LyricsEntry> =
        SMusicLyricsUtils.parseAnyLyrics(lyrics)

    /** The active row is the last lyric whose start timestamp has passed. */
    fun findCurrentLineIndex(
        lines: List<LyricsEntry>,
        position: Long,
    ): Int = SMusicLyricsUtils.findCurrentLineIndex(lines, position, 0L)
}
