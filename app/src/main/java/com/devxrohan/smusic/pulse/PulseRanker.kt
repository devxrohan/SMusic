package com.devxrohan.smusic.pulse

import kotlin.math.ln
import kotlin.math.pow

enum class PulseKind { SONG, ARTIST, ALBUM, PLAYLIST }

/** Minimal projection of any candidate entity for ranking. Songs/albums carry artist ids; artists carry themselves. */
data class PulseCandidate(
    val id: String,
    val kind: PulseKind,
    val artistIds: List<String>,
)

enum class PulseSurface { KEEP_LISTENING, SIMILAR, SHELF, DISCOVER, RADIO }

internal data class SurfaceWeights(
    val fam: Double, val act: Double, val rot: Double, val cooc: Double,
    val ctx: Double, val song: Double, val discovery: Double, val targetNovelty: Double,
    val clu: Double = 0.0, val trans: Double = 0.0, val emb: Double = 0.0,
)

/** Per-rank-call context computed once: taste groups, short- vs long-term drift, and the last-played song. */
internal class RankContext(
    val taste: TasteMap, val drift: Double, val lastSongId: String?,
    val emb: Embedding, val tv: TasteVectors, val seedArtists: List<String>,
) {
    /** Sound-space closeness (0..1) to what the user is into; for a radio, blended with closeness to the seed. */
    fun sim(artistIds: List<String>): Double {
        if (emb.vec.isEmpty() || artistIds.isEmpty()) return 0.0
        val taste = PulseEmbedding.similarity(emb, tv, artistIds)
        if (seedArtists.isEmpty()) return taste
        return 0.6 * PulseEmbedding.similarityToSeed(emb, seedArtists, artistIds) + 0.4 * taste
    }
}

internal object PulseRanker {
    fun squash(x: Double) = x / (1 + x)

    /** ACT-R base-level activation over the song's recent plays; clamped >= 0. */
    fun activation(state: PulseState, songId: String, now: Long): Double {
        val stamps = state.songs[songId]?.recent ?: return 0.0
        var sum = 0.0
        for (ts in stamps) {
            val h = ((now - ts) / 3_600_000.0).coerceAtLeast(PulseParams.ACT_DT_FLOOR_HOURS)
            sum += h.pow(-PulseParams.ACT_DECAY)
        }
        return if (sum <= 0.0) 0.0 else maxOf(ln(sum), 0.0)
    }

    fun weights(surface: PulseSurface) = when (surface) {
        PulseSurface.KEEP_LISTENING -> SurfaceWeights(0.45, 0.35, 0.30, 0.10, 0.12, 0.25, 0.05, 0.10, clu = 0.08, emb = 0.05)
        PulseSurface.SIMILAR -> SurfaceWeights(0.15, 0.00, 0.10, 0.25, 0.08, 0.00, 0.55, 0.55, clu = 0.25, emb = 0.35)
        PulseSurface.DISCOVER -> SurfaceWeights(0.15, 0.00, 0.10, 0.20, 0.08, 0.00, 0.60, 0.75, clu = 0.30, emb = 0.40)
        PulseSurface.RADIO -> SurfaceWeights(0.35, 0.20, 0.20, 0.25, 0.20, 0.10, 0.25, 0.35, clu = 0.15, trans = 0.35, emb = 0.45)
        PulseSurface.SHELF -> SurfaceWeights(0.35, 0.25, 0.20, 0.15, 0.12, 0.20, 0.15, 0.25, clu = 0.12, emb = 0.15)
    }

    /** 0 = recent taste matches long-term taste, 1 = completely different artists lately. */
    fun drift(state: PulseState): Double {
        val long = state.topArtists(5).map { it.first }.toSet()
        val short = state.recentRotation.entries.sortedByDescending { it.value }.take(5).map { it.key }.toSet()
        if (long.size < 3 || short.size < 3) return 0.0
        return 1.0 - long.intersect(short).size.toDouble() / minOf(long.size, short.size)
    }

    private fun anchors(state: PulseState) = state.topArtists(PulseParams.COOC_ANCHORS).map { it.first }

    private fun cooc(state: PulseState, artistId: String, anchors: List<String>): Double {
        if (state.artistCooc.isEmpty()) return 0.0
        var best = 0.0
        for (a in anchors) {
            if (a == artistId) continue
            val w = state.artistCooc[pairKey(artistId, a)] ?: continue
            best = maxOf(best, squash(w))
        }
        return best
    }

    private fun context(state: PulseState, artistId: String, now: Long): Double {
        val hist = state.timeBuckets[PulseTimeBucket.fromTimestamp(now)] ?: return 0.0
        val total = hist.values.sum()
        if (total <= 0.0) return 0.0
        return ((hist[artistId] ?: 0.0) / total) * (total / (total + PulseParams.CONTEXT_CONFIDENCE_K))
    }

    private fun isNovel(state: PulseState, c: PulseCandidate): Boolean =
        c.artistIds.isNotEmpty() && c.artistIds.none { it in state.seenArtists }

    fun score(state: PulseState, c: PulseCandidate, w: SurfaceWeights, anchors: List<String>, now: Long, rc: RankContext, sim: Double = 0.0): Double {
        val primary = c.artistIds.firstOrNull() ?: ""
        val fam = c.artistIds.maxOfOrNull { state.artists[it]?.score ?: 0.0 } ?: 0.0
        // When recent taste has drifted from long-term taste, what you're into *right now* counts for more.
        val rot = (c.artistIds.maxOfOrNull { state.recentRotation[it] ?: 0.0 } ?: 0.0) * (1.0 + rc.drift * 0.6)
        val clu = c.artistIds.maxOfOrNull { rc.taste.massOf(it) } ?: 0.0
        val trans = if (c.kind == PulseKind.SONG && rc.lastSongId != null)
            squash(state.songTransitions["${rc.lastSongId}>${c.id}"] ?: 0.0) else 0.0
        val act = if (c.kind == PulseKind.SONG) squash(activation(state, c.id, now)) else 0.0
        val co = c.artistIds.maxOfOrNull { cooc(state, it, anchors) } ?: 0.0
        val ctx = if (primary.isEmpty()) 0.0 else context(state, primary, now)
        val st = state.songs[c.id]
        var songAff = 0.0
        var penalty = 0.0
        if (c.kind == PulseKind.ALBUM) {
            state.albums[c.id]?.let { songAff = squash(it.plays * 0.5 + it.completions * 0.5) }
        }
        if (c.kind == PulseKind.SONG && st != null) {
            // Loves THIS song (replays / completions / like), not merely the artist.
            songAff = squash(st.replays * 0.6 + st.completions * 0.4 + if (st.liked) 1.0 else 0.0)
            penalty += squash(st.skips.toDouble()) * 0.20 + squash(st.skipSeverity) * 0.45
        }
        val novel = if (isNovel(state, c)) 1.0 else 0.0
        val discovery = w.discovery * novel * state.discoveryAppetite
        // Explicit intent is stronger than inferred taste: subscriptions and offline saves
        // should noticeably pull related artists/songs upward without making them mandatory.
        val subscribedBoost = if (c.artistIds.any { it in state.subscribedArtists }) 0.16 else 0.0
        val downloadedBoost = if (c.kind == PulseKind.SONG && c.id in state.downloadedSongs) 0.11 else 0.0

        var s = w.fam * fam + w.act * act + w.rot * rot + w.cooc * co + w.ctx * ctx + w.song * songAff + w.clu * clu + w.trans * trans + w.emb * sim + discovery + subscribedBoost + downloadedBoost - penalty

        // Freshness: recently shown content sinks; a shown item the user ignored sinks further.
        state.exposure[c.id]?.let { e ->
            if (now - e.lastShown < PulseParams.FRESHNESS_WINDOW_MS) s *= 0.6
            if (e.shown >= 3 && e.played == 0) s *= 0.7
        }
        if (c.kind == PulseKind.SONG) {
            state.notForMeSongs[c.id]?.let { ts ->
                if (now - ts < PulseParams.NOT_FOR_ME_MS) s *= 0.05
            }
        }
        return s
    }

    /**
     * Full pipeline: score, hard-drop blocked / cooled-down artists, compose to the surface's novelty
     * target, then spread long same-artist runs. Returns candidate indices in final order.
     */
    /** Minimum explainable connection to the user/seed taste. Prevents generic source candidates from leaking into Pulse. */
    fun relevance(state: PulseState, c: PulseCandidate, surface: PulseSurface, now: Long, seedArtistIds: List<String> = emptyList()): Double {
        val emb = PulseEmbedding.get(state)
        val tv = PulseEmbedding.tasteVectors(state, emb, now)
        val seed = if (seedArtistIds.isEmpty()) 0.0 else PulseEmbedding.similarityToSeed(emb, seedArtistIds, c.artistIds)
        val taste = PulseEmbedding.similarity(emb, tv, c.artistIds)
        val top = c.artistIds.maxOfOrNull { state.artists[it]?.score ?: 0.0 } ?: 0.0
        val co = c.artistIds.maxOfOrNull { id ->
            state.topArtists(PulseParams.COOC_ANCHORS).maxOfOrNull { a ->
                state.artistCooc[pairKey(id, a.first)]?.let { squash(it) } ?: 0.0
            } ?: 0.0
        } ?: 0.0
        val related = c.artistIds.maxOfOrNull { id ->
            state.topArtists(PulseParams.COOC_ANCHORS).maxOfOrNull { a ->
                state.relatedEdges[pairKey(id, a.first)]?.let { squash(it) } ?: 0.0
            } ?: 0.0
        } ?: 0.0
        val explicit = (if (c.artistIds.any { it in state.subscribedArtists }) 0.10 else 0.0) +
            (if (c.kind == PulseKind.SONG && c.id in state.downloadedSongs) 0.06 else 0.0)
        return (taste * 0.35 + seed * 0.30 + top * 0.15 + co * 0.12 + related * 0.08 + explicit).coerceIn(0.0, 1.0)
    }

    fun rank(
        state: PulseState, inputs: List<PulseCandidate>, surface: PulseSurface, now: Long,
        lastSongId: String? = null,
        seedArtistIds: List<String> = emptyList(),
    ): List<Int> {
        if (inputs.isEmpty()) return emptyList()
        val w = weights(surface)
        val an = anchors(state)
        val emb = PulseEmbedding.get(state)
        val rc = RankContext(PulseClusters.compute(state), drift(state), lastSongId, emb,
            PulseEmbedding.tasteVectors(state, emb, now), seedArtistIds)
        val sims = inputs.map { rc.sim(it.artistIds) }
        val scores = inputs.indices.map { score(state, inputs[it], w, an, now, rc, sims[it]) }
        val baseOrder = inputs.indices
            .filterNot { i -> inputs[i].artistIds.any { state.isHidden(it, now) } }
            .sortedWith(compareByDescending<Int> { scores[it] }.thenBy { it })

        // For recommendation surfaces, prefer candidates that have an explainable connection
        // to the user's taste/seed. Keep a graceful fallback when the source returns too few
        // explainable candidates so Home/Radio never becomes empty because metadata is sparse.
        val order = if (surface != PulseSurface.KEEP_LISTENING) {
            val rel = inputs.map { relevance(state, it, surface, now, seedArtistIds) }
            val threshold = if (surface == PulseSurface.RADIO) PulseParams.MIN_RELEVANCE else 0.08
            val relevant = baseOrder.filter { rel[it] >= threshold }
            if (relevant.size >= minOf(6, baseOrder.size.coerceAtLeast(1))) relevant else baseOrder
        } else baseOrder

        val target = (w.targetNovelty + (state.discoveryAppetite - PulseParams.DISCOVERY_NEUTRAL) * 0.5)
            .coerceIn(0.05, 0.95)
        val novel = order.filter { isNovel(state, inputs[it]) }.toSet()
        val adjacent = novel.filter { i ->
            inputs[i].artistIds.any { cooc(state, it, an) > 0.0 || rc.taste.massOf(it) >= 0.3 }
        }.toSet()
        val composed = if (surface == PulseSurface.RADIO && emb.vec.isNotEmpty())
            composeRadio(order, sims, scores, state.discoveryAppetite)
        else compose(order, novel, adjacent, target)
        return spread(composed, inputs, if (surface == PulseSurface.RADIO) 1 else PulseParams.MAX_CONSECUTIVE_ARTIST)
    }

    /**
     * Radio flow in sound space: mostly NEAR (very close to the seed/session), regularly a STRETCH (same taste
     * neighbourhood but a step sideways), and FAR items (unrelated to anything known) only at the very end.
     * Rhythm is near, near, stretch, near, near, stretch... ; a discovery-hungry user gets stretches more often.
     */
    internal fun composeRadio(order: List<Int>, sims: List<Double>, scores: List<Double>, appetite: Double): List<Int> {
        if (order.size < 4) return order
        val maxScore = order.maxOf { scores[it] }.takeIf { it > 0.0 } ?: 1.0
        // Sound-space closeness leads; taste score (familiarity/recency/etc.) refines.
        fun key(i: Int) = 0.6 * sims[i] + 0.4 * (scores[i] / maxScore).coerceIn(0.0, 1.0)
        val eligible = order.filter { sims[it] >= FAR_BELOW }.sortedByDescending { key(it) }
        val far = order.filter { sims[it] < FAR_BELOW }          // unrelated to anything known: only at the very end
        if (eligible.size < 3) return eligible + far
        val maxSim = eligible.maxOf { sims[it] }
        val near = ArrayDeque(eligible.filter { sims[it] >= maxSim * 0.5 })
        val stretch = ArrayDeque(eligible.filter { sims[it] < maxSim * 0.5 })
        val every = if (appetite >= 0.55) 2 else 3   // every Nth slot is a stretch
        val out = ArrayList<Int>(order.size)
        while (near.isNotEmpty() || stretch.isNotEmpty()) {
            val wantStretch = (out.size + 1) % every == 0
            out += when {
                wantStretch && stretch.isNotEmpty() -> stretch.removeFirst()
                near.isNotEmpty() -> near.removeFirst()
                else -> stretch.removeFirst()
            }
        }
        return out + far
    }

    private const val FAR_BELOW = 0.08

    internal fun compose(order: List<Int>, novel: Set<Int>, adjacent: Set<Int>, target: Double): List<Int> {
        val novelQ = ArrayDeque(order.filter { it in adjacent } + order.filter { it in novel && it !in adjacent })
        val famQ = ArrayDeque(order.filter { it !in novel })
        val out = ArrayList<Int>(order.size)
        var novelCount = 0.0
        repeat(order.size) {
            val wantNovel = when {
                novelQ.isEmpty() -> false
                famQ.isEmpty() -> true
                else -> (novelCount + 0.5) / (out.size + 1.0) < target
            }
            if (wantNovel) { out += novelQ.removeFirst(); novelCount += 1 } else out += famQ.removeFirst()
        }
        return out
    }

    /** Avoid A,A,A,A: defer an item that would extend a same-primary-artist run past [maxRun]. */
    internal fun spread(order: List<Int>, inputs: List<PulseCandidate>, maxRun: Int): List<Int> {
        val pending = ArrayDeque(order)
        val out = ArrayList<Int>(order.size)
        val counts = HashMap<String, Int>()
        fun key(i: Int) = inputs[i].artistIds.firstOrNull().orEmpty()
        while (pending.isNotEmpty()) {
            var pick = -1
            for ((pos, i) in pending.withIndex()) {
                val k = key(i)
                var run = 0
                for (j in out.indices.reversed()) { if (key(out[j]) == k && k.isNotEmpty()) run++ else break }
                val budget = if (maxRun == 1) PulseParams.MAX_ARTIST_PER_RADIO else PulseParams.MAX_ARTIST_PER_SHELF
                if (k.isEmpty() || (run < maxRun && (counts[k] ?: 0) < budget)) { pick = pos; break }
            }
            if (pick < 0) {
                // If diversity would make the row too short, relax total artist budget before dropping content.
                pick = pending.indexOfFirst { key(it).isEmpty() || (counts[key(it)] ?: 0) < 5 }.takeIf { it >= 0 } ?: 0
            }
            val chosen = pending.removeAt(pick)
            out += chosen
            key(chosen).takeIf { it.isNotEmpty() }?.let { counts[it] = (counts[it] ?: 0) + 1 }
        }
        return out
    }
}
