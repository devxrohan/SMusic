package com.devxrohan.smusic.pulse

import kotlin.math.pow

/** Pure learning functions: no I/O, no Android types. Called under the engine mutex. */
internal object PulseLearn {
    private fun ema(cur: Double, target: Double, alpha: Double) =
        (cur + (target - cur) * alpha).coerceIn(0.0, 1.0)

    private fun milestoneWeight(m: Double) = when {
        m >= 0.90 -> 1.00
        m >= 0.50 -> 0.70
        else -> 0.35
    }

    /**
     * One observed listen. Returns true when it COUNTED (>= 50% played or explicit like).
     * [prevPercent] lets live updates avoid re-crossing milestones; backfill passes 0.
     */
    fun applySignal(state: PulseState, sig: PulseSignal, now: Long = sig.timestamp): Boolean {
        decayRotationIfDue(state, now)
        val artistIds = sig.artistIds.filter { it.isNotBlank() }
        val pct = sig.percentPlayed.coerceIn(0.0, 1.0)
        val crossed = PulseParams.milestones.filter { it <= pct }
        val counted = pct >= PulseParams.COUNT_MILESTONE || sig.liked
        val skipped = pct < PulseParams.SKIP_PERCENT && !sig.liked

        val song = state.songs.getOrPut(sig.songId) { SongStats() }
        if (sig.title.isNotBlank()) song.title = sig.title
        if (artistIds.isNotEmpty()) song.artistIds = artistIds

        if (skipped) {
            song.skips += 1
            song.skipSeverity += when { pct < 0.10 -> 1.0; pct < 0.25 -> 0.7; else -> 0.3 }
            artistIds.forEach { id ->
                state.artists[id]?.let {
                    it.skips += 1
                    it.score = (it.score * PulseParams.SKIP_SCORE_FACTOR).coerceIn(0.0, 1.0)
                }
            }
            state.exposure[sig.songId]?.let { it.skipped += 1 }
            prune(state)
            return false
        }

        artistIds.forEachIndexed { i, id ->
            val novel = id !in state.seenArtists
            val e = state.artists.getOrPut(id) { ArtistAffinity() }
            sig.artistNames.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { e.name = it }
            if (e.firstPlayed == 0L) e.firstPlayed = now
            for (m in crossed) {
                e.score = ema(e.score, milestoneWeight(m), PulseParams.ALPHA_LONG)
                e.lastPlayed = now
                state.seenArtists.add(id)
            }
            if (counted) {
                e.plays += 1
                e.lastPlayed = now
                if (sig.liked) {
                    e.liked = true
                    e.score = maxOf(e.score, PulseParams.LIKE_SCORE_FLOOR)
                }
                state.recentRotation[id] = ema(state.recentRotation[id] ?: 0.0, 1.0, PulseParams.ALPHA_MED)
                val bucket = state.timeBuckets.getOrPut(PulseTimeBucket.fromTimestamp(now)) { HashMap() }
                bucket[id] = (bucket[id] ?: 0.0) + 1.0
                if (i == 0) updateAppetite(state, novel)
                state.dislikedArtists.remove(id) // a real listen forgives a dislike; a block does not
            }
        }
        if (!counted) { prune(state); return false }

        // Song-level intelligence
        if (song.firstPlayed == 0L) song.firstPlayed = now
        if (song.plays > 0) song.replays += 1
        song.plays += 1
        song.lastPlayed = now
        if (pct >= 0.90) song.completions += 1
        if (sig.liked) song.liked = true
        song.recent.add(now)
        while (song.recent.size > PulseParams.SONG_RING) song.recent.removeAt(0)
        state.notForMeSongs.remove(sig.songId)
        state.totalPlays += 1

        sig.albumId?.takeIf { it.isNotBlank() }?.let { aid ->
            val a = state.albums.getOrPut(aid) { AlbumStats() }
            a.plays += 1
            a.lastPlayed = now
            if (pct >= 0.90) a.completions += 1
        }

        state.exposure[sig.songId]?.let {
            it.played += 1
            if (pct >= 0.90) it.completed += 1
            // A recommendation of something unfamiliar that actually got played is the strongest discovery signal.
            if (it.novel && it.played == 1) {
                state.novelPlayed += 1
                state.discoveryAppetite = (state.discoveryAppetite + 0.01)
                    .coerceIn(PulseParams.APPETITE_MIN, PulseParams.APPETITE_MAX)
            }
        }

        // Session continuity -> co-listening and transitions
        val primary = artistIds.firstOrNull()
        val sameSession = state.sessionLastTs != 0L && now - state.sessionLastTs <= PulseParams.SESSION_GAP_MS
        if (sameSession) {
            val prevArtist = state.sessionLastArtist
            if (prevArtist != null && primary != null && prevArtist != primary) {
                val k = pairKey(prevArtist, primary)
                state.artistCooc[k] = (state.artistCooc[k] ?: 0.0) + 1.0
                state.graphVersion++
            }
            val prevSong = state.sessionLastSong
            if (prevSong != null && prevSong != sig.songId) {
                val k = "$prevSong>${sig.songId}"
                state.songTransitions[k] = (state.songTransitions[k] ?: 0.0) + 1.0
            }
        }
        if (!sameSession) state.sessionRecent.clear()
        if (primary != null) {
            state.sessionRecent.add(primary)
            while (state.sessionRecent.size > 8) state.sessionRecent.removeAt(0)
        }
        state.sessionLastSong = sig.songId
        state.sessionLastArtist = primary
        state.sessionLastTs = now

        prune(state)
        return true
    }

    fun applyFavorite(state: PulseState, songId: String, artistIds: List<String>, liked: Boolean) {
        val s = state.songs.getOrPut(songId) { SongStats() }
        s.liked = liked
        if (liked) {
            state.notForMeSongs.remove(songId)
            artistIds.forEach { id ->
                val e = state.artists.getOrPut(id) { ArtistAffinity() }
                e.score = maxOf(e.score, PulseParams.LIKE_SCORE_FLOOR)
                e.liked = true
            }
        }
    }

    /** Soft-suppresses the song only. The artist is NOT blocked; a repeat within cooldown nudges them down. */
    fun applyNotForMe(state: PulseState, songId: String, artistIds: List<String>, now: Long) {
        state.notForMeSongs[songId] = now
        state.exposure[songId]?.let { it.skipped += 1 }
        artistIds.forEach { id ->
            state.artists[id]?.let { it.score = (it.score * 0.9).coerceIn(0.0, 1.0) }
            state.recentRotation[id]?.let { state.recentRotation[id] = it * 0.5 }
        }
        val strikes = state.notForMeSongs.keys.count { sid ->
            state.songs[sid]?.artistIds?.any { it in artistIds } == true
        }
        if (strikes >= 3) artistIds.forEach { state.dislikedArtists[it] = now }
        state.discoveryAppetite = (state.discoveryAppetite - 0.01)
            .coerceIn(PulseParams.APPETITE_MIN, PulseParams.APPETITE_MAX)
        prune(state)
    }

    fun blockArtist(state: PulseState, artistId: String) {
        if (artistId.isBlank()) return
        state.blockedArtists.add(artistId)
        state.dislikedArtists.remove(artistId)
        state.recentRotation.remove(artistId)
        state.artists[artistId]?.let { it.score = 0.0; it.liked = false }
    }

    fun unblockArtist(state: PulseState, artistId: String) {
        state.blockedArtists.remove(artistId)
        state.dislikedArtists.remove(artistId)
    }

    fun recordShown(state: PulseState, ids: Collection<String>, now: Long, meta: PulseExposureMeta = PulseExposureMeta()) {
        ids.forEachIndexed { index, id ->
            val fresh = id !in state.exposure
            val e = state.exposure.getOrPut(id) { Exposure() }
            if (fresh) {
                // Unfamiliar = neither a song nor an artist Pulse has ever learned from.
                e.novel = id !in state.songs && id !in state.artists
                if (e.novel) state.novelShown += 1
            }
            e.shown += 1
            e.lastShown = now
            if (meta.source.isNotBlank()) e.source = meta.source
            if (meta.shelf.isNotBlank()) e.shelf = meta.shelf
            e.position = if (meta.position >= 0) meta.position + index else e.position
            if (meta.reason.isNotBlank()) e.reason = meta.reason
        }
        adaptAppetite(state)
        prune(state)
    }

    /**
     * Closes the loop: if the user keeps ignoring unfamiliar recommendations, discovery eases off;
     * if they keep accepting them, it grows. Only acts with enough evidence.
     */
    fun adaptAppetite(state: PulseState) {
        if (state.novelShown < PulseParams.NOVEL_ACCEPT_MIN_SHOWN) return
        val acceptance = (state.novelPlayed.toDouble() / state.novelShown).coerceIn(0.0, 1.0)
        val target = 0.15 + acceptance * 0.7
        state.discoveryAppetite = (state.discoveryAppetite * 0.97 + target * 0.03)
            .coerceIn(PulseParams.APPETITE_MIN, PulseParams.APPETITE_MAX)
    }

    /** Learn artist relatedness from a source result: anchor artists <-> artists found in its related items. */
    fun learnRelated(state: PulseState, anchors: List<String>, relatedArtistIds: Collection<String>, weight: Double = 0.3) {
        val a = anchors.filter { it.isNotBlank() }
        a.forEach { x ->
            relatedArtistIds.forEach { y ->
                if (x != y && y.isNotBlank()) {
                    val k = pairKey(x, y)
                    state.relatedEdges[k] = minOf(5.0, (state.relatedEdges[k] ?: 0.0) + weight)
                }
            }
        }
        state.graphVersion++
        prune(state)
    }

    fun updateAppetite(state: PulseState, wasNovel: Boolean) {
        var a = state.discoveryAppetite
        a += (PulseParams.DISCOVERY_NEUTRAL - a) * PulseParams.APPETITE_REGRESS
        if (wasNovel) a += PulseParams.APPETITE_NOVEL_BONUS
        state.discoveryAppetite = a.coerceIn(PulseParams.APPETITE_MIN, PulseParams.APPETITE_MAX)
    }

    fun decayRotationIfDue(state: PulseState, now: Long) {
        if (state.lastRotationDecay == 0L) { state.lastRotationDecay = now; return }
        val days = (now - state.lastRotationDecay) / 86_400_000L
        if (days <= 0) return
        val factor = PulseParams.ROTATION_DECAY_PER_DAY.pow(days.toDouble())
        val it = state.recentRotation.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val v = e.value * factor
            if (v < PulseParams.ROTATION_PRUNE_FLOOR) it.remove() else e.setValue(v)
        }
        state.lastRotationDecay = now
    }

    private fun <V> trimByValue(map: MutableMap<String, V>, max: Int, keep: Int, key: (V) -> Double) {
        if (map.size <= max) return
        val drop = map.entries.sortedBy { key(it.value) }.take(map.size - keep).map { it.key }
        drop.forEach { map.remove(it) }
    }

    fun prune(s: PulseState) {
        trimByValue(s.artists, PulseParams.ARTISTS_MAX, PulseParams.ARTISTS_KEEP) { it.score }
        trimByValue(s.songs, PulseParams.SONGS_MAX, PulseParams.SONGS_KEEP) { it.lastPlayed.toDouble() }
        trimByValue(s.artistCooc, PulseParams.COOC_MAX, PulseParams.COOC_KEEP) { it }
        trimByValue(s.songTransitions, PulseParams.TRANSITIONS_MAX, PulseParams.TRANSITIONS_KEEP) { it }
        if (s.relatedEdges.size > PulseParams.RELATED_MAX || s.artistCooc.size > PulseParams.COOC_MAX) s.graphVersion++
        trimByValue(s.relatedEdges, PulseParams.RELATED_MAX, PulseParams.RELATED_KEEP) { it }
        trimByValue(s.albums, PulseParams.ALBUMS_MAX, PulseParams.ALBUMS_KEEP) { it.lastPlayed.toDouble() }
        trimByValue(s.recentRotation, PulseParams.ROTATION_MAX, PulseParams.ROTATION_KEEP) { it }
        trimByValue(s.exposure, PulseParams.EXPOSURE_MAX, PulseParams.EXPOSURE_KEEP) { it.lastShown.toDouble() }
        if (s.seenArtists.size > PulseParams.SEEN_MAX) {
            val keep = s.seenArtists.toList().takeLast(PulseParams.SEEN_MAX - 500)
            s.seenArtists.clear(); s.seenArtists.addAll(keep)
        }
    }
}
