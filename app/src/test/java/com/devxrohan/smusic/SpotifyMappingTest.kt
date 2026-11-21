package com.devxrohan.smusic

import com.devxrohan.smusic.spotify.Spotify
import com.devxrohan.smusic.lyrics.artistForLyricsSearch
import com.devxrohan.smusic.lyrics.forLyricsSearch
import kotlinx.serialization.json.jsonObject
import com.devxrohan.smusic.spotify.SpotifyMapper
import com.devxrohan.smusic.spotify.models.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SpotifyMappingTest {
    @Test fun outboundQueryExcludesPlayCounts() {
        val artists = listOf("133M plays", "D Naveen", "Shiva Choudhary")
            .map { it.artistForLyricsSearch() }.filter { it.isNotBlank() }
        assertEquals("D Naveen", artists.first())
        assertEquals("80 Lakh", "80 Lakh (feat. Sweta Chauhan)".forLyricsSearch())
    }

    @Test fun searchAcceptsBothSpotifyResponseShapes() {
        val track = """{"__typename":"Track","uri":"spotify:track:abc","name":"My Song"}"""
        val current = Json.parseToJsonElement("""{"tracks":{"items":[{"track":$track}]}}""").jsonObject
        val wrapped = Json.parseToJsonElement("""{"tracksV2":{"items":[{"item":{"data":$track}}]}}""").jsonObject
        assertEquals("abc", Spotify.parseSearchTracks(current).single().id)
        assertEquals(Spotify.parseSearchTracks(current), Spotify.parseSearchTracks(wrapped))
    }

    @Test fun searchUsesTrackMetadata() {
        val track = SpotifyTrack(name = "My Song", artists = listOf(SpotifySimpleArtist(name = "Artist")))
        assertEquals("Artist My Song", SpotifyMapper.buildSearchQuery(track))
    }

    @Test fun exactRecordingOutranksDifferentTitleAndDuration() {
        val exact = SpotifyMapper.matchScore("My Song", "Artist", 180000, "My Song", "Artist", 180)
        val other = SpotifyMapper.matchScore("My Song", "Artist", 180000, "Something Else", "Other Artist", 360)
        assertTrue(exact > 0.95)
        assertTrue(exact > other)
    }

    @Test fun playlistCacheRetainsOwnerArtworkAndTrackCount() {
        val playlist = SpotifyPlaylist(id = "playlist", name = "Saved songs", images = listOf(SpotifyImage(url = "https://example.com/cover", width = 300)),
            owner = SpotifyPlaylistOwner(displayName = "Listener"), tracks = SpotifyPlaylistTracksRef(total = 42))
        val json = Json.encodeToString(SpotifyPlaylist.serializer(), playlist)
        assertEquals(playlist, Json.decodeFromString(SpotifyPlaylist.serializer(), json))
    }

    @Test fun thumbnailPrefersMediumArtwork() {
        val playlist = SpotifyPlaylist(images = listOf(SpotifyImage(url = "large", width = 640), SpotifyImage(url = "medium", width = 300)))
        assertEquals("medium", SpotifyMapper.getPlaylistThumbnail(playlist))
        assertNull(SpotifyMapper.getTrackThumbnail(SpotifyTrack()))
    }
}
