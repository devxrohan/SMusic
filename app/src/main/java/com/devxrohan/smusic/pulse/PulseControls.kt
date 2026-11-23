package com.devxrohan.smusic.pulse

enum class PulseControlKind(val label: String) {
    CHOSEN("Artists you chose"), MORE("More like this"), LESS("Do not recommend"), SKIPPED("Lower priority after skips"),
    RECENT("Recent-play cooldown"), PRIORITY("Artist priorities"), ARTIST("Hidden artists")
}

data class PulseControlEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: PulseControlKind,
    val track: PulseFeedbackTrack? = null,
    val timestamp: Long = 0L,
    val thumbnailUrl: String? = null,
)

data class PulseControls(
    val entries: List<PulseControlEntry> = emptyList(),
    val likedSongs: Int = 0,
    val followedArtists: Int = 0,
    val downloadedSongs: Int = 0,
    val completedSongs: Int = 0,
    val replayedSongs: Int = 0,
    val learnedSongs: Int = 0,
    val learnedArtists: Int = 0,
)

/** Explains saved signals; these groups are not a promise of the next queue's ordering. */
internal object PulseControlPolicy {
    fun listeningArtistId(state: PulseState, now: Long): String? = state.artists.entries
        .filter { (id, artist) -> artist.plays > 0 && artist.name.isNotBlank() && !state.isHidden(id, now) }
        .maxByOrNull { it.value.score }?.key

    fun clearFeedback(state: PulseState, songId: String, now: Long) {
        val artists = state.feedbackTracks[songId]?.artistIds
            ?: state.positiveFeedback[songId]?.artistIds ?: state.songs[songId]?.artistIds.orEmpty()
        state.positiveFeedback.remove(songId)
        state.notForMeSongs.remove(songId)
        state.feedbackTracks.remove(songId)
        state.graphVersion++
        artists.forEach { id ->
            val remaining = state.notForMeSongs.count { (sid, time) ->
                now - time < PulseParams.NOT_FOR_ME_MS &&
                    id in (state.feedbackTracks[sid]?.artistIds ?: state.songs[sid]?.artistIds.orEmpty())
            }
            if (remaining < 3) state.dislikedArtists.remove(id)
        }
    }

    fun snapshot(state: PulseState, library: Map<String, PulseFeedbackTrack>, now: Long): PulseControls {
        fun track(id: String): PulseFeedbackTrack {
            val saved = state.feedbackTracks[id]
            val stats = state.songs[id]
            val preferred = state.positiveFeedback[id]
            return library[id]?.let { it.copy(thumbnailUrl = it.thumbnailUrl ?: saved?.thumbnailUrl) } ?: saved ?: PulseFeedbackTrack(id,
                stats?.artistIds ?: preferred?.artistIds.orEmpty(),
                (stats?.artistIds ?: preferred?.artistIds.orEmpty()).map { state.artists[it]?.name.orEmpty() },
                stats?.title?.takeIf(String::isNotBlank) ?: preferred?.title.orEmpty())
        }
        fun song(id: String, kind: PulseControlKind, timestamp: Long): PulseControlEntry {
            val item = track(id)
            return PulseControlEntry(id, item.title.ifBlank { "Song · $id" },
                item.artistNames.filter(String::isNotBlank).joinToString(), kind, item, timestamp, item.thumbnailUrl)
        }
        val entries = buildList {
            state.artistPreferences.forEach { (id, choice) ->
                add(PulseControlEntry(id, choice.name, "Selected for Pulse", PulseControlKind.CHOSEN,
                    timestamp = choice.timestamp, thumbnailUrl = choice.thumbnail))
            }
            state.positiveFeedback.forEach { (id, preference) ->
                if (PulsePreferences.isActive(preference, now)) add(song(id, PulseControlKind.MORE, preference.timestamp))
            }
            state.notForMeSongs.forEach { (id, time) ->
                if (now - time < PulseParams.NOT_FOR_ME_MS) add(song(id, PulseControlKind.LESS, time))
            }
            state.songs.forEach { (id, stats) ->
                if (state.notForMeSongs[id]?.let { now - it < PulseParams.NOT_FOR_ME_MS } != true && !PulsePreferences.isActive(state.positiveFeedback[id], now)) {
                    if (stats.skips > 0 || stats.skipSeverity > 0) add(song(id, PulseControlKind.SKIPPED, stats.lastPlayed))
                    if (state.recentCooldownHours > 0 && stats.lastPlayed > 0 &&
                        now - stats.lastPlayed in 0..(state.recentCooldownHours * 3_600_000).toLong()) {
                        add(song(id, PulseControlKind.RECENT, stats.lastPlayed))
                    }
                }
            }
            val preferredArtists = PulsePreferences.directArtists(state, now)
            state.topArtists(state.artists.size + state.positiveFeedback.size + state.artistPreferences.size, now).filter { !state.isHidden(it.first, now) }.forEach { (id, _) ->
                val artist = state.artists[id]
                add(PulseControlEntry(id, artist?.name?.takeIf(String::isNotBlank) ?: id,
                    "${artist?.plays ?: 0} listens · " +
                        if (id in preferredArtists) "Includes your feedback" else "Learned from listening",
                    PulseControlKind.PRIORITY, thumbnailUrl = state.artistPreferences[id]?.thumbnail))
            }
            (state.blockedArtists + state.dislikedArtists.filterValues {
                now - it < PulseParams.DISLIKE_COOLDOWN_MS
            }.keys).forEach { id ->
                add(PulseControlEntry(id, state.artists[id]?.name?.takeIf(String::isNotBlank) ?: id,
                    if (id in state.blockedArtists) "Blocked by you" else "Temporary cooldown after repeated feedback",
                    PulseControlKind.ARTIST, thumbnailUrl = state.artistPreferences[id]?.thumbnail))
            }
        }.sortedByDescending { it.timestamp }
        return PulseControls(entries, state.songs.values.count { it.liked }, state.subscribedArtists.size,
            state.downloadedSongs.size, state.songs.values.count { it.completions > 0 },
            state.songs.values.count { it.replays > 0 }, state.songs.values.count { it.plays > 0 },
            state.artists.values.count { it.plays > 0 })
    }
}
