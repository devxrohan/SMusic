package com.devxrohan.smusic.pulse

import kotlin.math.exp

data class PulsePreference(val timestamp: Long, val artistIds: List<String>, val title: String = "")
data class PulseFeedbackTrack(
    val id: String, val artistIds: List<String>, val artistNames: List<String> = emptyList(), val title: String = "",
)

internal data class PulsePreferenceWeights(val songs: Map<String, Double>, val artists: Map<String, Double>) {
    fun score(candidate: PulseCandidate): Double =
        (if (candidate.kind == PulseKind.SONG) songs[candidate.id] ?: 0.0 else 0.0) * .65 +
            (candidate.artistIds.maxOfOrNull { artists[it] ?: 0.0 } ?: 0.0) * .40
}

/** Explicit intent is bounded, time-decayed and independent of listening counts and favourites. */
internal object PulsePreferences {
    private const val MAX_ENTRIES = 500
    private const val LIFETIME = 90L * 86_400_000
    private fun weight(timestamp: Long, now: Long): Double {
        val age = (now - timestamp).coerceAtLeast(0)
        return if (age >= LIFETIME) 0.0 else exp(-age.toDouble() / (30L * 86_400_000))
    }

    fun isActive(preference: PulsePreference?, now: Long): Boolean =
        preference != null && weight(preference.timestamp, now) > 0.0

    fun apply(state: PulseState, track: PulseFeedbackTrack, now: Long) {
        if (track.id.isBlank()) return
        val artists = track.artistIds.filter(String::isNotBlank).distinct()
        state.positiveFeedback[track.id] = PulsePreference(now, artists, track.title)
        state.graphVersion++
        state.notForMeSongs.remove(track.id)
        artists.forEach { id ->
            state.dislikedArtists.remove(id)
            val name = track.artistNames.getOrNull(track.artistIds.indexOf(id)).orEmpty()
            val artist = state.artists.getOrPut(id) { ArtistAffinity() }
            if (name.isNotBlank()) artist.name = name
        }
        state.positiveFeedback.entries.removeAll { weight(it.value.timestamp, now) == 0.0 }
        while (state.positiveFeedback.size > MAX_ENTRIES) {
            state.positiveFeedback.remove(state.positiveFeedback.minBy { it.value.timestamp }.key)
        }
    }

    fun directArtists(state: PulseState, now: Long): Map<String, Double> {
        val result = HashMap<String, Double>()
        state.positiveFeedback.values.forEach { preference ->
            val strength = weight(preference.timestamp, now)
            if (strength > 0.0) preference.artistIds.forEach { id ->
                if (!state.isHidden(id, now)) result[id] = maxOf(result[id] ?: 0.0, strength)
            }
        }
        return result
    }

    fun hasActive(state: PulseState, now: Long): Boolean =
        state.positiveFeedback.values.any { weight(it.timestamp, now) > 0.0 && it.artistIds.none { id -> state.isHidden(id, now) } }

    fun weights(state: PulseState, now: Long): PulsePreferenceWeights {
        val songs = state.positiveFeedback.mapValues { weight(it.value.timestamp, now) }.filterValues { it > 0.0 }
        val direct = directArtists(state, now)
        val artists = direct.toMutableMap()
        state.relatedEdges.forEach { (pair, evidence) ->
            val separator = pair.indexOf('|')
            if (separator < 0 || evidence <= 0.0) return@forEach
            val a = pair.substring(0, separator)
            val b = pair.substring(separator + 1)
            fun transfer(from: String, to: String) {
                val interest = direct[from] ?: return
                if (!state.isHidden(to, now)) {
                    val strength = interest * .55 * evidence / (evidence + .08)
                    artists[to] = maxOf(artists[to] ?: 0.0, strength)
                }
            }
            transfer(a, b)
            transfer(b, a)
        }
        return PulsePreferenceWeights(songs, artists)
    }
}
