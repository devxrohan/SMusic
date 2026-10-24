package com.devxrohan.smusic.innertube

import com.devxrohan.smusic.innertube.models.AccountInfo
import com.devxrohan.smusic.innertube.models.AccountRecentSearch
import com.devxrohan.smusic.innertube.models.AlbumItem
import com.devxrohan.smusic.innertube.models.Artist
import com.devxrohan.smusic.innertube.models.ArtistItem
import com.devxrohan.smusic.innertube.models.BrowseEndpoint
import com.devxrohan.smusic.innertube.models.GridRenderer
import com.devxrohan.smusic.innertube.models.MusicCarouselShelfRenderer
import com.devxrohan.smusic.innertube.models.MusicShelfRenderer
import com.devxrohan.smusic.innertube.models.PlaylistItem
import com.devxrohan.smusic.innertube.models.SearchSuggestions
import com.devxrohan.smusic.innertube.models.SongItem
import com.devxrohan.smusic.innertube.models.WatchEndpoint
import com.devxrohan.smusic.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.devxrohan.smusic.innertube.models.YouTubeClient
import com.devxrohan.smusic.innertube.models.YouTubeClient.Companion.WEB
import com.devxrohan.smusic.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.devxrohan.smusic.innertube.models.YouTubeLocale
import com.devxrohan.smusic.innertube.models.getContinuation
import com.devxrohan.smusic.innertube.models.getItems
import com.devxrohan.smusic.innertube.models.oddElements
import com.devxrohan.smusic.innertube.models.response.AccountMenuResponse
import com.devxrohan.smusic.innertube.models.response.BrowseResponse
import com.devxrohan.smusic.innertube.models.response.CreatePlaylistResponse
import com.devxrohan.smusic.innertube.models.response.GetQueueResponse
import com.devxrohan.smusic.innertube.models.response.GetSearchSuggestionsResponse
import com.devxrohan.smusic.innertube.models.response.FeedbackResponse
import com.devxrohan.smusic.innertube.models.response.GetTranscriptResponse
import com.devxrohan.smusic.innertube.models.response.NextResponse
import com.devxrohan.smusic.innertube.models.response.PlayerResponse
import com.devxrohan.smusic.innertube.models.response.SearchResponse
import com.devxrohan.smusic.innertube.utils.parseCookieString
import com.devxrohan.smusic.innertube.pages.AlbumPage
import com.devxrohan.smusic.innertube.pages.ArtistItemsContinuationPage
import com.devxrohan.smusic.innertube.pages.ArtistItemsPage
import com.devxrohan.smusic.innertube.pages.ArtistPage
import com.devxrohan.smusic.innertube.pages.BrowseResult
import com.devxrohan.smusic.innertube.pages.ExplorePage
import com.devxrohan.smusic.innertube.pages.HistoryPage
import com.devxrohan.smusic.innertube.pages.HomePage
import com.devxrohan.smusic.innertube.pages.LibraryContinuationPage
import com.devxrohan.smusic.innertube.pages.LibraryPage
import com.devxrohan.smusic.innertube.pages.MoodAndGenres
import com.devxrohan.smusic.innertube.pages.NewReleaseAlbumPage
import com.devxrohan.smusic.innertube.pages.NextPage
import com.devxrohan.smusic.innertube.pages.NextResult
import com.devxrohan.smusic.innertube.pages.PlaylistContinuationPage
import com.devxrohan.smusic.innertube.pages.PlaylistPage
import com.devxrohan.smusic.innertube.pages.RelatedPage
import com.devxrohan.smusic.innertube.pages.SearchPage
import com.devxrohan.smusic.innertube.pages.SearchResult
import com.devxrohan.smusic.innertube.pages.SearchSuggestionPage
import com.devxrohan.smusic.innertube.pages.SearchSummary
import com.devxrohan.smusic.innertube.pages.SearchSummaryPage
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.Proxy
import kotlin.random.Random

/**
 * Parse useful data with [InnerTube] sending requests.
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
object YouTube {
    private val innerTube = InnerTube()

    /**
     * InnerTube sometimes returns HTTP 200 for a rejected/no-op account write.
     * Validate the JSON response and any explicit status instead of treating
     * transport success as proof that YouTube applied the mutation.
     */
    private suspend fun validateWriteResponse(
        response: io.ktor.client.statement.HttpResponse,
        operation: String,
        requireSucceededStatus: Boolean = false,
    ) {
        val raw = response.bodyAsText()
        check(raw.isNotBlank()) { "YouTube $operation returned an empty response" }
        val root = runCatching { Json.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw IllegalStateException("YouTube $operation returned invalid JSON", it) }
        check("error" !in root) { "YouTube rejected $operation: ${root["error"]}" }
        val status = root["status"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        if (requireSucceededStatus) {
            check(status == "STATUS_SUCCEEDED") {
                "YouTube did not confirm $operation (status=${status ?: "missing"})"
            }
        } else if (status != null) {
            check(status == "STATUS_SUCCEEDED") { "YouTube rejected $operation (status=$status)" }
        }
    }

    var locale: YouTubeLocale
        get() = innerTube.locale
        set(value) {
            innerTube.locale = value
        }
    var visitorData: String?
        get() = innerTube.visitorData
        set(value) {
            innerTube.visitorData = value
        }
    /** Debug hook, see [InnerTube.authTrace]. */
    var authTrace: ((String) -> Unit)?
        get() = innerTube.authTrace
        set(value) {
            innerTube.authTrace = value
        }

    var dataSyncId: String?
        get() = innerTube.dataSyncId
        set(value) {
            innerTube.dataSyncId = value
        }
    var cookie: String?
        get() = innerTube.cookie
        set(value) {
            innerTube.cookie = value
        }
    var proxy: Proxy?
        get() = innerTube.proxy
        set(value) {
            innerTube.proxy = value
        }
    var streamBypassProxy: Boolean = false
    val streamProxy: Proxy?
        get() = if (streamBypassProxy) null else proxy
    var webClientPoTokenEnabled: Boolean = false
    var useLoginForBrowse: Boolean
        get() = innerTube.useLoginForBrowse
        set(value) {
            innerTube.useLoginForBrowse = value
        }

    suspend fun searchSuggestions(query: String): Result<SearchSuggestions> = runCatching {
        val response = innerTube.getSearchSuggestions(WEB_REMIX, query).body<GetSearchSuggestionsResponse>()
        val contents = response.contents.orEmpty()
        SearchSuggestions(
            queries = contents.flatMap { it.searchSuggestionsSectionRenderer?.contents.orEmpty() }.mapNotNull { content ->
                content.searchSuggestionRenderer?.suggestion?.runs?.joinToString(separator = "") { it.text }
            },
            recommendedItems = contents.flatMap { it.searchSuggestionsSectionRenderer?.contents.orEmpty() }.mapNotNull { content ->
                content.musicResponsiveListItemRenderer?.let { renderer ->
                    SearchSuggestionPage.fromMusicResponsiveListItemRenderer(renderer)
                }
            }
        )
    }

    /**
     * Fetch only YouTube Music's explicitly marked account-history entries. If the endpoint
     * returns ordinary suggestions, those are deliberately not treated as recent searches.
     * The caller must verify the current authenticated account before and after this call.
     */
    suspend fun accountRecentSearches(): Result<List<AccountRecentSearch>> = runCatching {
        val activeCookie = cookie.orEmpty()
        check(activeCookie.isNotBlank() && "SAPISID" in parseCookieString(activeCookie)) {
            "A connected YouTube account is required to read account search history"
        }
        val response = innerTube.getSearchSuggestions(
            WEB_REMIX,
            input = "",
            authenticated = true
        ).body<GetSearchSuggestionsResponse>()
        response.contents.orEmpty()
            .flatMap { it.searchSuggestionsSectionRenderer?.contents.orEmpty() }
            .mapNotNull { content ->
                val history = content.historySuggestionRenderer ?: return@mapNotNull null
                val token = history.serviceEndpoint?.feedbackEndpoint?.feedbackToken
                    ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val query = history.navigationEndpoint?.searchEndpoint?.query
                    ?.trim()?.takeIf { it.isNotEmpty() }
                    ?: history.suggestion?.runs?.joinToString(separator = "") { it.text }
                        ?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                AccountRecentSearch(query, token)
            }
            .distinctBy { it.query.lowercase() }
            .take(20)
    }

    /** Remove a real account-history row using its server-issued feedback token. */
    suspend fun removeAccountRecentSearch(feedbackToken: String): Result<Boolean> = runCatching {
        check(feedbackToken.isNotBlank()) { "Missing YouTube search-history token" }
        val activeCookie = cookie.orEmpty()
        check(activeCookie.isNotBlank() && "SAPISID" in parseCookieString(activeCookie)) {
            "A connected YouTube account is required to remove account search history"
        }
        val response = innerTube.removeSearchHistory(WEB_REMIX, feedbackToken).body<FeedbackResponse>()
        response.feedbackResponses?.firstOrNull()?.isProcessed == true
    }


    suspend fun searchSummary(query: String): Result<SearchSummaryPage> = runCatching {
        val response = innerTube.search(WEB_REMIX, query).body<SearchResponse>()
        val contents = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty()

        val shelfSummaries = contents.mapNotNull { it ->
            if (it.musicCardShelfRenderer != null) {
                SearchSummary(
                    title = it.musicCardShelfRenderer.header?.musicCardShelfHeaderBasicRenderer?.title?.runs?.firstOrNull()?.text ?: "Top result",
                    items = listOfNotNull(SearchSummaryPage.fromMusicCardShelfRenderer(it.musicCardShelfRenderer))
                        .plus(
                            it.musicCardShelfRenderer.contents
                                ?.mapNotNull { it.musicResponsiveListItemRenderer }
                                ?.mapNotNull(SearchSummaryPage.Companion::fromMusicResponsiveListItemRenderer)
                                .orEmpty()
                        )
                        .distinctBy { it.id }
                        .ifEmpty { null } ?: return@mapNotNull null
                )
            } else if (it.musicShelfRenderer != null) {
                SearchSummary(
                    title = it.musicShelfRenderer.title?.runs?.firstOrNull()?.text ?: "Other",
                    items = it.musicShelfRenderer.contents?.getItems()
                        ?.mapNotNull {
                            SearchSummaryPage.fromMusicResponsiveListItemRenderer(it)
                        }
                        ?.distinctBy { it.id }
                        ?.ifEmpty { null } ?: return@mapNotNull null
                )
            } else {
                null
            }
        }

        val flatItems = contents
            .mapNotNull { it.itemSectionRenderer?.contents }
            .flatten()
            .mapNotNull { it.musicResponsiveListItemRenderer }
            .mapNotNull { SearchSummaryPage.fromMusicResponsiveListItemRenderer(it) }
            .distinctBy { it.id }

        val itemSectionSummaries = flatItems.groupBy { item ->
            when (item) {
                is SongItem -> "Songs"
                is AlbumItem -> "Albums"
                is ArtistItem -> "Artists"
                is PlaylistItem -> "Playlists"
                else -> "Other"
            }
        }.map { (title, items) ->
            SearchSummary(title = title, items = items)
        }

        SearchSummaryPage(
            summaries = shelfSummaries + itemSectionSummaries
        )
    }


    suspend fun search(query: String, filter: SearchFilter): Result<SearchResult> = runCatching {
        val response = innerTube.search(WEB_REMIX, query, filter.value).body<SearchResponse>()
        SearchResult(
            items = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.lastOrNull()
                ?.musicShelfRenderer?.contents?.getItems()?.mapNotNull {
                    SearchPage.toYTItem(it)
                }.orEmpty(),
            continuation = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.lastOrNull()
                ?.musicShelfRenderer?.contents?.getContinuation()
        )
    }

    suspend fun searchContinuation(continuation: String): Result<SearchResult> = runCatching {
        val response = innerTube.search(WEB_REMIX, continuation = continuation).body<SearchResponse>()
        SearchResult(
            items = response.continuationContents?.musicShelfContinuation?.contents
                ?.mapNotNull {
                    SearchPage.toYTItem(it.musicResponsiveListItemRenderer)
                }!!,
            continuation = response.continuationContents.musicShelfContinuation.continuations?.getContinuation()
        )
    }

    suspend fun album(browseId: String, withSongs: Boolean = true): Result<AlbumPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()
        val sections = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?: response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?: response.contents?.sectionListRenderer?.contents
            ?: emptyList()
        val header = sections.firstNotNullOfOrNull { it.musicResponsiveHeaderRenderer }
        val detailHeader = response.header?.musicDetailHeaderRenderer
        val playlistId = response.microformat?.microformatDataRenderer?.urlCanonical
            ?.substringAfter("list=", "")?.substringBefore('&')?.takeIf { it.isNotBlank() }
            ?: browseId.removePrefix("MPREb_")
        val albumTitle = header?.title?.runs?.firstOrNull()?.text
            ?: detailHeader?.title?.runs?.firstOrNull()?.text
            ?: sections.firstNotNullOfOrNull { it.musicCardShelfRenderer?.header?.musicCardShelfHeaderBasicRenderer?.title?.runs?.firstOrNull()?.text }
            ?: "Unknown Album"
        val artists = header?.straplineTextOne?.runs?.oddElements()?.map {
            Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId)
        } ?: detailHeader?.subtitle?.runs?.oddElements()?.map {
            Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId)
        } ?: emptyList()
        val thumbnail = header?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url
            ?: detailHeader?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
            ?: detailHeader?.thumbnail?.croppedSquareThumbnailRenderer?.getThumbnailUrl()
            ?: ""
        AlbumPage(
            album = AlbumItem(
                browseId = browseId,
                playlistId = playlistId,
                title = albumTitle,
                artists = artists,
                year = header?.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                thumbnail = thumbnail,
            ),
            songs = if (withSongs) albumSongs(playlistId).getOrThrow() else emptyList(),
            otherVersions = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents
                ?.sectionListRenderer?.contents?.getOrNull(1)?.musicCarouselShelfRenderer?.contents
                ?.mapNotNull { it.musicTwoRowItemRenderer }
                ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                .orEmpty()
        )
    }

    suspend fun albumSongs(playlistId: String): Result<List<SongItem>> =
        runCatching {
            var response = innerTube.browse(WEB_REMIX, playlistId.let { if (it.startsWith("VL")) it else "VL$it" }).body<BrowseResponse>()
            val shelf = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents
                ?.sectionListRenderer?.contents?.firstOrNull()?.musicPlaylistShelfRenderer
                ?: response.contents?.sectionListRenderer?.contents?.firstNotNullOfOrNull { it.musicPlaylistShelfRenderer }
                ?: response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer
                    ?.content?.sectionListRenderer?.contents?.firstNotNullOfOrNull { it.musicPlaylistShelfRenderer }
            val songs = mutableListOf<SongItem>()
            songs += shelf?.contents?.getItems()?.mapNotNull { AlbumPage.fromMusicResponsiveListItemRenderer(it) }.orEmpty()
            var continuation = shelf?.contents?.getContinuation()
            val seenContinuations = mutableSetOf<String>()
            var pageCount = 0
            while (continuation != null && seenContinuations.add(continuation) && pageCount++ < 100) {
                response = innerTube.browse(client = WEB_REMIX, continuation = continuation).body<BrowseResponse>()
                val page = response.continuationContents?.musicPlaylistShelfContinuation ?: break
                val nextSongs = page.contents.getItems().mapNotNull { AlbumPage.fromMusicResponsiveListItemRenderer(it) }
                songs += nextSongs
                continuation = page.continuations?.getContinuation()
                if (nextSongs.isEmpty() && continuation?.let { it in seenContinuations } == true) continuation = null
            }
            songs.distinctBy { it.id }
        }

    suspend fun artist(browseId: String): Result<ArtistPage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()
            ArtistPage(
                artist = ArtistItem(
                    id = browseId,
                    title = response.header?.musicImmersiveHeaderRenderer?.title?.runs?.firstOrNull()?.text
                        ?: response.header?.musicVisualHeaderRenderer?.title?.runs?.firstOrNull()?.text
                        ?: response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text ?: "Unknown Title",
                    thumbnail = response.header?.musicImmersiveHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                        ?: response.header?.musicVisualHeaderRenderer?.foregroundThumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                        ?: response.header?.musicDetailHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: "",
                    channelId = response.header?.musicImmersiveHeaderRenderer?.subscriptionButton?.subscribeButtonRenderer?.channelId,
                    shuffleEndpoint = response.header?.musicImmersiveHeaderRenderer?.playButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint
                        ?: response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer
                            ?.contents?.firstOrNull()?.musicShelfRenderer?.contents?.firstOrNull()?.musicResponsiveListItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                    radioEndpoint = response.header?.musicImmersiveHeaderRenderer?.startRadioButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint
                ),
                sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                    ?.tabRenderer?.content?.sectionListRenderer?.contents
                    ?.mapNotNull(ArtistPage::fromSectionListRendererContent)!!,
                description = response.header?.musicImmersiveHeaderRenderer?.description?.runs?.firstOrNull()?.text
            )
        }

    suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
        val gridRenderer = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.gridRenderer
        if (gridRenderer != null) {
            ArtistItemsPage(
                title = gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text.orEmpty(),
                items = gridRenderer.items.mapNotNull {
                    it.musicTwoRowItemRenderer?.let { renderer ->
                        ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                    }
                },
                continuation = gridRenderer.continuations?.getContinuation()
            )
        } else {
            val musicPlaylistShelfRenderer = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                ?.musicPlaylistShelfRenderer
            ArtistItemsPage(
                title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text!!,
                items = musicPlaylistShelfRenderer?.contents?.getItems()?.mapNotNull {
                    ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                }!!,
                continuation = musicPlaylistShelfRenderer.contents.getContinuation()
            )
        }
    }

    suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()

        when {
            response.continuationContents?.gridContinuation != null -> {
                val gridContinuation = response.continuationContents.gridContinuation
                ArtistItemsContinuationPage(
                    items = gridContinuation.items.mapNotNull {
                        it.musicTwoRowItemRenderer?.let { renderer ->
                            ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                        }
                    },
                    continuation = gridContinuation.continuations?.getContinuation()
                )
            }

            response.continuationContents?.musicPlaylistShelfContinuation != null -> {
                val musicPlaylistShelfContinuation = response.continuationContents.musicPlaylistShelfContinuation
                ArtistItemsContinuationPage(
                    items = musicPlaylistShelfContinuation.contents.getItems().mapNotNull {
                        ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                    },
                    continuation = musicPlaylistShelfContinuation.continuations?.getContinuation()
                )
            }

            else -> {
                val continuationItems = response.onResponseReceivedActions?.firstOrNull()
                    ?.appendContinuationItemsAction?.continuationItems
                ArtistItemsContinuationPage(
                    items = continuationItems?.getItems()?.mapNotNull {
                        ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                    }!!,
                    continuation = continuationItems.getContinuation()
                )
            }
        }
    }

    suspend fun playlist(playlistId: String): Result<PlaylistPage> = runCatching {
        val normalizedPlaylistBrowseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = normalizedPlaylistBrowseId,
            setLogin = true
        ).body<BrowseResponse>()
        val sectionContents = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?: response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?: response.contents?.sectionListRenderer?.contents
            ?: emptyList()
        val base = sectionContents.firstOrNull()
        val responsiveHeader = base?.musicResponsiveHeaderRenderer
            ?: base?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer
        val detailHeader = response.header?.musicDetailHeaderRenderer
        val playlistShelf = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents
            ?.sectionListRenderer?.contents?.firstOrNull()?.musicPlaylistShelfRenderer
            ?: sectionContents.firstNotNullOfOrNull { it.musicPlaylistShelfRenderer }
        val editable = base?.musicEditablePlaylistDetailHeaderRenderer != null

        PlaylistPage(
            playlist = PlaylistItem(
                id = playlistId,
                title = responsiveHeader?.title?.runs?.firstOrNull()?.text
                    ?: detailHeader?.title?.runs?.firstOrNull()?.text ?: "",
                author = responsiveHeader?.straplineTextOne?.runs?.firstOrNull()?.let {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId
                    )
                } ?: detailHeader?.subtitle?.runs?.firstOrNull()?.let {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId
                    )
                },
                songCountText = responsiveHeader?.secondSubtitle?.runs?.firstOrNull()?.text
                    ?: detailHeader?.secondSubtitle?.runs?.firstOrNull()?.text,
                thumbnail = responsiveHeader?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url
                    ?: detailHeader?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                    ?: detailHeader?.thumbnail?.croppedSquareThumbnailRenderer?.getThumbnailUrl() ?: "",
                playEndpoint = null,
                shuffleEndpoint = responsiveHeader?.buttons?.lastOrNull()?.menuRenderer?.items?.firstOrNull()?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                    ?: detailHeader?.menu?.menuRenderer?.items?.firstOrNull()?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                radioEndpoint = responsiveHeader?.buttons?.getOrNull(2)?.menuRenderer?.items?.find {
                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                    ?: detailHeader?.menu?.menuRenderer?.items?.find {
                        it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                    }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                isEditable = editable
            ),
            songs = playlistShelf?.contents?.getItems()?.mapNotNull {
                PlaylistPage.fromMusicResponsiveListItemRenderer(it)
            }.orEmpty(),
            songsContinuation = playlistShelf?.contents?.getContinuation(),
            continuation = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents
                ?.sectionListRenderer?.continuations?.getContinuation()
    
        )
    }

    suspend fun playlistContinuation(continuation: String) = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            continuation = continuation,
            setLogin = true
        ).body<BrowseResponse>()

        val musicPlaylistShelfContinuation = response.continuationContents?.musicPlaylistShelfContinuation
        if (musicPlaylistShelfContinuation != null) {
            PlaylistContinuationPage(
                songs = musicPlaylistShelfContinuation.contents.getItems().mapNotNull {
                    PlaylistPage.fromMusicResponsiveListItemRenderer(it)
                },
                continuation = musicPlaylistShelfContinuation.continuations?.getContinuation()
            )
        } else {
            val continuationItems = response.onResponseReceivedActions?.firstOrNull()
                ?.appendContinuationItemsAction?.continuationItems
            PlaylistContinuationPage(
                songs = continuationItems?.getItems()?.mapNotNull {
                    PlaylistPage.fromMusicResponsiveListItemRenderer(it)
                }!!,
                continuation = continuationItems.getContinuation()
            )
        }
    }

    /**
     * Home feed. [maxContinuations] limits how many extra pages are fetched before returning (null = all).
     * The returned [HomePage.continuation] lets the caller load the rest in the background with [homeMore],
     * so the first screen of rows no longer waits for every page.
     */
    suspend fun home(maxContinuations: Int? = null): Result<HomePage> = runCatching {
        var response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_home").body<BrowseResponse>()
        var continuation = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.continuations?.getContinuation()
        val sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents!!
            .mapNotNull { it.musicCarouselShelfRenderer }
            .mapNotNull {
                HomePage.Section.fromMusicCarouselShelfRenderer(it)
            }.toMutableList()
        var fetched = 0
        while (continuation != null && (maxContinuations == null || fetched < maxContinuations)) {
            response = innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()
            continuation = response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
            sections += response.continuationContents?.sectionListContinuation?.contents
                ?.mapNotNull { it.musicCarouselShelfRenderer }
                ?.mapNotNull {
                    HomePage.Section.fromMusicCarouselShelfRenderer(it)
                }.orEmpty()
            fetched++
        }
        HomePage(sections, continuation)
    }

    /** Loads the remaining home pages after a limited [home] call. */
    suspend fun homeMore(continuationToken: String, maxContinuations: Int = 1): Result<HomePage> = runCatching {
        var continuation: String? = continuationToken
        val sections = mutableListOf<HomePage.Section>()
        var fetched = 0
        while (continuation != null && fetched < maxContinuations.coerceAtLeast(1)) {
            val response = innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()
            continuation = response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
            sections += response.continuationContents?.sectionListContinuation?.contents
                ?.mapNotNull { it.musicCarouselShelfRenderer }
                ?.mapNotNull { HomePage.Section.fromMusicCarouselShelfRenderer(it) }
                .orEmpty()
            fetched++
        }
        HomePage(sections, continuation)
    }

    suspend fun explore(): Result<ExplorePage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_explore").body<BrowseResponse>()
        ExplorePage(
            newReleaseAlbums = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.find {
                it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_new_releases_albums"
            }?.musicCarouselShelfRenderer?.contents
                ?.mapNotNull { it.musicTwoRowItemRenderer }
                ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer).orEmpty(),
            moodAndGenres = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.find {
                it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_moods_and_genres"
            }?.musicCarouselShelfRenderer?.contents
                ?.mapNotNull { it.musicNavigationButtonRenderer }
                ?.mapNotNull(MoodAndGenres.Companion::fromMusicNavigationButtonRenderer)
                .orEmpty()
        )
    }

    suspend fun newReleaseAlbums(): Result<List<AlbumItem>> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_new_releases_albums").body<BrowseResponse>()
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.gridRenderer?.items
            ?.mapNotNull { it.musicTwoRowItemRenderer }
            ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
            .orEmpty()
    }

    suspend fun moodAndGenres(): Result<List<MoodAndGenres>> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_moods_and_genres").body<BrowseResponse>()
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents!!
            .mapNotNull(MoodAndGenres.Companion::fromSectionListRendererContent)
    }

    suspend fun moodGenreArtwork(browseId: String, params: String?): Result<String?> = runCatching {
        browse(browseId, params).getOrThrow().items
            .asSequence()
            .flatMap { it.items.asSequence() }
            .map { it.thumbnail }
            .firstOrNull { it.isNotBlank() }
    }

    suspend fun browse(browseId: String, params: String?): Result<BrowseResult> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = browseId, params = params).body<BrowseResponse>()
        BrowseResult(
            title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text,
            items = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.mapNotNull { content ->
                when {
                    content.gridRenderer != null -> {
                        BrowseResult.Item(
                            title = content.gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text,
                            items = content.gridRenderer.items
                                .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                                .mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer)
                        )
                    }

                    content.musicCarouselShelfRenderer != null -> {
                        BrowseResult.Item(
                            title = content.musicCarouselShelfRenderer.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text,
                            items = content.musicCarouselShelfRenderer.contents
                                .mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                                .mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer)
                        )
                    }

                    else -> null
                }
            }.orEmpty()
        )
    }

    suspend fun library(browseId: String, tabIndex: Int = 0) = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = browseId,
            setLogin = true
        ).body<BrowseResponse>()

        val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs

        val contents = if (tabs != null && tabs.size >= tabIndex) {
            tabs[tabIndex].tabRenderer.content?.sectionListRenderer?.contents?.firstOrNull()
        }
        else {
            null
        }

        when {
            contents?.gridRenderer != null -> {
                LibraryPage(
                    items = contents.gridRenderer.items
                        .mapNotNull (GridRenderer.Item::musicTwoRowItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
                    continuation = contents.gridRenderer.continuations?.getContinuation()
                )
            }

            else -> { // contents?.musicShelfRenderer != null
                LibraryPage(
                    items = contents?.musicShelfRenderer?.contents!!
                        .mapNotNull (MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
                    continuation = contents.musicShelfRenderer.continuations?.getContinuation()
                )
            }
        }
    }

    suspend fun libraryContinuation(continuation: String) = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            continuation = continuation,
            setLogin = true
        ).body<BrowseResponse>()

        val contents = response.continuationContents

        when {
            contents?.gridContinuation != null -> {
                LibraryContinuationPage(
                    items = contents.gridContinuation.items
                        .mapNotNull (GridRenderer.Item::musicTwoRowItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
                    continuation = contents.gridContinuation.continuations?.getContinuation()
                )
            }

            else -> { // contents?.musicShelfContinuation != null
                LibraryContinuationPage(
                    items = contents?.musicShelfContinuation?.contents!!
                        .mapNotNull (MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
                    continuation = contents.musicShelfContinuation.continuations?.getContinuation()
                )
            }
        }
    }

    suspend fun libraryRecentActivity(): Result<LibraryPage> = runCatching {
        val continuation = LibraryFilter.FILTER_RECENT_ACTIVITY.value

        val response = innerTube.browse(
            client = WEB_REMIX,
            continuation = continuation,
            setLogin = true
        ).body<BrowseResponse>()

        val items = response.continuationContents?.sectionListContinuation?.contents?.firstOrNull()
            ?.gridRenderer?.items!!.mapNotNull {
                it.musicTwoRowItemRenderer?.let { renderer ->
                    LibraryPage.fromMusicTwoRowItemRenderer(renderer)
                }
            }.toMutableList()

        /*
         * We need to fetch the artist page when accessing the library because it allows to have
         * a proper playEndpoint, which is needed to correctly report the playing indicator in
         * the home page.
         *
         * Despite this, we need to use the old thumbnail because it's the proper format for a
         * square picture, which is what we need.
         */
        items.forEachIndexed { index, item ->
            if (item is ArtistItem)
                items[index] = artist(item.id).getOrNull()?.artist!!.copy(thumbnail = item.thumbnail)
        }

        LibraryPage(
            items = items,
            continuation = null
        )
    }

    suspend fun musicHistory() = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = "FEmusic_history",
            setLogin = true
        ).body<BrowseResponse>()

        HistoryPage(
            sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents
                ?.mapNotNull {
                    it.musicShelfRenderer?.let { musicShelfRenderer ->
                        HistoryPage.fromMusicShelfRenderer(musicShelfRenderer)
                    }
                }
        )
    }

    suspend fun visitorData(): Result<String> = runCatching {
        Json.parseToJsonElement(innerTube.getSwJsData().bodyAsText().substring(5))
            .jsonArray[0]
            .jsonArray[2]
            .jsonArray.first {
                (it as? JsonPrimitive)?.contentOrNull?.let { candidate ->
                    VISITOR_DATA_REGEX.containsMatchIn(candidate)
                } ?: false
            }
            .jsonPrimitive.content
    }

    suspend fun likeVideo(videoId: String, like: Boolean) = runCatching {
        val response = if (like) innerTube.likeVideo(WEB_REMIX, videoId)
        else innerTube.unlikeVideo(WEB_REMIX, videoId)
        validateWriteResponse(response, if (like) "like video" else "unlike video")
    }

    suspend fun likePlaylist(playlistId: String, like: Boolean) = runCatching {
        val response = if (like) innerTube.likePlaylist(WEB_REMIX, playlistId)
        else innerTube.unlikePlaylist(WEB_REMIX, playlistId)
        validateWriteResponse(response, if (like) "like playlist" else "unlike playlist")
    }

    suspend fun subscribeChannel(channelId: String, subscribe: Boolean) = runCatching {
        require(channelId.startsWith("UC")) { "A canonical YouTube channel ID is required" }
        val response = if (subscribe) innerTube.subscribeChannel(WEB_REMIX, channelId)
        else innerTube.unsubscribeChannel(WEB_REMIX, channelId)
        validateWriteResponse(response, if (subscribe) "subscribe to channel" else "unsubscribe from channel")
    }

    suspend fun getChannelId(browseId: String): String {
        artist(browseId).onSuccess {
            return it.artist.channelId ?: ""
        }
        return ""
    }

    suspend fun addToPlaylist(playlistId: String, videoId: String) = runCatching {
        val response = innerTube.addToPlaylist(WEB_REMIX, playlistId, videoId)
        validateWriteResponse(response, "add song to playlist", requireSucceededStatus = true)
    }

    suspend fun addPlaylistToPlaylist(playlistId: String, addPlaylistId: String) = runCatching {
        val response = innerTube.addPlaylistToPlaylist(WEB_REMIX, playlistId, addPlaylistId)
        validateWriteResponse(response, "add playlist to playlist", requireSucceededStatus = true)
    }

    suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String) = runCatching {
        val response = innerTube.removeFromPlaylist(WEB_REMIX, playlistId, videoId, setVideoId)
        validateWriteResponse(response, "remove song from playlist", requireSucceededStatus = true)
    }

    suspend fun moveSongPlaylist(playlistId: String, setVideoId: String, successorSetVideoId: String) = runCatching {
        val response = innerTube.moveSongPlaylist(WEB_REMIX, playlistId, setVideoId, successorSetVideoId)
        validateWriteResponse(response, "reorder playlist", requireSucceededStatus = true)
    }

    fun createPlaylist(title: String) = runBlocking {
        val response = innerTube.createPlaylist(WEB_REMIX, title)
        val body = response.body<CreatePlaylistResponse>()
        check(body.playlistId.isNotBlank()) { "YouTube did not return a playlist ID" }
        body.playlistId
    }

    suspend fun renamePlaylist(playlistId: String, name: String) = runCatching {
        val response = innerTube.renamePlaylist(WEB_REMIX, playlistId, name)
        validateWriteResponse(response, "rename playlist", requireSucceededStatus = true)
    }

    suspend fun deletePlaylist(playlistId: String) = runCatching {
        val response = innerTube.deletePlaylist(WEB_REMIX, playlistId)
        validateWriteResponse(response, "delete playlist", requireSucceededStatus = true)
    }

    suspend fun player(
        videoId: String,
        playlistId: String? = null,
        client: YouTubeClient,
        signatureTimestamp: Int? = null,
        setLogin: Boolean = true,
    ): Result<PlayerResponse> = runCatching {
        innerTube.player(client, videoId, playlistId, signatureTimestamp, setLogin).body<PlayerResponse>()
    }

    suspend fun registerPlayback(playlistId: String? = null, playbackTracking: String) = runCatching {
        // This URL is supplied by /player, but validate it before attaching the
        // connected account's cookies/auth headers to the tracking request.
        val parsed = java.net.URI(playbackTracking)
        require(parsed.scheme.equals("https", ignoreCase = true)) { "Refusing non-HTTPS playback tracking URL" }
        require(parsed.host in setOf("s.youtube.com", "www.youtube.com", "music.youtube.com")) {
            "Refusing untrusted playback tracking host: ${parsed.host}"
        }
        val cpn = (1..16).map {
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"[Random.Default.nextInt(0, 64)]
        }.joinToString("")

        val playbackUrl = playbackTracking.replace("https://s.youtube.com", "https://music.youtube.com")
        val response = innerTube.registerPlayback(url = playbackUrl, playlistId = playlistId, cpn = cpn)
        check(response.status.value in 200..299) { "YouTube did not accept playback history (HTTP ${response.status.value})" }
    }

    suspend fun next(endpoint: WatchEndpoint, continuation: String? = null): Result<NextResult> = runCatching {
        val response = innerTube.next(
            WEB_REMIX,
            endpoint.videoId,
            endpoint.playlistId,
            endpoint.playlistSetVideoId,
            endpoint.index,
            endpoint.params,
            continuation).body<NextResponse>()
        val playlistPanelRenderer = response.continuationContents?.playlistPanelContinuation
            ?: response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer
                .watchNextTabbedResultsRenderer.tabs[0].tabRenderer.content?.musicQueueRenderer
                ?.content?.playlistPanelRenderer!!
        val title = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer
            .watchNextTabbedResultsRenderer.tabs[0].tabRenderer.content?.musicQueueRenderer
            ?.header?.musicQueueHeaderRenderer?.subtitle?.runs?.firstOrNull()?.text
        val items = playlistPanelRenderer.contents.mapNotNull { content ->
            content.playlistPanelVideoRenderer
                ?.let(NextPage::fromPlaylistPanelVideoRenderer)
                ?.let { it to content.playlistPanelVideoRenderer.selected }
        }
        val songs = items.map { it.first }
        val currentIndex = items.indexOfFirst { it.second }.takeIf { it != -1 }

        // load automix items
        playlistPanelRenderer.contents.lastOrNull()?.automixPreviewVideoRenderer?.content?.automixPlaylistVideoRenderer?.navigationEndpoint?.watchPlaylistEndpoint?.let { watchPlaylistEndpoint ->
            return@runCatching next(watchPlaylistEndpoint).getOrThrow().let { result ->
                result.copy(
                    title = title,
                    items = songs + result.items,
                    lyricsEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint,
                    relatedEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint,
                    currentIndex = currentIndex,
                    endpoint = watchPlaylistEndpoint
                )
            }
        }
        NextResult(
            title = title,
            items = songs,
            currentIndex = currentIndex,
            lyricsEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint,
            relatedEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint,
            continuation = playlistPanelRenderer.continuations?.getContinuation(),
            endpoint = endpoint
        )
    }

    suspend fun lyrics(endpoint: BrowseEndpoint): Result<String?> = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
        response.contents?.sectionListRenderer?.contents?.firstOrNull()?.musicDescriptionShelfRenderer?.description?.runs?.firstOrNull()?.text
    }

    suspend fun related(endpoint: BrowseEndpoint, authenticated: Boolean = false): Result<RelatedPage> = runCatching {
        // Account queue expansion must use the connected YouTube Music session, not anonymous browse.
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId, setLogin = authenticated).body<BrowseResponse>()
        val songs = mutableListOf<SongItem>()
        val albums = mutableListOf<AlbumItem>()
        val artists = mutableListOf<ArtistItem>()
        val playlists = mutableListOf<PlaylistItem>()
        response.contents?.sectionListRenderer?.contents?.forEach { sectionContent ->
            sectionContent.musicCarouselShelfRenderer?.contents?.forEach { content ->
                when (val item = content.musicResponsiveListItemRenderer?.let(RelatedPage.Companion::fromMusicResponsiveListItemRenderer)
                    ?: content.musicTwoRowItemRenderer?.let(RelatedPage.Companion::fromMusicTwoRowItemRenderer)) {
                    is SongItem -> if (content.musicResponsiveListItemRenderer?.overlay
                            ?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchEndpoint?.watchEndpointMusicSupportedConfigs
                            ?.watchEndpointMusicConfig?.musicVideoType == MUSIC_VIDEO_TYPE_ATV
                    ) songs.add(item)

                    is AlbumItem -> albums.add(item)
                    is ArtistItem -> artists.add(item)
                    is PlaylistItem -> playlists.add(item)
                    null -> {}
                }
            }
        }
        RelatedPage(songs, albums, artists, playlists)
    }

    suspend fun queue(videoIds: List<String>? = null, playlistId: String? = null): Result<List<SongItem>> = runCatching {
        if (videoIds != null) {
            assert(videoIds.size <= MAX_GET_QUEUE_SIZE) // Max video limit
        }
        innerTube.getQueue(WEB_REMIX, videoIds, playlistId).body<GetQueueResponse>().queueDatas
            .mapNotNull {
                it.content.playlistPanelVideoRenderer?.let { renderer ->
                    NextPage.fromPlaylistPanelVideoRenderer(renderer)
                }
            }
    }

    suspend fun transcript(videoId: String): Result<String> = runCatching {
        val response = innerTube.getTranscript(WEB, videoId).body<GetTranscriptResponse>()
        response.actions?.firstOrNull()?.updateEngagementPanelAction?.content?.transcriptRenderer?.body?.transcriptBodyRenderer?.cueGroups?.joinToString(separator = "\n") { group ->
            val time = group.transcriptCueGroupRenderer.cues[0].transcriptCueRenderer.startOffsetMs
            val text = group.transcriptCueGroupRenderer.cues[0].transcriptCueRenderer.cue.simpleText
                .trim('♪')
                .trim(' ')
            "[%02d:%02d.%03d]$text".format(time / 60000, (time / 1000) % 60, time % 1000)
        }!!
    }

    suspend fun accountInfo(): Result<AccountInfo> = runCatching {
        innerTube.accountMenu(WEB_REMIX).body<AccountMenuResponse>()
            .actions[0].openPopupAction.popup.multiPageMenuRenderer
            .header?.activeAccountHeaderRenderer
            ?.toAccountInfo()!!
    }

    @JvmInline
    value class SearchFilter(val value: String) {
        companion object {
            val FILTER_SONG = SearchFilter("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            val FILTER_VIDEO = SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ALBUM = SearchFilter("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ARTIST = SearchFilter("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_FEATURED_PLAYLIST = SearchFilter("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D")
            val FILTER_COMMUNITY_PLAYLIST = SearchFilter("EgeKAQQoAEABagoQAxAEEAoQCRAF")
        }
    }

    @JvmInline
    value class LibraryFilter(val value: String) {
        companion object {
            val FILTER_RECENT_ACTIVITY = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpYnJhcnlfbGFuZGluZxoQZ2dNR0tnUUlCaEFCb0FZQg%3D%3D")
            val FILTER_RECENTLY_PLAYED = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpYnJhcnlfbGFuZGluZxoQZ2dNR0tnUUlCUkFCb0FZQg%3D%3D")
            val FILTER_PLAYLISTS_ALPHABETICAL = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpa2VkX3BsYXlsaXN0cxoQZ2dNR0tnUUlBUkFBb0FZQg%3D%3D")
            val FILTER_PLAYLISTS_RECENTLY_SAVED = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpa2VkX3BsYXlsaXN0cxoQZ2dNR0tnUUlBQkFCb0FZQg%3D%3D")
        }
    }

    const val MAX_GET_QUEUE_SIZE = 1000

    private val VISITOR_DATA_REGEX = Regex("^Cg[t|s]")
}
