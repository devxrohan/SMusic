package com.devxrohan.smusic.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devxrohan.smusic.pulse.PulseArtistInsight
import com.devxrohan.smusic.pulse.PulseEngine
import com.devxrohan.smusic.pulse.PulseSummary
import com.devxrohan.smusic.playback.DownloadUtil
import androidx.media3.exoplayer.offline.Download
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PulseViewModel @Inject constructor(
    private val pulseEngine: PulseEngine,
    private val downloadUtil: DownloadUtil,
) : ViewModel() {
    /** null while loading or if Pulse is unavailable; the screen handles both safely. */
    val summary = MutableStateFlow<PulseSummary?>(null)
    val loaded = MutableStateFlow(false)
    val blocked = MutableStateFlow<List<PulseArtistInsight>>(emptyList())

    init {
        // Same as any other page: it loads by itself. The heavy read just starts after the slide-in
        // so it never competes with the animation (the screen shows its normal "learning" card meanwhile).
        viewModelScope.launch(Dispatchers.IO) {
            delay(400)
            refresh(rebuild = false)
        }
    }

    private fun refresh(rebuild: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { if (rebuild) pulseEngine.rebuildFromHistory() else pulseEngine.backfillIfNeeded() }
            runCatching {
                pulseEngine.syncUserSignals(
                    downloadUtil.downloads.value
                        .filterValues { it.state == Download.STATE_COMPLETED }
                        .keys
                )
            }
            summary.value = runCatching { pulseEngine.summary() }.getOrNull()
            blocked.value = runCatching { pulseEngine.blockedArtists() }.getOrDefault(emptyList())
            loaded.value = true
        }
    }

    fun unblock(artistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { pulseEngine.setArtistBlocked(artistId, false) }
            blocked.value = runCatching { pulseEngine.blockedArtists() }.getOrDefault(emptyList())
        }
    }

    /** Re-learn from History (keeps explicit blocks and Not-For-Me choices). */
    fun rebuild() = refresh(rebuild = true)
}
