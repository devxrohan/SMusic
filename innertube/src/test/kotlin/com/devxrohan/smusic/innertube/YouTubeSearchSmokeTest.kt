package com.devxrohan.smusic.innertube

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class YouTubeSearchSmokeTest {
    @Test
    fun suggestionsCanBeLoadedFromYouTube() {
        assumeTrue("Live network tests are opt-in", System.getenv("SMUSIC_RUN_NETWORK_TESTS") == "true")
        runBlocking {
            val suggestions = YouTube.searchSuggestions("taylor swift").getOrThrow()
            assertTrue(suggestions.queries.isNotEmpty() || suggestions.recommendedItems.isNotEmpty())
        }
    }
}
