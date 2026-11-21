/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
// Adapted for SMusic on 2026-11-21; preserves the upstream account protocol.

package com.devxrohan.smusic.spotify

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.devxrohan.smusic.spotify.spotifyReportException as reportException
import javax.inject.Inject

@HiltViewModel
class SpotifyAccountViewModel
    @Inject
    constructor(
        private val repository: SpotifyLibraryRepository,
        private val libraryBridge: SpotifyLibraryBridge,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SpotifyAccountUiState(isLoading = true))
        val uiState: StateFlow<SpotifyAccountUiState> = _uiState.asStateFlow()

        init {
            restoreSession()
        }

        fun restoreSession() {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repository.restoreSession() }
                    .onSuccess { session ->
                        _uiState.update {
                            it.copy(
                                isAuthenticated = session.isAuthenticated,
                                accountName = session.accountName,
                                accountAvatarUrl = session.accountAvatarUrl,
                                isLoading = false,
                            )
                        }
                        if (session.isAuthenticated) loadLibrary(force = false)
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        reportException(error)
                        _uiState.update {
                            it.copy(
                                isAuthenticated = false,
                                isLoading = false,
                                errorMessage = spotifyErrorMessage(error),
                            )
                        }
                    }
            }
        }

        fun connectWithCookies(
            spDc: String,
            spKey: String,
        ) {
            if (spDc.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                runCatching { repository.connectWithCookies(spDc = spDc, spKey = spKey) }
                    .onSuccess { session ->
                        _uiState.update {
                            it.copy(
                                isAuthenticated = true,
                                accountName = session.accountName,
                                accountAvatarUrl = session.accountAvatarUrl,
                                isLoading = false,
                            )
                        }
                        reloadPlaylists()
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        reportException(error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = spotifyErrorMessage(error),
                            )
                        }
                    }
            }
        }

        fun reloadPlaylists() = loadLibrary(force = true)

        private fun loadLibrary(force: Boolean) {
            if (_uiState.value.isLoading) return
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val playlists = repository.refreshPlaylists()
                    if (repository.errorMessage.value == null) libraryBridge.syncLibrary(force)
                    _uiState.update { it.copy(playlistCount = playlists.size, errorMessage = repository.errorMessage.value) }
                } catch (error: CancellationException) { throw error
                } catch (error: Exception) {
                    _uiState.update { it.copy(errorMessage = spotifyErrorMessage(error)) }
                } finally {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }

        fun logout() {
            if (_uiState.value.isLoading) return
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                runCatching { repository.logout() }
                    .onSuccess {
                        _uiState.value = SpotifyAccountUiState()
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        reportException(error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = spotifyErrorMessage(error),
                            )
                        }
                    }
            }
        }

        fun dismissError() {
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

@Immutable
data class SpotifyAccountUiState(
    val isAuthenticated: Boolean = false,
    val accountName: String = "",
    val accountAvatarUrl: String? = null,
    val playlistCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
