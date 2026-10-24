package com.devxrohan.smusic.pulse

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Continuous "sound space" for artists, used instead of (unavailable) genre labels.
 *
 * Every artist gets a deterministic pseudo-random base vector (random indexing). Vectors are then smoothed
 * over the listening graph (own co-listening + related-artist links from the music source) with a
 * personalised-random-walk style propagation, so artists that share neighbours, even 2-3 hops away, end up
 * close together. Cosine similarity in this space generalises: "close to what you play", "bridges two of
 * your tastes", "same neighbourhood but a bit different". Nothing is stored; it's rebuilt from the graph.
 */
internal class Embedding(val vec: Map<String, FloatArray>) {
    fun has(id: String) = vec.containsKey(id)
    fun of(id: String): FloatArray? = vec[id]

    companion object { val EMPTY = Embedding(emptyMap()) }
}

internal class TasteVectors(
    val long: FloatArray?,
    val short: FloatArray?,
    val session: FloatArray?,
    val time: FloatArray?,
)

internal object PulseEmbedding {
    const val DIM = 96
    private const val ROUNDS = 3
    private const val ALPHA = 0.6f
    private const val RELATED_WEIGHT = 0.5
    /** Expected |cosine| between unrelated random vectors at DIM; subtracted so noise never counts as taste. */
    private const val NOISE_FLOOR = 0.12f
    private const val MAX_NODES = 2500

    fun base(id: String): FloatArray {
        var x = id.hashCode().toLong() * 2685821657736338717L + 0x9E3779B97F4A7C15uL.toLong()
        val v = FloatArray(DIM)
        for (i in 0 until DIM) {
            x = x xor (x shl 13); x = x xor (x ushr 7); x = x xor (x shl 17)
            v[i] = if (x and 1L == 0L) 1f else -1f
        }
        return normalize(v)
    }

    /** Cached per graph version; recomputed only after the graph actually changes. */
    fun get(state: PulseState): Embedding {
        state.embCache?.let { (ver, e) -> if (ver == state.graphVersion) return e }
        val e = compute(state)
        state.embCache = state.graphVersion to e
        return e
    }

    private fun compute(state: PulseState): Embedding {
        if (state.artistCooc.isEmpty() && state.relatedEdges.isEmpty()) return Embedding.EMPTY
        val adj = HashMap<String, MutableMap<String, Double>>()
        fun edge(key: String, w: Double) {
            val i = key.indexOf('|'); if (i <= 0) return
            val a = key.substring(0, i); val b = key.substring(i + 1)
            val ww = ln(1.0 + w) // damp heavy repeat pairs so one pair can't dominate the space
            adj.getOrPut(a) { HashMap() }.merge(b, ww, Double::plus)
            adj.getOrPut(b) { HashMap() }.merge(a, ww, Double::plus)
        }
        state.artistCooc.forEach { (k, v) -> edge(k, v) }
        state.relatedEdges.forEach { (k, v) -> edge(k, v * RELATED_WEIGHT) }
        val nodes = adj.keys.sorted().take(MAX_NODES)
        val nodeSet = nodes.toHashSet()
        val baseV = nodes.associateWith { base(it) }
        var cur: Map<String, FloatArray> = baseV
        repeat(ROUNDS) {
            val next = HashMap<String, FloatArray>(nodes.size)
            for (n in nodes) {
                val nbrs = adj[n]!!.filterKeys { it in nodeSet }
                val total = nbrs.values.sum()
                val acc = FloatArray(DIM)
                if (total > 0.0) {
                    for ((nb, w) in nbrs) {
                        val c = cur[nb]!!; val f = (w / total).toFloat()
                        for (i in 0 until DIM) acc[i] += f * c[i]
                    }
                }
                val b = baseV[n]!!
                val out = FloatArray(DIM) { (1 - ALPHA) * b[it] + ALPHA * acc[it] }
                next[n] = normalize(out)
            }
            cur = next
        }
        return Embedding(cur)
    }

    fun normalize(v: FloatArray): FloatArray {
        var s = 0.0
        for (x in v) s += x * x
        val n = sqrt(s).toFloat()
        if (n > 0f) for (i in v.indices) v[i] /= n
        return v
    }

    fun cos(a: FloatArray, b: FloatArray): Float {
        var d = 0f
        for (i in a.indices) d += a[i] * b[i]
        return d
    }

    private fun weighted(emb: Embedding, w: Map<String, Double>): FloatArray? {
        val acc = FloatArray(DIM); var any = false
        for ((id, wt) in w) {
            val e = emb.of(id) ?: continue
            if (wt <= 0.0) continue
            for (i in 0 until DIM) acc[i] += (wt * e[i]).toFloat()
            any = true
        }
        return if (any) normalize(acc) else null
    }

    fun tasteVectors(state: PulseState, emb: Embedding, now: Long): TasteVectors {
        if (emb.vec.isEmpty()) return TasteVectors(null, null, null, null)
        val long = weighted(emb, state.artists.mapValues { (_, a) -> a.score * ln(1.0 + a.plays) })
        val short = weighted(emb, state.recentRotation)
        val sess = HashMap<String, Double>()
        var wgt = 1.0
        for (id in state.sessionRecent.asReversed()) { sess.merge(id, wgt, Double::plus); wgt *= 0.7 }
        val session = if (state.sessionLastTs != 0L && now - state.sessionLastTs <= PulseParams.SESSION_GAP_MS)
            weighted(emb, sess) else null
        val time = state.timeBuckets[PulseTimeBucket.fromTimestamp(now)]
            ?.takeIf { it.values.sum() >= 20.0 }?.let { weighted(emb, it) }
        return TasteVectors(long, short, session, time)
    }

    /** 0..1 "how close is this to what the user is into", noise-corrected; unknown artists score 0. */
    fun similarity(emb: Embedding, tv: TasteVectors, artistIds: List<String>): Double {
        var best = 0.0
        for (id in artistIds) {
            val e = emb.of(id) ?: continue
            fun part(t: FloatArray?) = if (t == null) 0f else maxOf(0f, cos(e, t) - NOISE_FLOOR) / (1f - NOISE_FLOOR)
            val l = part(tv.long); val sh = part(tv.short); val se = part(tv.session); val ti = part(tv.time)
            val s = if (tv.session != null) 0.30 * l + 0.20 * sh + 0.40 * se + 0.10 * ti
            else 0.55 * l + 0.35 * sh + 0.10 * ti
            if (s > best) best = s
        }
        return best.coerceIn(0.0, 1.0)
    }

    /** Similarity of one artist to a specific seed (for radio from a chosen song/artist). */
    fun similarityToSeed(emb: Embedding, seedArtistIds: List<String>, artistIds: List<String>): Double {
        val seeds = seedArtistIds.mapNotNull { emb.of(it) }
        if (seeds.isEmpty()) return 0.0
        var best = 0f
        for (id in artistIds) {
            val e = emb.of(id) ?: continue
            for (s in seeds) best = maxOf(best, (cos(e, s) - NOISE_FLOOR) / (1f - NOISE_FLOOR))
        }
        return best.toDouble().coerceIn(0.0, 1.0)
    }
}
