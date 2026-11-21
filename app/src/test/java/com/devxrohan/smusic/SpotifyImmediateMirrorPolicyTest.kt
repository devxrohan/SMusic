package com.devxrohan.smusic

import com.devxrohan.smusic.spotify.Spotify
import com.devxrohan.smusic.spotify.SpotifyMirrorPolicy
import com.devxrohan.smusic.spotify.SpotifyWebSession
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class SpotifyImmediateMirrorPolicyTest {
    @Test fun libraryWritesAndTrackSearchUseWebPlayerPathfinderVersion() {
        for (operation in listOf("addToLibrary", "removeFromLibrary", "searchDesktop")) {
            assertEquals("https://api-partner.spotify.com/pathfinder/v1/query", Spotify.graphQlEndpoint(operation))
        }
        assertEquals("https://api-partner.spotify.com/pathfinder/v2/query", Spotify.graphQlEndpoint("fetchPlaylist"))
    }

    @Test fun existingSpotifyLikeIsNeitherRepeatedNorRemovedByAnAppUnlike() {
        assertEquals(SpotifyMirrorPolicy.Action.ACKNOWLEDGE, SpotifyMirrorPolicy.action(true, false, true, false))
        assertEquals(SpotifyMirrorPolicy.Action.ACKNOWLEDGE, SpotifyMirrorPolicy.action(false, false, true, false))
    }
    @Test fun onlyALikeKnownToBeNewCanLaterBeRemoved() {
        assertEquals(SpotifyMirrorPolicy.Action.ADD_OWNED, SpotifyMirrorPolicy.action(true, false, false, false))
        assertEquals(SpotifyMirrorPolicy.Action.REMOVE_OWNED, SpotifyMirrorPolicy.action(false, false, null, true))
    }
    @Test fun unknownOwnershipNeverAllowsAnUnlike() {
        assertEquals(SpotifyMirrorPolicy.Action.ADD_PROTECTED, SpotifyMirrorPolicy.action(true, false, null, false))
        assertEquals(SpotifyMirrorPolicy.Action.ACKNOWLEDGE, SpotifyMirrorPolicy.action(false, false, null, false))
        assertEquals(SpotifyMirrorPolicy.Action.ACKNOWLEDGE, SpotifyMirrorPolicy.action(true, true, false, true))
    }
    @Test fun partialCatalogCannotProveThatASongWasNotAlreadyLiked() {
        assertFalse(SpotifyMirrorPolicy.completeListing(true, false, setOf("one", "two"), 2))
        assertFalse(SpotifyMirrorPolicy.completeListing(false, true, setOf("one"), 1))
        assertFalse(SpotifyMirrorPolicy.completeListing(true, true, setOf("one"), 2))
        assertTrue(SpotifyMirrorPolicy.completeListing(true, true, setOf("one", "two"), 2))
    }

    @Test fun webPlayerVersionSupportsPlainAndEncodedConfiguration() {
        assertEquals("1.2.3", SpotifyWebSession.extractVersion("{\"clientVersion\":\"1.2.3\"}"))
        val encoded = Base64.getEncoder().encodeToString("{\"clientVersion\":\"1.2.4\"}".toByteArray())
        assertEquals("1.2.4", SpotifyWebSession.extractVersion("<script id=\"appServerConfig\" type=\"text/plain\">$encoded</script>"))
    }
    @Test fun mutationHashExtractionReadsDataWithoutExecutingTheBundle() {
        val hash = "a".repeat(64)
        assertEquals(mapOf("addToLibrary" to hash), SpotifyWebSession.extractHashes("new X(\"addToLibrary\",\"mutation\",\"$hash\",null)"))
    }
}
