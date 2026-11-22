package com.devxrohan.smusic

import com.devxrohan.smusic.pulse.PulseRadioOrder
import com.devxrohan.smusic.pulse.PulseCandidate
import com.devxrohan.smusic.pulse.PulseKind
import com.devxrohan.smusic.pulse.PulseRanker
import com.devxrohan.smusic.pulse.PulseState
import com.devxrohan.smusic.pulse.PulseSurface
import com.devxrohan.smusic.pulse.PulsePreferences
import com.devxrohan.smusic.pulse.PulseFeedbackTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class PulseRadioOrderTest {
    @Test fun tasteCannotPromoteDistantCandidatesAheadOfTheSeedNeighborhood() {
        val provider = (1..12).toList()
        val ranked = PulseRadioOrder.rankNearby(provider) { it.reversed() }
        assertEquals(listOf(4, 3, 2, 1, 8, 7, 6, 5, 12, 11, 10, 9), ranked)
    }

    @Test fun blockedCandidatesAreNotReintroducedWhenAGroupBecomesEmpty() {
        val ranked = PulseRadioOrder.rankNearby((1..9).toList()) { group -> group.filter { it > 4 } }
        assertEquals((5..9).toList(), ranked)
    }

    @Test fun shortColdStartQueuesKeepAllAvailableCandidates() {
        assertEquals(listOf("one", "two"), PulseRadioOrder.rankNearby(listOf("one", "two")) { it })
        assertEquals(emptyList<String>(), PulseRadioOrder.rankNearby(emptyList<String>()) { it })
    }

    @Test fun rankingRunsOnceForTheWholePage() {
        var calls = 0
        PulseRadioOrder.rankNearby((1..100).toList()) { calls++; it }
        assertEquals(1, calls)
    }

    @Test fun realRankerCannotRestoreBlockedArtistsInASeedAnchoredPage() {
        val state = PulseState().apply { blockedArtists.add("blocked") }
        val tracks = (1..9).map { PulseCandidate("song$it", PulseKind.SONG,
            listOf(if (it <= 4) "blocked" else "seed-artist")) }
        val result = PulseRadioOrder.rankNearby(tracks) { candidates ->
            PulseRanker.rank(state, candidates, PulseSurface.RADIO, 1_800_000_000_000L,
                seedArtistIds = listOf("seed-artist"), preserveProviderCandidates = true).map { candidates[it] }
        }
        assertEquals((5..9).map { "song$it" }.toSet(), result.map { it.id }.toSet())
    }

    @Test fun longTermPreferencesCannotDiscardAnUnfamiliarSeedNeighborhood() {
        val now = 1_800_000_000_000L
        val state = PulseState()
        val tracks = (1..12).map { PulseCandidate("song$it", PulseKind.SONG,
            listOf(if (it <= 4) "new-seed-artist" else "old-favorite-artist")) }
        tracks.drop(4).forEach {
            PulsePreferences.apply(state, PulseFeedbackTrack(it.id, it.artistIds), now)
        }
        val result = PulseRadioOrder.rankNearby(tracks) { candidates ->
            PulseRanker.rank(state, candidates, PulseSurface.RADIO, now,
                seedArtistIds = listOf("new-seed-artist"), preserveProviderCandidates = true)
                .map { candidates[it] }
        }
        assertEquals(tracks.take(4).toSet(), result.take(4).toSet())
        assertEquals(tracks.size, result.size)
    }
}
