package com.devxrohan.smusic.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.pages.ArtistPage
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
    private val artworkRepository: com.devxrohan.smusic.utils.ArtistArtworkRepository,
) : ViewModel() {
    val artistId = savedStateHandle.get<String>("artistId")!!
    var artistPage by mutableStateOf<ArtistPage?>(null)
    /** True when the online artist page could not be loaded, so the screen can stop showing the loading skeleton. */
    var loadFailed by mutableStateOf(false)
    val libraryArtist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val librarySongs = database.artistSongsPreview(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        fetchArtistsFromYTM()
    }

    private fun fetchArtistsFromYTM() {
        viewModelScope.launch {
            com.devxrohan.smusic.utils.MusicPageRequests.artist(artistId)
                .onSuccess {
                    artistPage = it
                    loadFailed = false
                    // The online header and Pulse should use the same persisted artwork.
                    try { artworkRepository.cache(it) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (failure: Exception) { reportException(failure) }
                }.onFailure {
                    loadFailed = true
                    reportException(it)
                }
        }
    }
}
