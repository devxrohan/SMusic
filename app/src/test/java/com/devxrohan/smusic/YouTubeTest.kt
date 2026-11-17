package com.devxrohan.smusic

import com.devxrohan.smusic.innertube.YouTube
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class YouTubeTest {
    @Test
    fun testPlaylist() = runBlocking {
        assumeTrue("Live YouTube smoke test is opt-in", System.getenv("SMUSIC_RUN_NETWORK_TESTS") == "true")
        val search = YouTube.search("The playlist", YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST).getOrThrow()
        val playlistId = search.items.firstOrNull()?.id
        assertNotNull("Search should return a playlist", playlistId)
        val playlist = YouTube.playlist(requireNotNull(playlistId)).getOrThrow()
        assertEquals(playlistId, playlist.playlist.id)
    }
}
