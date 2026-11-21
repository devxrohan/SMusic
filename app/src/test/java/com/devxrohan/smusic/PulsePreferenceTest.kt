package com.devxrohan.smusic

import com.devxrohan.smusic.pulse.*
import org.junit.Assert.*
import org.junit.Test

class PulsePreferenceTest {
    private val now = 1_800_000_000_000L
    private fun candidate(id: String, artist: String) = PulseCandidate(id, PulseKind.SONG, listOf(artist))
    private fun request(state: PulseState, id: String = "requested", artist: String = "chosen") {
        PulsePreferences.apply(state, PulseFeedbackTrack(id, listOf(artist), listOf("Chosen artist"), "Requested song"), now)
    }

    @Test fun explicitFeedbackDoesNotFabricatePlaysOrLikes() {
        val state = PulseState()
        request(state)
        assertEquals(0, state.totalPlays)
        assertTrue(state.songs.isEmpty())
        assertEquals(0, state.artists.getValue("chosen").plays)
        assertFalse(state.artists.getValue("chosen").liked)
        assertTrue(state.effectiveShare(now) > 0.0)
    }

    @Test fun requestedSongAndArtistOutrankEquivalentUnknownCandidates() {
        val state = PulseState()
        request(state)
        val candidates = listOf(candidate("other", "unrelated"), candidate("sibling", "chosen"), candidate("requested", "chosen"))
        val order = PulseRanker.rank(state, candidates, PulseSurface.KEEP_LISTENING, now)
        assertEquals(listOf(2, 1, 0), order)
        assertTrue(PulseRanker.relevance(state, candidates[1], PulseSurface.DISCOVER, now) > .08)
    }

    @Test fun relatedArtistsReceiveWeakerEvidenceBasedInterest() {
        val state = PulseState()
        request(state)
        state.relatedEdges[pairKey("chosen", "nearby")] = .4
        val weights = PulsePreferences.weights(state, now)
        assertTrue(weights.artists.getValue("nearby") > 0.0)
        assertTrue(weights.artists.getValue("nearby") < weights.artists.getValue("chosen"))
        assertFalse(weights.artists.containsKey("unrelated"))
    }

    @Test fun repeatedClicksAreIdempotentAndDoNotCreateExtraConfidence() {
        val state = PulseState()
        request(state)
        val before = PulsePreferences.weights(state, now)
        repeat(10) { request(state) }
        assertEquals(1, state.positiveFeedback.size)
        assertEquals(before, PulsePreferences.weights(state, now))
        assertEquals(0, state.totalPlays)
    }

    @Test fun newestExplicitFeedbackWinsWithoutOverridingArtistBlocks() {
        val state = PulseState()
        state.notForMeSongs["requested"] = now - 1
        request(state)
        assertFalse(state.notForMeSongs.containsKey("requested"))
        PulseLearn.applyNotForMe(state, "requested", listOf("chosen"), now + 1)
        assertFalse(state.positiveFeedback.containsKey("requested"))
        state.blockedArtists.add("chosen")
        request(state)
        assertTrue(state.blockedArtists.contains("chosen"))
        assertTrue(PulseRanker.rank(state, listOf(candidate("requested", "chosen")), PulseSurface.RADIO, now).isEmpty())
    }

    @Test fun oldRequestsDecayAndEventuallyExpire() {
        val state = PulseState()
        request(state)
        val initial = PulsePreferences.weights(state, now).songs.getValue("requested")
        val later = PulsePreferences.weights(state, now + 30L * 86_400_000).songs.getValue("requested")
        assertTrue(later < initial)
        assertTrue(PulsePreferences.weights(state, now + 91L * 86_400_000).songs.isEmpty())
        assertEquals(0.0, state.effectiveShare(now + 91L * 86_400_000), .0001)
    }

    @Test fun preferenceStorageIsBoundedToLatestRequests() {
        val state = PulseState()
        repeat(510) { PulsePreferences.apply(state, PulseFeedbackTrack("song$it", emptyList()), now + it) }
        assertEquals(500, state.positiveFeedback.size)
        assertFalse(state.positiveFeedback.containsKey("song0"))
        assertTrue(state.positiveFeedback.containsKey("song509"))
    }

    @Test fun explicitTasteEntersRadioBeforeUnconnectedCandidates() {
        val state = PulseState()
        request(state)
        val inputs = listOf(candidate("a", "other"), candidate("requested", "chosen"),
            candidate("b", "else"), candidate("c", "another"))
        assertEquals(1, PulseRanker.rank(state, inputs, PulseSurface.RADIO, now).first())
    }
}
