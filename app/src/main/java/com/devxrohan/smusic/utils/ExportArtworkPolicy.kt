package com.devxrohan.smusic.utils

/** Separate untagged singles so Android cannot reuse one unknown album's artwork. */
internal object ExportArtworkPolicy {
    fun album(title: String, artist: String, album: String?): String =
        album?.trim()?.takeUnless { it.isBlank() || it.equals("<unknown>", true) || it.equals("Unknown album", true) }
            ?: listOf(title.trim(), artist.trim()).filter(String::isNotBlank).joinToString(" — ")

    fun cacheKey(songId: String, url: String): String = "audio-export-cover:$songId:$url"
}
