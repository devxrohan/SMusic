package com.devxrohan.smusic.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.models.PlaylistItem
import com.devxrohan.smusic.innertube.models.WatchEndpoint
import com.devxrohan.smusic.innertube.models.YTItem
import com.devxrohan.smusic.innertube.pages.ExplorePage
import com.devxrohan.smusic.innertube.pages.HomePage
import com.devxrohan.smusic.innertube.utils.completedLibraryPage
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.db.entities.Album
import com.devxrohan.smusic.db.entities.Artist
import com.devxrohan.smusic.db.entities.LocalItem
import com.devxrohan.smusic.db.entities.Playlist
import com.devxrohan.smusic.db.entities.Song
import com.devxrohan.smusic.models.SimilarRecommendation
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.getSuspend
import com.devxrohan.smusic.utils.get
import com.devxrohan.smusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val recentActivity = MutableStateFlow<List<YTItem>?>(null)
    val recentPlaylistsDb = MutableStateFlow<List<Playlist>?>(null)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    private suspend fun load() {
        isLoading.value = true

        val musicProvider = context.dataStore.getSuspend(com.devxrohan.smusic.constants.MusicProviderKey, "YT")
        val isJioSaavn = musicProvider == "JIOSAAVN"

        if (isJioSaavn) {
            com.devxrohan.smusic.jiosaavn.JioSaavnApi.getTrendingSongs().onSuccess { songs ->
                homePage.value = HomePage(
                    sections = listOf(
                        HomePage.Section(
                            title = "Trending Songs",
                            label = "JioSaavn",
                            thumbnail = null,
                            endpoint = null,
                            items = songs
                        )
                    )
                )
            }.onFailure {
                reportException(it)
            }
            explorePage.value = ExplorePage(emptyList(), emptyList())
            similarRecommendations.value = emptyList()
        }

        val rawQuickPicks = database.quickPicks()
            .first()
            .filter { if (isJioSaavn) it.id.startsWith("JS:") else !it.id.startsWith("JS:") }

        val finalQuickPicks = if (rawQuickPicks.isEmpty()) {
            database.mostPlayedSongs(0L, limit = 20, offset = 0)
                .first()
                .filter { if (isJioSaavn) it.id.startsWith("JS:") else !it.id.startsWith("JS:") }
        } else {
            rawQuickPicks
        }

        quickPicks.value = finalQuickPicks.shuffled().take(20)

        forgottenFavorites.value = database.forgottenFavorites()
            .first()
            .filter { if (isJioSaavn) it.id.startsWith("JS:") else !it.id.startsWith("JS:") }
            .shuffled().take(20)

        val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2
        val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 50, offset = 0)
            .first()
            .filter { if (isJioSaavn) it.id.startsWith("JS:") else !it.id.startsWith("JS:") }
            .shuffled().take(10)
            
        val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 50, offset = 0)
            .first().filter { it.album.thumbnailUrl != null }
            .filter { if (isJioSaavn) it.id.startsWith("JS:") else !it.id.startsWith("JS:") }
            .shuffled().take(5)
            
        val keepListeningArtists = database.mostPlayedArtists(fromTimeStamp, limit = 50, offset = 0)
            .first().filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }
            .filter { if (isJioSaavn) it.id.startsWith("JS:") else !it.id.startsWith("JS:") }
            .shuffled().take(5)
            
        keepListening.value =
            (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()

        allLocalItems.value =
            (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
                .filter { it is Song || it is Album }

        if (!isJioSaavn) {
            if (YouTube.cookie != null) {
                YouTube.library("FEmusic_liked_playlists").completedLibraryPage().onSuccess {
                    accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>()
                        .filterNot { it.id == "SE" }
                }.onFailure {
                    reportException(it)
                }
            }

            // Similar to artists
            val artistRecommendations =
                database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
                    .filter { it.artist.isYouTubeArtist }
                    .shuffled().take(3)
                    .mapNotNull {
                        val items = mutableListOf<YTItem>()
                        YouTube.artist(it.id).onSuccess { page ->
                            items += page.sections.getOrNull(page.sections.size - 2)?.items.orEmpty()
                            items += page.sections.lastOrNull()?.items.orEmpty()
                        }
                        SimilarRecommendation(
                            title = it,
                            items = items
                                .shuffled()
                                .ifEmpty { return@mapNotNull null }
                        )
                    }
            // Similar to songs
            val songRecommendations =
                database.mostPlayedSongs(fromTimeStamp, limit = 10).first()
                    .filter { it.album != null }
                    .shuffled().take(2)
                    .mapNotNull { song ->
                        val endpoint =
                            YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                                ?: return@mapNotNull null
                        val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                        SimilarRecommendation(
                            title = song,
                            items = (page.songs.shuffled().take(8) +
                                    page.albums.shuffled().take(4) +
                                    page.artists.shuffled().take(4) +
                                    page.playlists.shuffled().take(4))
                                .shuffled()
                                .ifEmpty { return@mapNotNull null }
                        )
                    }
            similarRecommendations.value = (artistRecommendations + songRecommendations).shuffled()

            YouTube.home().onSuccess { page ->
                homePage.value = page
            }.onFailure {
                reportException(it)
            }

            YouTube.explore().onSuccess { page ->
                val artists: Set<String>
                val favouriteArtists: Set<String>
                database.artistsBookmarkedByCreateDateAsc().first().let { list ->
                    artists = list.map(Artist::id).toHashSet()
                    favouriteArtists = list
                        .filter { it.artist.bookmarkedAt != null }
                        .map { it.id }
                        .toHashSet()
                }
                explorePage.value = page.copy(
                    newReleaseAlbums = page.newReleaseAlbums
                        .sortedBy { album ->
                            if (album.artists.orEmpty().any { it.id in favouriteArtists }) 0
                            else if (album.artists.orEmpty().any { it.id in artists }) 1
                            else 2
                        }
                )
            }.onFailure {
                reportException(it)
            }
        }

        allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                homePage.value?.sections?.flatMap { it.items }.orEmpty() +
                explorePage.value?.newReleaseAlbums.orEmpty()

        isLoading.value = false
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing.value = true
            load()
            isRefreshing.value = false
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            load()
        }
    }
}
