package com.devxrohan.smusic.playback.queues

import com.devxrohan.smusic.lyrics.artistForLyricsSearch
import com.devxrohan.smusic.lyrics.forLyricsSearch
import java.text.Normalizer
import java.util.Locale

/** Deduplicate automatic continuations without changing manual replay or ordered playlists. */
internal object QueueContinuationPolicy {
    private val separators = Regex("[^\\p{L}\\p{M}\\p{N}]+")
    private fun key(text: String) = Normalizer.normalize(text, Normalizer.Form.NFC)
        .lowercase(Locale.ROOT).replace(separators, " ").trim()

    fun recordingKey(title: String, artists: List<String>): String? {
        val artist = artists.asSequence().map { it.artistForLyricsSearch() }.firstOrNull { it.isNotBlank() } ?: return null
        val songKey = key(title.forLyricsSearch())
        val artistKey = key(artist)
        return if (songKey.isBlank() || artistKey.isBlank()) null else "$artistKey|$songKey"
    }

    fun <T> unseen(items: List<T>, occupied: Set<String>, recordingKeys: Set<String> = emptySet(),
        recordingKeyOf: (T) -> String? = { null }, idOf: (T) -> String): List<T> {
        val seen = occupied.toMutableSet()
        val recordings = recordingKeys.toMutableSet()
        return items.filter { item ->
            val id = idOf(item)
            val recording = recordingKeyOf(item)
            id.isNotBlank() && seen.add(id) && (recording == null || recordings.add(recording))
        }
    }
}
