package com.devxrohan.smusic.pulse

/** Keep provider proximity to the seed ahead of long-term taste during radio playback. */
internal object PulseRadioOrder {
    fun <T> rankNearby(items: List<T>, rank: (List<T>) -> List<T>): List<T> {
        val positions = rank(items).withIndex().associate { it.value to it.index }
        return items.chunked(4).flatMap { group ->
            group.filter { it in positions }.sortedBy { positions.getValue(it) }
        }
    }
}
