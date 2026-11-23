package com.devxrohan.smusic.playback

import java.text.Normalizer
import kotlin.math.abs

/** Conservative recording matching preserves remixes, live cuts and slowed versions. */
internal object MusicVersionMatch {
    fun normalized(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFKC)
        .replace(Regex("\\b(official music video|official video|official audio|lyrics? video|visualizer)\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex(" +"), " ")

    fun matches(title: String, artists: List<String>, seconds: Int,
                candidateTitle: String, candidateArtists: List<String>, candidateSeconds: Int): Boolean {
        if (normalized(title).isBlank() || normalized(title) != normalized(candidateTitle)) return false
        val names = artists.map { normalized(it.removeSuffix(" - Topic")) }.filter(String::isNotBlank)
        if (names.none { source -> candidateArtists.any { normalized(it.removeSuffix(" - Topic")) == source } }) return false
        return seconds <= 0 || candidateSeconds <= 0 || abs(seconds - candidateSeconds) <= maxOf(8, seconds / 12)
    }
}
