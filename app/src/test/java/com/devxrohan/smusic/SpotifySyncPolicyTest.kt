package com.devxrohan.smusic

import com.devxrohan.smusic.spotify.SpotifyLibraryBridge
import com.devxrohan.smusic.spotify.SpotifySyncPolicy
import org.junit.Assert.*
import org.junit.Test

class SpotifySyncPolicyTest {
    @Test fun disconnectedProvidersAreHiddenAndAnUnavailableSelectionFallsBackToAll() {
        val available = SpotifySyncPolicy.availableLikeSources(false, false)
        assertEquals(setOf("all", "smusic"), available)
        assertEquals("all", SpotifySyncPolicy.activeLikeSource("spotify", available))
        assertEquals("all", SpotifySyncPolicy.activeLikeSource("youtube", available))
        assertEquals("smusic", SpotifySyncPolicy.activeLikeSource("smusic", available))
    }
    @Test fun onlyConnectedProviderFiltersAreAvailable() {
        assertEquals(setOf("all", "smusic", "spotify"), SpotifySyncPolicy.availableLikeSources(true, false))
        assertEquals(setOf("all", "smusic", "youtube"), SpotifySyncPolicy.availableLikeSources(false, true))
        assertEquals(4, SpotifySyncPolicy.availableLikeSources(true, true).size)
    }
    @Test fun allLikesMergeThreeSourcesWithoutDuplicates() {
        assertEquals(setOf("local", "shared", "spotify", "youtube"),
            SpotifySyncPolicy.likedIds(setOf("local", "shared"), setOf("shared", "spotify"), setOf("youtube"), "all"))
    }

    @Test fun sourceFiltersDoNotLeakOtherLikes() {
        assertEquals(setOf("spotify"), SpotifySyncPolicy.likedIds(setOf("local"), setOf("spotify"), setOf("youtube"), "spotify"))
        assertEquals(setOf("youtube"), SpotifySyncPolicy.likedIds(setOf("local"), setOf("spotify"), setOf("youtube"), "youtube"))
    }

    @Test fun explicitSMusicLikesRemainVisibleEvenWhenAlsoLikedOnYouTube() {
        assertEquals(setOf("shared", "local"), SpotifySyncPolicy.likedIds(setOf("shared", "local"), setOf("spotify"), setOf("shared"), "smusic", setOf("shared", "local")))
        assertFalse(SpotifySyncPolicy.likedIds(emptySet(), setOf("spotify"), setOf("youtube"), "smusic", emptySet()).contains("spotify"))
    }

    @Test fun youtubeImportsDoNotBecomeSMusicLikesWithoutAnAppLike() {
        assertTrue(SpotifySyncPolicy.likedIds(setOf("youtube"), emptySet(), setOf("youtube"), "smusic").isEmpty())
    }

    @Test fun playlistSongBlockDoesNotDependOnItsRowPosition() {
        val first = SpotifySyncPolicy.playlistTrackToken("SP:playlist", 12, "spotify:song:3")!!
        val moved = SpotifySyncPolicy.playlistTrackToken("SP:playlist", 99, "spotify:song:27")!!
        val a = SpotifySyncPolicy.parsePlaylistTrackToken(first)!!
        val b = SpotifySyncPolicy.parsePlaylistTrackToken(moved)!!
        assertEquals(SpotifySyncPolicy.playlistTrackBlockKey(a.playlistId, a.spotifyId), SpotifySyncPolicy.playlistTrackBlockKey(b.playlistId, b.spotifyId))
        assertNotEquals(SpotifySyncPolicy.playlistTrackBlockKey("SP:other", a.spotifyId), SpotifySyncPolicy.playlistTrackBlockKey(a.playlistId, a.spotifyId))
        assertNull(SpotifySyncPolicy.playlistTrackToken("LP_local", 12, "spotify:song:3"))
        assertNull(SpotifySyncPolicy.parsePlaylistTrackToken("SP:playlist|invalid|song"))
    }

    @Test fun deletionBlocksOnlyMatchingKindAndLocalItem() {
        val links = mapOf("track:one" to "yt1", "track:two" to "yt1", "artist:one" to "yt1", "track:other" to "yt2")
        assertEquals(setOf("track:one", "track:two"), SpotifySyncPolicy.removalKeys(links, "track", "yt1"))
    }

    @Test fun artistMatchingPreservesUnicodeAndRejectsUnrelatedNames() {
        assertTrue(SpotifyLibraryBridge.sameName("Beyoncé", "BEYONCE"))
        assertTrue(SpotifyLibraryBridge.sameName("अरिजीत सिंह", "अरिजीत सिंह"))
        assertFalse(SpotifyLibraryBridge.sameName("Artist", "Different Artist"))
    }

    @Test fun damagedMembershipCacheDoesNotCreateSourceLinks() {
        assertTrue(SpotifyLibraryBridge.decodeLinks("not valid json").isEmpty())
        assertEquals(mapOf("track:one" to "yt1"), SpotifyLibraryBridge.decodeLinks("{\"track:one\":\"yt1\"}"))
    }
}
