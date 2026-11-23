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
    val controls = MutableStateFlow(PulseControls())
    val artistResults = MutableStateFlow<List<com.devxrohan.smusic.innertube.models.ArtistItem>>(emptyList())
    val artistSearchBusy = MutableStateFlow(false)
    val artistSearchError = MutableStateFlow<String?>(null)
    private var artistSearch: Job? = null
    private var artistSearchGeneration = 0
    private val artistSearchCache = LinkedHashMap<String, List<com.devxrohan.smusic.innertube.models.ArtistItem>>()
    private val artworkPermits = kotlinx.coroutines.sync.Semaphore(3)
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
        controls.value = pulseEngine.controls()
        loaded.value = true
    }
    private suspend fun resolveArtistArtwork() = coroutineScope {
        val missing = (summary.value?.topArtists.orEmpty() + summary.value?.rising.orEmpty() + listOfNotNull(summary.value?.listeningArtist))
            .filter { it.thumbnail.isNullOrBlank() }.distinctBy { it.id }
        val permits = kotlinx.coroutines.sync.Semaphore(3)
        missing.forEach { artist ->
            launch {
                permits.acquire()
                try {
                    val url = artworkRepository.resolveMissing(artist.id) ?: return@launch
                    summary.update { current -> current?.copy(
                        listeningArtist = current.listeningArtist?.let { if (it.id == artist.id) it.copy(thumbnail = url) else it },
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
    fun searchArtists(query: String) {
        artistSearch?.cancel()
        val generation = ++artistSearchGeneration
        val term = query.trim().take(160)
        artistSearchError.value = null
        artistResults.value = emptyList()
        artistSearchBusy.value = false
        if (term.length < 2) return
        artistSearchCache[term.lowercase(java.util.Locale.ROOT)]?.let { artistResults.value = it; return }
        artistSearchBusy.value = true
        artistSearch = viewModelScope.launch {
            try {
                val results = withTimeout(15_000L) {
                    com.devxrohan.smusic.innertube.YouTube.search(term,
                        com.devxrohan.smusic.innertube.YouTube.SearchFilter.FILTER_ARTIST).getOrThrow()
                }.items.filterIsInstance<com.devxrohan.smusic.innertube.models.ArtistItem>().distinctBy { it.id }.take(30)
                artistResults.value = results
                artistSearchCache[term.lowercase(java.util.Locale.ROOT)] = results
                if (artistSearchCache.size > 6) artistSearchCache.remove(artistSearchCache.keys.first())
            } catch (_: TimeoutCancellationException) { artistSearchError.value = "Search took too long. Try again." }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { artistSearchError.value = "Could not search artists. Check your connection and try again." }
            finally { if (generation == artistSearchGeneration) artistSearchBusy.value = false }
        }
    }
    fun stopArtistSearch() {
        artistSearchGeneration++
        artistSearch?.cancel()
        artistSearchBusy.value = false
        artistResults.value = emptyList()
        artistSearchError.value = null
    }
    fun chooseArtist(artist: com.devxrohan.smusic.innertube.models.ArtistItem) = update {
        pulseEngine.setArtistPreference(artist.id, PulseArtistChoice(artist.title, artist.thumbnail, System.currentTimeMillis()))
    }
    fun removeArtist(id: String) = update { pulseEngine.setArtistPreference(id, null) }
    suspend fun resolveControlArtwork(id: String) {
        artworkPermits.acquire()
        try {
            val image = artworkRepository.resolveMissing(id) ?: return
            controls.update { it.copy(entries = it.entries.map { entry ->
                if (entry.id == id && entry.track == null) entry.copy(thumbnailUrl = image) else entry
            }) }
        } finally { artworkPermits.release() }
    }

    fun clearFeedback(songId: String) = update { pulseEngine.clearFeedback(songId) }
    fun feedback(track: PulseFeedbackTrack, positive: Boolean) = update {
        check(pulseEngine.onTasteFeedback(listOf(track), positive))
    }
    fun block(artistId: String) = update { pulseEngine.setArtistBlocked(artistId, true) }
    fun unblock(artistId: String) = update { pulseEngine.setArtistBlocked(artistId, false) }
    fun setRecommendationTuning(variety: Double, cooldownHours: Double) = update { pulseEngine.setRecommendationTuning(variety, cooldownHours) }
    fun setDiscoveryMode(mode: String) = update { pulseEngine.setDiscoveryMode(mode) }
    fun rebuild() = update { pulseEngine.rebuildFromHistory() }
}
