package com.devxrohan.smusic
import com.devxrohan.smusic.spotify.Spotify
import com.devxrohan.smusic.spotify.SpotifyPlaylistEdits
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SpotifyPlaylistAppendTest {
    @Test fun appendTargetsOriginalPlaylistAndPreservesIntentionalDuplicates() {
        val body = Spotify.playlistAdditionVariables("original", listOf("spotify:track:a", "spotify:track:a"))
        assertEquals("spotify:playlist:original", body["playlistUri"]!!.jsonPrimitive.content)
        assertEquals(2, body["playlistItemUris"]!!.jsonArray.size)
        assertEquals("BOTTOM_OF_PLAYLIST", body["newPosition"]!!.jsonObject["moveType"]!!.jsonPrimitive.content)
        assertEquals("https://api-partner.spotify.com/pathfinder/v1/query", Spotify.graphQlEndpoint("addToPlaylist"))
    }
    @Test fun timeoutRecoveryRequiresTheCompleteExpectedAppendAtTheRecordedOffset() {
        assertTrue(SpotifyPlaylistEdits.alreadyApplied(listOf("a", "a", "later"), listOf("a", "a")))
        assertFalse(SpotifyPlaylistEdits.alreadyApplied(listOf("a"), listOf("a", "a")))
        assertFalse(SpotifyPlaylistEdits.alreadyApplied(listOf("other", "a"), listOf("a")))
    }
    @Test fun pendingAppendRetainsDestinationOrderAndLocalRowsAcrossRestart() {
        val operation = SpotifyPlaylistEdits.Addition("request", "playlist", listOf("yt1", "yt1"), listOf("sp1", "sp1"), 42, listOf(1, 2))
        assertEquals(operation, Json.decodeFromString<SpotifyPlaylistEdits.Addition>(Json.encodeToString(operation)))
    }
}
