package com.devxrohan.smusic.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Resolves only the next item in playback order, including shuffle. */
internal class MusicOnlyQueueController(
    private val player: Player,
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean,
    private val resolver: MusicOnlyResolver,
) : Player.Listener {
    private var job: Job? = null
    private var replacing = false

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (!replacing && reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) refresh()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh()
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = refresh()
    override fun onRepeatModeChanged(repeatMode: Int) = refresh()

    fun refresh() {
        job?.cancel()
        if (!enabled()) return
        val timeline = player.currentTimeline
        val current = player.currentMediaItemIndex
        if (timeline.isEmpty || player.repeatMode == Player.REPEAT_MODE_ONE) return
        val slots = buildList {
            var index = current
            repeat(1) {
                index = timeline.getNextWindowIndex(index, player.repeatMode, player.shuffleModeEnabled)
                if (index == C.INDEX_UNSET || index == current) return@buildList
                add(index to player.getMediaItemAt(index))
            }
        }
        job = scope.launch {
            for ((index, original) in slots) {
                val answer = resolver.resolve(original)
                if (!enabled()) break
                if (index >= player.mediaItemCount || index == player.currentMediaItemIndex ||
                    player.getMediaItemAt(index) !== original || answer.mediaId == original.mediaId) continue
                replacing = true
                try {
                    player.replaceMediaItem(index, answer)
                } finally {
                    replacing = false
                }
            }
        }
    }

    fun close() {
        job?.cancel()
        player.removeListener(this)
    }
}
