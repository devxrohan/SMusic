package com.devxrohan.smusic.generator

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devxrohan.smusic.models.MediaMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

private const val GENERATION_TIMEOUT_MS = 90_000L

@Immutable
data class GenerateUiState(
    val selectedMode: GenerateMode = GenerateMode.MY_MIX,
    val trackCount: Int = 25,
    val promptInput: String = "",
    val seedTrackName: String = "",
    val seedArtistName: String = "",
    val seedArtistQuery: String = "",
    val selectedTag: String = "Lofi",
    val isGenerating: Boolean = false,
    val loadingMessage: String = "",
    val error: String? = null,
)

sealed interface GenerateNavEvent {
    data class NavigateToPlaylist(val playlistId: String) : GenerateNavEvent
}

@HiltViewModel
class GenerateViewModel @Inject constructor(
    private val repository: GeneratorRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenerateUiState())
    val uiState: StateFlow<GenerateUiState> = _uiState.asStateFlow()

    private val _navEvents = MutableSharedFlow<GenerateNavEvent>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<GenerateNavEvent> = _navEvents

    fun selectMode(mode: GenerateMode) {
        if (_uiState.value.isGenerating) return
        _uiState.update { it.copy(selectedMode = mode, error = null) }
    }

    fun setTrackCount(count: Int) {
        if (!_uiState.value.isGenerating) {
            _uiState.update { it.copy(trackCount = count.coerceIn(5, 40)) }
        }
    }

    fun setPrompt(value: String) = _uiState.update { it.copy(promptInput = value) }
    fun setSelectedTag(value: String) = _uiState.update { it.copy(selectedTag = value) }
    fun setSeedTrack(title: String, artist: String) = _uiState.update {
        it.copy(seedTrackName = title, seedArtistName = artist)
    }
    fun setSeedArtistQuery(value: String) = _uiState.update { it.copy(seedArtistQuery = value) }
    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun generate() {
        val state = _uiState.value
        if (state.isGenerating) return

        val mode = state.selectedMode
        when (mode) {
            GenerateMode.SMART_AI -> if (state.promptInput.isBlank()) {
                _uiState.update { it.copy(error = "Please enter a vibe or theme for the AI.") }
                return
            }
            GenerateMode.SONG_RADIO -> if (state.seedTrackName.isBlank()) {
                _uiState.update { it.copy(error = "Please enter a song name for the radio mix.") }
                return
            }
            GenerateMode.SIMILAR_ARTISTS -> if (state.seedArtistQuery.isBlank()) {
                _uiState.update { it.copy(error = "Please enter an artist name.") }
                return
            }
            else -> Unit
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null, loadingMessage = "Initializing Generator…") }
            try {
                val onProgress: (String) -> Unit = { msg ->
                    _uiState.update { s -> s.copy(loadingMessage = msg) }
                }

                val tracks: List<MediaMetadata> = withTimeout(GENERATION_TIMEOUT_MS) {
                    when (mode) {
                        GenerateMode.MY_MIX -> repository.fetchMyMix(state.trackCount, onProgress)
                        GenerateMode.SMART_AI -> repository.fetchSmartAi(state.promptInput, state.trackCount, onProgress)
                        GenerateMode.SONG_RADIO -> repository.fetchSongRadio(
                            seedVideoId = null,
                            seedTitle = state.seedTrackName,
                            seedArtist = state.seedArtistName,
                            count = state.trackCount,
                            onProgress = onProgress
                        )
                        GenerateMode.SIMILAR_ARTISTS -> repository.fetchSimilarArtists(
                            artistQuery = state.seedArtistQuery,
                            count = state.trackCount,
                            onProgress = onProgress
                        )
                        GenerateMode.TAG -> repository.fetchGenreTag(
                            tag = state.selectedTag,
                            count = state.trackCount,
                            onProgress = onProgress
                        )
                        GenerateMode.NEVER_HEARD -> repository.fetchNeverHeard(state.trackCount, onProgress)
                        GenerateMode.TOP -> repository.fetchTopTracks(state.trackCount, onProgress)
                        GenerateMode.RECENT -> repository.fetchRecentTracks(state.trackCount, onProgress)
                    }
                }

                if (tracks.isEmpty()) {
                    throw IllegalStateException("No songs found for this selection. Try another prompt or mode.")
                }

                val title = when (mode) {
                    GenerateMode.MY_MIX -> "My Taste Mix • SMusic AI"
                    GenerateMode.SMART_AI -> "${state.promptInput.take(28)} • AI Vibe"
                    GenerateMode.SONG_RADIO -> "${state.seedTrackName} Radio"
                    GenerateMode.SIMILAR_ARTISTS -> "${state.seedArtistQuery} & Similar Artists"
                    GenerateMode.TAG -> "${state.selectedTag} Vibes"
                    GenerateMode.NEVER_HEARD -> "Never Heard Discoveries"
                    GenerateMode.TOP -> "My Top Tracks Mix"
                    GenerateMode.RECENT -> "Recent Rewind Mix"
                }

                val playlistId = repository.savePlaylist(title, tracks, onProgress)
                _navEvents.tryEmit(GenerateNavEvent.NavigateToPlaylist(playlistId))
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                _uiState.update { it.copy(error = "Generation timed out. Please check your internet connection.") }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage ?: "Failed to generate playlist.") }
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }
}
