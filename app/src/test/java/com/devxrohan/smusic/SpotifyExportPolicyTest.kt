package com.devxrohan.smusic

import com.devxrohan.smusic.spotify.SpotifyExportPolicy
import com.devxrohan.smusic.spotify.SpotifyOutboundSync
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class SpotifyExportPolicyTest {
    @Test fun acknowledgementKeepsAnUnlikeQueuedDuringAnOlderLikeRequest() {
        assertEquals(mapOf("song" to false), SpotifyExportPolicy.acknowledge(mapOf("song" to false), mapOf("song" to true)))
        assertTrue(SpotifyExportPolicy.acknowledge(mapOf("song" to true), mapOf("song" to true)).isEmpty())
    }
    @Test fun explicitUnlikeOverridesTheCurrentLikedSnapshot() {
        assertEquals(mapOf("song" to false), SpotifyExportPolicy.pendingLikes(setOf("song"), mapOf("song" to false), mapOf("song" to true)))
        assertTrue(SpotifyExportPolicy.pendingLikes(setOf("song"), emptyMap(), mapOf("song" to true)).isEmpty())
    }
    @Test fun unresolvedTrackPreventsReplacingAnyExistingPlaylistCopy() {
        assertNull(SpotifyExportPolicy.playlistUris(listOf("first", "missing"), mapOf("first" to "sp1")))
        assertEquals(listOf("spotify:track:sp1", "spotify:track:sp2", "spotify:track:sp1"), SpotifyExportPolicy.playlistUris(listOf("first", "second", "first"), mapOf("first" to "sp1", "second" to "sp2")))
    }
    @Test fun newPlaylistCopiesAreAlwaysPrivate() {
        val body = SpotifyExportPolicy.privatePlaylistBody("A \"quoted\" playlist")
        assertFalse(body.getValue("public").jsonPrimitive.boolean)
        assertEquals("A \"quoted\" playlist", body.getValue("name").jsonPrimitive.content)
    }
    @Test fun playlistFingerprintIncludesItsNameAndSongOrder() {
        val first = SpotifyOutboundSync.fingerprint("Mix", listOf("one", "two"))
        assertEquals(first, SpotifyOutboundSync.fingerprint("Mix", listOf("one", "two")))
        assertNotEquals(first, SpotifyOutboundSync.fingerprint("Mix", listOf("two", "one")))
        assertNotEquals(first, SpotifyOutboundSync.fingerprint("Other", listOf("one", "two")))
    }
}
