package com.devxrohan.smusic

import com.devxrohan.smusic.innertube.YouTube
import kotlinx.coroutines.runBlocking
import org.junit.Test

class YouTubeTest {
    @Test
    fun testPlaylist() = runBlocking {
        // Search for a playlist
        val searchResult = YouTube.search("The playlist", YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST)
        val playlistId = searchResult.getOrNull()?.items?.firstOrNull()?.id
        println("Found playlist ID: $playlistId")
        
        if (playlistId != null) {
            val result = YouTube.playlist(playlistId)
            if (result.isFailure) {
                println("Failed to fetch playlist:")
                result.exceptionOrNull()?.printStackTrace()
            } else {
                println("Successfully fetched playlist: ${result.getOrNull()?.playlist?.title}")
            }
        }
    }
}
