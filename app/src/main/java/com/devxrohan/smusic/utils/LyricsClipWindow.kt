package com.devxrohan.smusic.utils

internal data class LyricsClipWindow(val startMs: Long, val durationMs: Long) {
    val endMs: Long get() = startMs + durationMs
}

/** Clip duration is a span, not the absolute timestamp of the last selected lyric. */
internal fun lyricsClipWindow(startMs: Long, endMs: Long): LyricsClipWindow {
    require(startMs >= 0 && endMs >= startMs)
    return LyricsClipWindow(startMs, (endMs - startMs).coerceIn(1_200L, 45_000L))
}
