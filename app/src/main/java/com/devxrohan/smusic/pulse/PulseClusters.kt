package com.devxrohan.smusic.pulse

import kotlin.math.ln

/**
 * Data-driven "taste groups". SMusic has no genre metadata, so instead of faking genres Pulse finds
 * communities of artists that the user's own listening (co-listening sessions) and the music source's
 * related-artist links tie together, then measures how much of the user's taste lives in each group.
 * Deterministic label propagation: same input, same groups.
 */
internal class TasteMap(
    val labelOf: Map<String, Int>,
    /** Cluster taste mass normalised to 0..1 (1 = the user's biggest taste group). */
    val mass: Map<Int, Double>,
    /** Members of each cluster, strongest affinity first. */
    val members: Map<Int, List<String>>,
) {
    fun massOf(artistId: String): Double = labelOf[artistId]?.let { mass[it] } ?: 0.0

    companion object {
        val EMPTY = TasteMap(emptyMap(), emptyMap(), emptyMap())
    }
}

internal object PulseClusters {
    private const val ITERATIONS = 10
    private const val RELATED_WEIGHT = 0.5

    fun compute(state: PulseState): TasteMap {
        if (state.artistCooc.isEmpty() && state.relatedEdges.isEmpty()) return TasteMap.EMPTY
        val adj = HashMap<String, MutableMap<String, Double>>()
        fun edge(key: String, w: Double) {
            val i = key.indexOf('|')
            if (i <= 0) return
            val a = key.substring(0, i); val b = key.substring(i + 1)
            adj.getOrPut(a) { HashMap() }.merge(b, w, Double::plus)
            adj.getOrPut(b) { HashMap() }.merge(a, w, Double::plus)
        }
        state.artistCooc.forEach { (k, v) -> edge(k, v) }
        state.relatedEdges.forEach { (k, v) -> edge(k, v * RELATED_WEIGHT) }

        val nodes = adj.keys.sorted()
        val label = HashMap<String, Int>().also { m -> nodes.forEachIndexed { i, n -> m[n] = i } }
        repeat(ITERATIONS) {
            var changed = false
            for (n in nodes) {
                val tally = HashMap<Int, Double>()
                adj[n]!!.forEach { (nb, w) -> tally.merge(label[nb]!!, w, Double::plus) }
                val best = tally.entries.maxWithOrNull(compareBy<Map.Entry<Int, Double>> { it.value }.thenByDescending { it.key })?.key
                if (best != null && best != label[n]) { label[n] = best; changed = true }
            }
            if (!changed) return@repeat
        }
        val groups = label.entries.groupBy({ it.value }, { it.key })
            .filter { it.value.size >= 2 }
        val mass = HashMap<Int, Double>()
        val members = HashMap<Int, List<String>>()
        groups.forEach { (lab, ids) ->
            mass[lab] = ids.sumOf { id -> state.artists[id]?.let { it.score * ln(1.0 + it.plays) } ?: 0.0 }
            members[lab] = ids.sortedByDescending { state.artists[it]?.score ?: 0.0 }
        }
        val top = mass.values.maxOrNull() ?: 0.0
        if (top <= 0.0) return TasteMap.EMPTY
        mass.keys.toList().forEach { mass[it] = mass[it]!! / top }
        val keep = groups.keys
        return TasteMap(label.filterValues { it in keep }, mass, members)
    }
}
