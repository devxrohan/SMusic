package com.devxrohan.smusic.innertube

import com.devxrohan.smusic.innertube.models.AlbumItem
import com.devxrohan.smusic.innertube.models.ArtistItem
import com.devxrohan.smusic.innertube.models.MusicResponsiveListItemRenderer
import com.devxrohan.smusic.innertube.models.SongItem
import com.devxrohan.smusic.innertube.models.getItems
import com.devxrohan.smusic.innertube.models.response.GetSearchSuggestionsResponse
import com.devxrohan.smusic.innertube.models.response.SearchResponse
import com.devxrohan.smusic.innertube.pages.SearchSuggestionPage
import com.devxrohan.smusic.innertube.pages.SearchSummaryPage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchResponseParsingTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun suggestionsPreserveTextRunsAndRecommendedSongs() {
        val response = json.decodeFromString<GetSearchSuggestionsResponse>(fixture("suggestions.json"))
        val contents = response.contents.orEmpty()
            .flatMap { it.searchSuggestionsSectionRenderer?.contents.orEmpty() }
        val queries = contents.mapNotNull {
            it.searchSuggestionRenderer?.suggestion?.runs?.joinToString("") { run -> run.text }
        }
        assertEquals(listOf("fixture song", "fixture artist"), queries)

        val renderer = contents.mapNotNull { it.musicResponsiveListItemRenderer }.single()
        val song = SearchSuggestionPage.fromMusicResponsiveListItemRenderer(renderer) as SongItem
        assertEquals("fixture0001", song.id)
        assertEquals("Fixture Song", song.title)
        assertEquals("Fixture Artist", song.artists.single().name)
        assertEquals("UC_fixture_artist", song.artists.single().id)
        assertEquals("https://example.invalid/fixture-song.png", song.thumbnail)
    }

    @Test
    fun searchResultsPreserveSongMetadataAndDuration() {
        val response = json.decodeFromString<SearchResponse>(fixture("results.json"))
        val shelf = requireNotNull(response.contents?.tabbedSearchResultsRenderer)
            .tabs.single().tabRenderer.content?.sectionListRenderer?.contents
            ?.single()?.musicShelfRenderer
        assertEquals("Songs", shelf?.title?.runs?.single()?.text)
        val renderer = requireNotNull(shelf?.contents).getItems().single()
        val song = SearchSummaryPage.fromMusicResponsiveListItemRenderer(renderer) as SongItem
        assertEquals("fixture0001", song.id)
        assertEquals("Fixture Song", song.title)
        assertEquals("Fixture Artist", song.artists.single().name)
        assertEquals(222, song.duration)
        assertEquals("https://example.invalid/fixture-song.png", song.thumbnail)
    }

    @Test
    fun incompleteSongsAreRejectedByBothSearchMappers() {
        val response = json.decodeFromString<GetSearchSuggestionsResponse>(fixture("suggestions.json"))
        val renderer = response.contents.orEmpty()
            .flatMap { it.searchSuggestionsSectionRenderer?.contents.orEmpty() }
            .mapNotNull { it.musicResponsiveListItemRenderer }.single()
        for (incomplete in listOf(renderer.copy(thumbnail = null), renderer.copy(playlistItemData = null))) {
            assertNull(SearchSuggestionPage.fromMusicResponsiveListItemRenderer(incomplete))
            assertNull(SearchSummaryPage.fromMusicResponsiveListItemRenderer(incomplete))
        }
    }

    @Test
    fun emptyResponsesPreserveNullableContents() {
        assertNull(json.decodeFromString<GetSearchSuggestionsResponse>("{}").contents)
        assertNull(json.decodeFromString<SearchResponse>("{}").contents)
    }

    @Test
    fun artistAndAlbumBrowseItemsPreserveTheirTypesAndMetadata() {
        val renderers = json.decodeFromString<List<MusicResponsiveListItemRenderer>>(fixture("browse-items.json"))
        for (mapItem in listOf(
            SearchSuggestionPage::fromMusicResponsiveListItemRenderer,
            SearchSummaryPage.Companion::fromMusicResponsiveListItemRenderer,
        )) {
            val artist = mapItem(renderers[0]) as ArtistItem
            assertEquals("UC_fixture_artist", artist.id)
            assertEquals("Fixture Artist", artist.title)
            assertNull(artist.shuffleEndpoint)
            assertNull(artist.radioEndpoint)

            val album = mapItem(renderers[1]) as AlbumItem
            assertEquals("MPRE_fixture_album", album.browseId)
            assertEquals("OLAK5uy_MPRE_fixture_album", album.playlistId)
            assertEquals("Fixture Album", album.title)
            assertEquals("Fixture Artist", album.artists?.single()?.name)
            assertEquals(2024, album.year)
        }
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/search/$name")) { "Missing search fixture: $name" }
            .bufferedReader().use { it.readText() }
}
