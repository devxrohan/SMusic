package com.devxrohan.smusic.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.models.PlaylistItem
import com.devxrohan.smusic.innertube.models.SongItem
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnlinePlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    database: MusicDatabase
) : ViewModel() {
    private val playlistId = savedStateHandle.get<String>("playlistId")!!

    val playlist = MutableStateFlow<PlaylistItem?>(null)
    val playlistSongs = MutableStateFlow<List<SongItem>>(emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    val dbPlaylist = database.playlistByBrowseId(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    var continuation: String? = null
        private set
    private val consumedContinuations = mutableSetOf<String>()
    private var continuationPagesLoaded = 0
    private var consecutiveEmptyPages = 0

    init {
        load(initial = true)
    }

    fun refresh() {
        load(initial = false)
    }

    fun loadMoreSongs() {
        if (_isLoadingMore.value) return

        continuation?.let { token ->
            if (continuationPagesLoaded >= 100 || !consumedContinuations.add(token)) {
                continuation = null // A repeated continuation must never trap the screen in a load loop.
                return
            }
            continuationPagesLoaded++
            viewModelScope.launch(Dispatchers.IO) {
                _isLoadingMore.value = true
                YouTube.playlistContinuation(token)
                    .onSuccess { playlistContinuationPage ->
                        val beforeCount = playlistSongs.value.size
                        playlistSongs.value = (playlistSongs.value + playlistContinuationPage.songs).distinctBy { it.id }
                        continuation = playlistContinuationPage.continuation
                        if (playlistSongs.value.size == beforeCount) consecutiveEmptyPages++ else consecutiveEmptyPages = 0
                        if (consecutiveEmptyPages >= 3 || continuation?.let { it in consumedContinuations } == true) {
                            continuation = null
                        }
                        _isLoadingMore.value = false
                    }.onFailure { throwable ->
                        consumedContinuations.remove(token)
                        _isLoadingMore.value = false
                        reportException(throwable)
                    }
            }
        }
    }

    fun retry() {
        load(initial = true)
    }

    private fun load(initial: Boolean) {
        if (initial) {
            if (_isLoading.value && playlist.value != null) return
        } else {
            if (_isRefreshing.value || _isLoading.value) return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (initial) {
                _isLoading.value = true
            } else {
                _isRefreshing.value = true
            }

            _error.value = null

            consumedContinuations.clear()
            continuationPagesLoaded = 0
            consecutiveEmptyPages = 0
            YouTube
                .playlist(playlistId)
                .onSuccess { playlistPage ->
                    playlist.value = playlistPage.playlist
                    playlistSongs.value = playlistPage.songs.distinctBy { it.id }
                    continuation = playlistPage.songsContinuation
                }.onFailure { throwable ->
                    _error.value = throwable.message ?: "Failed to load playlist"
                    reportException(throwable)
                }

            if (initial) {
                _isLoading.value = false
            } else {
                _isRefreshing.value = false
            }
        }
    }
}
