package com.devxrohan.smusic

import com.devxrohan.smusic.pulse.*
import org.junit.Assert.*
import org.junit.Test

class PulseControlsTest {
    private val now = 1_800_000_000_000L

    @Test fun requestedArtistDoesNotReplaceListeningBanner() {
        val state = PulseState()
        state.artists["heard"] = ArtistAffinity(name = "Heard", plays = 10, score = .4)
        PulsePreferences.apply(state, PulseFeedbackTrack("new", listOf("requested"), listOf("Requested")), now)
        assertEquals("requested", state.topArtists(1, now).first().first)
        assertEquals("heard", PulseControlPolicy.listeningArtistId(state, now))
    }

    @Test fun feedbackWithoutListeningDoesNotInventBanner() {
        val state = PulseState()
        PulsePreferences.apply(state, PulseFeedbackTrack("new", listOf("requested"), listOf("Requested")), now)
        assertNull(PulseControlPolicy.listeningArtistId(state, now))
    }

    @Test fun snapshotExcludesExpiredFeedbackAndRetainsTemporaryArtistCooldowns() {
        val state = PulseState()
        state.notForMeSongs["old"] = now - PulseParams.NOT_FOR_ME_MS - 1
        state.notForMeSongs["current"] = now - 1
        state.dislikedArtists["temporary"] = now
        state.blockedArtists.add("blocked")
        val entries = PulseControlPolicy.snapshot(state, emptyMap(), now).entries
        assertEquals(listOf("current"), entries.filter { it.kind == PulseControlKind.LESS }.map { it.id })
        assertEquals(setOf("blocked", "temporary"), entries.filter { it.kind == PulseControlKind.ARTIST }.map { it.id }.toSet())
    }

    @Test fun savedFeedbackTitlesAreAvailableWithoutLibraryRows() {
        val state = PulseState()
        state.notForMeSongs["remote"] = now
        state.feedbackTracks["remote"] = PulseFeedbackTrack("remote", listOf("a"), listOf("Artist"), "Remote song")
        val item = PulseControlPolicy.snapshot(state, emptyMap(), now).entries.single()
        assertEquals("Remote song", item.title)
        assertEquals("Artist", item.subtitle)
    }

    @Test fun clearingFeedbackPreservesListeningLikesAndExplicitArtistBlocks() {
        val state = PulseState()
        state.songs["s"] = SongStats(artistIds = listOf("a"), plays = 7, liked = true)
        state.notForMeSongs["s"] = now
        state.dislikedArtists["a"] = now
        state.blockedArtists.add("a")
        PulseControlPolicy.clearFeedback(state, "s", now)
        assertFalse(state.notForMeSongs.containsKey("s"))
        assertFalse(state.dislikedArtists.containsKey("a"))
        assertTrue("a" in state.blockedArtists)
        assertEquals(7, state.songs.getValue("s").plays)
        assertTrue(state.songs.getValue("s").liked)
    }

    @Test fun skipAndRecentSignalsAreSeparateFromExplicitFeedback() {
        val state = PulseState()
        state.songs["s"] = SongStats(title = "Song", plays = 1, skips = 1, lastPlayed = now - 1000)
        val kinds = PulseControlPolicy.snapshot(state, emptyMap(), now).entries.map { it.kind }
        assertTrue(PulseControlKind.SKIPPED in kinds)
        assertTrue(PulseControlKind.RECENT in kinds)
        state.notForMeSongs["s"] = now
        assertEquals(listOf(PulseControlKind.LESS), PulseControlPolicy.snapshot(state, emptyMap(), now).entries.map { it.kind })
    }
    @Test fun chosenArtistBoostsRankingWithoutInventingHistoryOrLikes() {
        val state = PulseState()
        PulsePreferences.selectArtist(state, "chosen", PulseArtistChoice("Chosen artist", "https://example.com/cover", now))
        assertEquals(0, state.totalPlays)
        assertTrue(state.songs.isEmpty())
        assertFalse(state.artists.getValue("chosen").liked)
        assertEquals(0, state.artists.getValue("chosen").plays)
        assertNull(PulseControlPolicy.listeningArtistId(state, now))
        assertEquals(.9, PulsePreferences.directArtists(state, now).getValue("chosen"), .001)
        val entries = PulseControlPolicy.snapshot(state, emptyMap(), now).entries
        assertEquals("https://example.com/cover", entries.first { it.kind == PulseControlKind.CHOSEN }.thumbnailUrl)
        PulsePreferences.selectArtist(state, "chosen", null)
        assertFalse(PulsePreferences.directArtists(state, now).containsKey("chosen"))
    }

    @Test fun choosingArtistDoesNotOverrideAnExplicitBlock() {
        val state = PulseState()
        state.blockedArtists.add("chosen")
        PulsePreferences.selectArtist(state, "chosen", PulseArtistChoice("Chosen artist", timestamp = now))
        assertFalse(PulsePreferences.directArtists(state, now).containsKey("chosen"))
        assertFalse(PulsePreferences.hasActive(state, now))
    }

    @Test fun feedbackSnapshotPreservesSongArtwork() {
        val state = PulseState()
        state.notForMeSongs["remote"] = now
        val track = PulseFeedbackTrack("remote", emptyList(), title = "Song", thumbnailUrl = "https://example.com/art")
        state.feedbackTracks["remote"] = track
        assertEquals(track.thumbnailUrl, PulseControlPolicy.snapshot(state, emptyMap(), now).entries.single().thumbnailUrl)
    }

}
