package com.devxrohan.smusic.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devxrohan.smusic.constants.InnerTubeCookieKey
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.extensions.toMediaItem
import com.devxrohan.smusic.innertube.utils.parseCookieString
import com.devxrohan.smusic.models.toMediaMetadata
import com.devxrohan.smusic.pulse.PulseEngine
import com.devxrohan.smusic.spotify.*
import com.devxrohan.smusic.utils.SyncUtils
import com.devxrohan.smusic.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LibrarySyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val youtube: SyncUtils,
    private val spotify: SpotifyLibraryRepository,
    private val bridge: SpotifyLibraryBridge,
    private val pulse: PulseEngine,
) : ViewModel() {
    data class State(val syncing: Boolean = false, val message: String = "Your favourites and collections, together.", val connectAccounts: Boolean = false)
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    val connected = kotlinx.coroutines.flow.combine(context.dataStore.data, SpotifyCredentialStore.get(context).data) { preferences, session ->
        "SAPISID" in parseCookieString(preferences[InnerTubeCookieKey].orEmpty()) || session[SpotifySpDcKey].orEmpty().isNotBlank()
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), false)


    fun syncNow() {
        if (mutableState.value.syncing) return
        mutableState.value = State(syncing = true, message = "Checking connected accounts…")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val preferences = context.dataStore.data.first()
                val hasYouTube = "SAPISID" in parseCookieString(preferences[InnerTubeCookieKey].orEmpty()) && preferences[com.devxrohan.smusic.constants.YtmSyncKey] != false
                val hasSpotify = SpotifyCredentialStore.get(context).data.first()[SpotifySpDcKey].orEmpty().isNotBlank()
                val spotifyEnabled = preferences[ShowSpotifyPlaylistsKey] == true
                if (!hasYouTube && !(hasSpotify && spotifyEnabled)) {
                    mutableState.value = State(message = "Connect an account and enable library sync in Account settings.", connectAccounts = true)
                    return@launch
                }
                if (hasYouTube) {
                    mutableState.value = State(true, "Syncing YouTube Music…")
                    youtube.syncAll()
                }
                if (hasSpotify && spotifyEnabled) {
                    mutableState.value = State(true, "Syncing Spotify favourites and collections…")
                    spotify.refreshPlaylists()
                    spotify.errorMessage.value?.let { error -> throw IllegalStateException(error) }
                    bridge.syncLibrary(force = true)
                }
                mutableState.value = State(true, "Updating your Pulse taste profile…")
                pulse.refreshLibraryTaste()
                mutableState.value = State(message = "Sync finished. Your saved music is ready.")
            } catch (cancelled: CancellationException) {
                mutableState.value = State(message = "Sync paused. Tap Sync now to continue.")
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = State(message = if (error is Spotify.SpotifyException)
                    "Spotify request failed (HTTP ${error.statusCode}). Your local music is still available. Try again later."
                    else "Some items could not sync. Saved music is still available; try again.")
            }
        }
    }

    suspend fun somethingToPlay() = withContext(Dispatchers.IO) {
        val songs = database.allSongs().first()
        val preferences = context.dataStore.data.first()
        val spotifyLiked = if (preferences[ShowSpotifyPlaylistsKey] == true)
            SpotifyLibraryBridge.decodeLinks(preferences[SpotifyImportedLinksKey]).filterKeys { it.startsWith("track:") }.values.toSet()
            else emptySet()
        val favourites = songs.filter { it.song.liked || it.song.inLibrary != null || it.id in spotifyLiked || it.id in preferences[SMusicLikedIdsKey].orEmpty() }
        (favourites.ifEmpty { songs }).shuffled().take(40).map { it.toMediaMetadata().toMediaItem() }
    }

    val artwork = kotlinx.coroutines.flow.combine(database.recentSongs(limit = 3), database.latestSongArtwork(3)) { recent, imported ->
        (recent.mapNotNull { it.thumbnailUrl } + imported).distinct().take(3)
    }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    fun noMusic() { mutableState.value = mutableState.value.copy(message = "Like, add or sync some music first.") }
}
