@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.devxrohan.smusic.pulse.*
import com.devxrohan.smusic.playback.DownloadUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class PulseViewModel @Inject constructor(private val pulseEngine: PulseEngine, private val downloadUtil: DownloadUtil, private val database: com.devxrohan.smusic.db.MusicDatabase, private val artworkRepository: com.devxrohan.smusic.utils.ArtistArtworkRepository) : ViewModel() {
    val summary = MutableStateFlow<PulseSummary?>(null)
    val loaded = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val blocked = MutableStateFlow<List<PulseArtistInsight>>(emptyList())
    private var observation: Job? = null
    val recommendations = combine(database.allSongs(), pulseEngine.revision.debounce(500)) { songs, _ -> songs }
        .mapLatest { pool -> withContext(Dispatchers.Default) { pulseEngine.familiarFavorites(pool, emptySet(), 12) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mixes = database.playlists(com.devxrohan.smusic.constants.PlaylistSortType.NAME, false)
        .map { playlists -> playlists.filter { it.id.startsWith("LP_PULSE_") && it.songCount > 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    init {
        observeProfile()
    }
    fun retry() = observeProfile()
    private fun observeProfile() {
        observation?.cancel()
        observation = viewModelScope.launch(Dispatchers.IO) {
            error.value = null
            loaded.value = false
            try {
                pulseEngine.backfillIfNeeded()
                pulseEngine.syncUserSignals(downloadUtil.downloads.value.filterValues { it.state == Download.STATE_COMPLETED }.keys)
                // Observe local learning without starting additional recommendation requests.
                summary.subscriptionCount.map { it > 0 }.distinctUntilChanged().collectLatest { visible ->
                    if (visible) pulseEngine.revision.debounce(300).collectLatest {
                        refreshSummary()
                        resolveArtistArtwork()
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) {
                error.value = "Your listening profile could not be loaded. Try again."
                loaded.value = true
            }
        }
    }
    private suspend fun refreshSummary() {
        summary.value = pulseEngine.summary()
        blocked.value = pulseEngine.blockedArtists()
        loaded.value = true
    }
    private suspend fun resolveArtistArtwork() = coroutineScope {
        val missing = (summary.value?.topArtists.orEmpty() + summary.value?.rising.orEmpty())
            .filter { it.thumbnail.isNullOrBlank() }.distinctBy { it.id }
        val permits = kotlinx.coroutines.sync.Semaphore(3)
        missing.forEach { artist ->
            launch {
                permits.acquire()
                try {
                    val url = artworkRepository.resolveMissing(artist.id) ?: return@launch
                    summary.update { current -> current?.copy(
                        topArtists = current.topArtists.map { if (it.id == artist.id) it.copy(thumbnail = url) else it },
                        rising = current.rising.map { if (it.id == artist.id) it.copy(thumbnail = url) else it },
                    ) }
                } finally { permits.release() }
            }
        }
    }
    private fun update(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            error.value = null
            try { action(); refreshSummary() }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { error.value = "Could not update Pulse. Try again." }
            finally { busy.value = false }
        }
    }
    fun unblock(artistId: String) = update { pulseEngine.setArtistBlocked(artistId, false) }
    fun setDiscoveryMode(mode: String) = update { pulseEngine.setDiscoveryMode(mode) }
    fun rebuild() = update { pulseEngine.rebuildFromHistory() }
}
