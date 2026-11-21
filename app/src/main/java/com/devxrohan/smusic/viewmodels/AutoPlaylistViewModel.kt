@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.devxrohan.smusic.constants.SongSortDescendingKey
import com.devxrohan.smusic.constants.SongSortType
import com.devxrohan.smusic.constants.SongSortTypeKey
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.extensions.reversed
import com.devxrohan.smusic.extensions.toEnum
import com.devxrohan.smusic.playback.DownloadUtil
import com.devxrohan.smusic.utils.SyncUtils
import com.devxrohan.smusic.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoPlaylistViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    savedStateHandle: SavedStateHandle,
    private val syncUtils: SyncUtils,
    private val spotifyBridge: com.devxrohan.smusic.spotify.SpotifyLibraryBridge,
) : ViewModel() {
    val likeSource = MutableStateFlow("all")
    val availableLikeSources = combine(context.dataStore.data,
        com.devxrohan.smusic.spotify.SpotifyCredentialStore.get(context).data) { preferences, credentials ->
        com.devxrohan.smusic.spotify.SpotifySyncPolicy.availableLikeSources(
            !credentials[com.devxrohan.smusic.spotify.SpotifySpDcKey].isNullOrBlank(),
            "SAPISID" in com.devxrohan.smusic.innertube.utils.parseCookieString(preferences[com.devxrohan.smusic.constants.InnerTubeCookieKey].orEmpty()))
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, setOf("all", "smusic"))
    val selectedLikeSource = combine(likeSource, availableLikeSources) { selected, available ->
        com.devxrohan.smusic.spotify.SpotifySyncPolicy.activeLikeSource(selected, available)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "all")

    val spotifyError = MutableStateFlow<String?>(null)
    val spotifySyncing = MutableStateFlow(false)

    val playlist = savedStateHandle.get<String>("playlist")!!

    @OptIn(ExperimentalCoroutinesApi::class)
    val likedSongs =
        context.dataStore.data
            .map {
                it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE) to (it[SongSortDescendingKey]
                    ?: true)
            }
            .distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                when (playlist) {
                    "liked" -> combine(database.likedSongs(sortType, descending), database.allSongs(), spotifyBridge.links, context.dataStore.data, selectedLikeSource) { liked, all, links, preferences, source ->
                        val spotifyIds = if (preferences[com.devxrohan.smusic.spotify.ShowSpotifyPlaylistsKey] == true)
                            links.filterKeys { it.startsWith("track:") }.values.toSet() else emptySet()
                        val youtubeIds = preferences[com.devxrohan.smusic.utils.RemoteSeenLikedSongsKey].orEmpty().lineSequence().filter { it.isNotBlank() }.toSet()
                        val smusicIds = preferences[com.devxrohan.smusic.spotify.SMusicLikedIdsKey].orEmpty()
                        val ids = com.devxrohan.smusic.spotify.SpotifySyncPolicy.likedIds(liked.map { it.id }.toSet(), spotifyIds, youtubeIds, source, smusicIds)
                        val filtered = (liked + all.filter { it.id in ids }).distinctBy { it.id }.filter { it.id in ids }
                        val sorted = when (sortType) {
                            SongSortType.CREATE_DATE -> filtered.sortedBy { it.song.likedDate ?: it.song.inLibrary ?: java.time.LocalDateTime.MIN }
                            SongSortType.NAME -> filtered.sortedBy { it.title.lowercase() }
                            SongSortType.ARTIST -> filtered.sortedBy { song -> song.artists.joinToString { it.name }.lowercase() }
                            SongSortType.PLAY_TIME -> filtered.sortedBy { it.song.totalPlayTime }
                        }
                        sorted.reversed(descending)
                    }
                    "downloaded" -> downloadUtil.downloads.flatMapLatest { downloads ->
                        database.allSongs()
                            .flowOn(Dispatchers.IO)
                            .map { songs ->
                                songs.filter {
                                    downloads[it.id]?.state == Download.STATE_COMPLETED
                                }
                            }
                            .map { songs ->
                                when (sortType) {
                                    SongSortType.CREATE_DATE -> songs.sortedBy {
                                        downloads[it.id]?.updateTimeMs ?: 0L
                                    }

                                    SongSortType.NAME -> songs.sortedBy { it.song.title }
                                    SongSortType.ARTIST -> songs.sortedBy { song ->
                                        song.artists.joinToString(separator = "") { it.name }
                                    }

                                    SongSortType.PLAY_TIME -> songs.sortedBy { it.song.totalPlayTime }
                                }.reversed(descending)
                            }
                    }

                    else -> MutableStateFlow(emptyList())
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            availableLikeSources.collect { available ->
                if (likeSource.value !in available) likeSource.value = "all"
            }
        }
        if (playlist == "liked") syncSpotifyLikes(false)
    }

    fun syncSpotifyLikes(force: Boolean = true) {
        if (spotifySyncing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            if (com.devxrohan.smusic.spotify.SpotifyCredentialStore.get(context).data.first()[com.devxrohan.smusic.spotify.SpotifySpDcKey].isNullOrBlank()) {
                spotifyError.value = null
                return@launch
            }
            spotifySyncing.value = true
            try { spotifyBridge.syncLikes(force); spotifyError.value = null }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { spotifyError.value = "Spotify likes could not be synced. Try again." }
            finally { spotifySyncing.value = false }
        }
    }

    fun syncLikedSongs() {
        syncSpotifyLikes()
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
    }
}
