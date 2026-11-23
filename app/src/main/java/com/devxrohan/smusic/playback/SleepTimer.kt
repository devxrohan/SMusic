package com.devxrohan.smusic.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

class SleepTimer(
    private val scope: CoroutineScope,
    var player: Player,
) : Player.Listener {
    private var sleepTimerJob: Job? = null
    var triggerTime by mutableStateOf(-1L)
        private set
    var pauseWhenSongEnd by mutableStateOf(false)
        private set
    var tracksRemaining by mutableStateOf(0)
        private set
    val isActive: Boolean
        get() = triggerTime != -1L || pauseWhenSongEnd || tracksRemaining > 0

    fun startTrackCount(count: Int) {
        require(count in 1..100) { "Choose between 1 and 100 tracks" }
        clear()
        tracksRemaining = count
    }

    fun start(minute: Int) {
        clear()
        if (minute == -1) {
            pauseWhenSongEnd = true
        } else if (minute > 0) {
            triggerTime = System.currentTimeMillis() + minute.minutes.inWholeMilliseconds
            sleepTimerJob =
                scope.launch {
                    delay(minute.minutes)
                    player.pause()
                    triggerTime = -1L
                    sleepTimerJob = null
                }
        }
    }

    fun clear() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        pauseWhenSongEnd = false
        tracksRemaining = 0
        triggerTime = -1L
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        if (tracksRemaining > 0 && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)) {
            tracksRemaining -= 1
            if (tracksRemaining == 0) { clear(); player.pause() }
            return
        }
        if (pauseWhenSongEnd && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)) {
            clear()
            player.pause()
        }
    }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        if (playbackState == Player.STATE_ENDED && (pauseWhenSongEnd || tracksRemaining > 0)) {
            clear()
            player.pause()
        }
    }
}
