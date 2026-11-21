package com.devxrohan.smusic

import com.devxrohan.smusic.pulse.*
import org.junit.Assert.*
import org.junit.Test

class PulseRankingTest {
    private val now = 1800000000000L
    private fun song(id: String, artist: String = "artist") = PulseCandidate(id, PulseKind.SONG, listOf(artist))
    @Test fun explicitNegativeFeedbackAndBlocksWinEvenWithNoTasteHistory() {
        val state = PulseState()
        state.notForMeSongs["rejected"] = now; state.blockedArtists.add("hidden")
        val inputs = listOf(song("rejected"), song("blocked", "hidden"), song("okay"))
        assertEquals(listOf(2), PulseRanker.rank(state, inputs, PulseSurface.RADIO, now))
    }
    @Test fun duplicateCandidatesAreNotRepeatedAndExpiredFeedbackCanReturn() {
        val state = PulseState(); state.notForMeSongs["old"] = now - PulseParams.NOT_FOR_ME_MS - 1
        val inputs = listOf(song("old"), song("old"), song("other"))
        val result = PulseRanker.rank(state, inputs, PulseSurface.KEEP_LISTENING, now)
        assertEquals(2, result.size); assertEquals(setOf("old", "other"), result.map { inputs[it].id }.toSet())
    }
    @Test fun completedRecentSongGetsFatigueAgainstEquivalentOlderSong() {
        val state = PulseState()
        state.songs["recent"] = SongStats(plays = 5, completions = 5, lastPlayed = now)
        state.songs["older"] = SongStats(plays = 5, completions = 5, lastPlayed = now - 86400000)
        val result = PulseRanker.rank(state, listOf(song("recent"), song("older")), PulseSurface.RADIO, now)
        assertEquals(1, result.first())
    }
    @Test fun manualDiscoveryChoiceDoesNotOverwriteLearnedAppetite() {
        val state = PulseState(); state.discoveryAppetite = .25; state.discoveryPreference = .60
        assertEquals(.60, state.recommendationAppetite(), .0001)
        state.discoveryPreference = null; assertEquals(.25, state.recommendationAppetite(), .0001)
    }
}
