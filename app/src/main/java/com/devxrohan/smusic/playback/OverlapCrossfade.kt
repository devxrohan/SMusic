@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlin.math.cos
import kotlin.math.sin

/** The next track buffers on a separate deck while the session player finishes the current track. */
internal class OverlapCrossfade(
    private var main: ExoPlayer,
    private val scope: CoroutineScope,
    private val createDeck: () -> ExoPlayer,
    private val seconds: () -> Int,
    private val volume: () -> Float,
    private val allowed: () -> Boolean,
    private val skipSilence: () -> Boolean,
    private val promote: (ExoPlayer, ExoPlayer) -> Unit,
) : Player.Listener {
    private var watch: Job? = null
    private var mix: Job? = null
    private var tail: ExoPlayer? = null
    private var attempted: String? = null
    private var closed = false
    private var incomingId: String? = null
    private var generation = 0L

    fun refreshSilenceGate() {
        val window = seconds().coerceIn(0, 12) * 1000L
        val remaining = main.duration - main.currentPosition
        val nearFade = window > 0 && allowed() && main.playWhenReady &&
            main.repeatMode != Player.REPEAT_MODE_ONE && main.hasNextMediaItem() &&
            main.duration > window * 2 && remaining in 0..(window + 8_000)
        val enabled = skipSilence() && !nearFade && incomingId == null
        if (main.skipSilenceEnabled != enabled) main.skipSilenceEnabled = enabled
    }

    fun refresh() {
        refreshSilenceGate()
        if (seconds() <= 0 || !main.playWhenReady || !allowed()) {
            watch?.cancel(); watch = null
            abort()
            return
        }
        if (watch?.isActive == true || !main.isPlaying) return
        watch = scope.launch {
            while (isActive && main.playWhenReady && seconds() > 0 && allowed()) {
                refreshSilenceGate()
                val item = main.currentMediaItem
                val duration = main.duration
                val remaining = duration - main.currentPosition
                val window = seconds().coerceIn(0, 12) * 1000L
                if (mix?.isActive != true && item != null && attempted != item.mediaId && main.isPlaying &&
                    duration != C.TIME_UNSET && duration > window * 2 && remaining in 1..(window + 6_000) &&
                    main.repeatMode != Player.REPEAT_MODE_ONE && main.hasNextMediaItem()) {
                    attempted = item.mediaId
                    start(item, window)
                }
                delay(250)
            }
        }
    }

    private fun start(item: MediaItem, window: Long) {
        val currentGeneration = ++generation
        mix = scope.launch {
            var deck: ExoPlayer? = null
            try {
                val next = main.nextMediaItemIndex
                val nextItem = main.getMediaItemAt(next)
                val incoming = createDeck()
                deck = incoming
                tail = incoming
                incoming.skipSilenceEnabled = skipSilence()
                incoming.volume = 0f
                incoming.playbackParameters = main.playbackParameters
                incoming.setMediaItem(nextItem)
                incoming.prepare()
                fun stillNext() = main.currentMediaItem === item &&
                    main.nextMediaItemIndex == next && main.getMediaItemAt(next) === nextItem
                while (stillNext() && main.isPlaying && allowed() && incoming.playerError == null &&
                    (incoming.playbackState != Player.STATE_READY || main.duration - main.currentPosition > window)) {
                    delay(40)
                }
                if (!stillNext() || !main.isPlaying || !allowed() ||
                    incoming.playbackState != Player.STATE_READY) return@launch
                if (main.duration - main.currentPosition < 400) return@launch
                incomingId = nextItem.mediaId
                incoming.play()
                // Leave the deck muted while it starts output and processes leading silence.
                val started = withTimeoutOrNull(1_500L) {
                    while (stillNext() && main.isPlaying && incoming.playerError == null &&
                        (!incoming.isPlaying || incoming.currentPosition < 100L)) delay(20)
                    stillNext() && incoming.isPlaying && incoming.currentPosition >= 100L
                } == true
                if (!started) return@launch
                val overlap = (main.duration - main.currentPosition).coerceAtMost(window)
                if (overlap < 200) return@launch
                val start = SystemClock.elapsedRealtime()
                while (stillNext() && main.isPlaying && incoming.isPlaying && allowed()) {
                    val progress = ((SystemClock.elapsedRealtime() - start).toFloat() * main.playbackParameters.speed / overlap).coerceIn(0f, 1f)
                    val angle = progress * (Math.PI / 2).toFloat()
                    main.volume = volume() * cos(angle)
                    incoming.volume = volume() * sin(angle)
                    if (progress >= 1f || main.duration - main.currentPosition <= 85) break
                    delay(20)
                }
                val outgoing = main
                val naturallyAdvanced = outgoing.currentMediaItem === nextItem &&
                    outgoing.currentMediaItemIndex == next
                if ((!stillNext() && !naturallyAdvanced) || !outgoing.playWhenReady ||
                    !incoming.isPlaying || !allowed()) return@launch
                val items = (0 until outgoing.mediaItemCount).map(outgoing::getMediaItemAt)
                val timeline = outgoing.currentTimeline
                val order = IntArray(items.size)
                var index = timeline.getFirstWindowIndex(outgoing.shuffleModeEnabled)
                for (position in order.indices) {
                    if (index == C.INDEX_UNSET) return@launch
                    order[position] = index
                    index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, outgoing.shuffleModeEnabled)
                }
                // Insert around the active item so its renderer keeps playing at the same position.
                incoming.addMediaItems(0, items.take(next))
                incoming.addMediaItems(items.drop(next + 1))
                incoming.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(order, 0L))
                incoming.shuffleModeEnabled = outgoing.shuffleModeEnabled
                incoming.repeatMode = outgoing.repeatMode
                outgoing.removeListener(this@OverlapCrossfade)
                outgoing.volume = 0f
                incoming.volume = volume()
                tail = null
                main = incoming
                incoming.addListener(this@OverlapCrossfade)
                attempted = null
                promote(outgoing, incoming)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Keep the main player's normal transition on deck errors. */ }
            finally {
                if (tail === deck) { deck?.release(); tail = null }
                if (currentGeneration == generation) {
                    incomingId = null
                    if (!closed) {
                        main.volume = volume()
                        refreshSilenceGate()
                    }
                }
            }
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (!player.playWhenReady || player.playerError != null || player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) abort()
        refresh()
    }
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && mediaItem?.mediaId == incomingId &&
            tail?.isPlaying == true) {
            main.volume = 0f
        }
        if (mediaItem?.mediaId != incomingId) {
            abort()
            attempted = null
        }
    }
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION && newPosition.mediaItem?.mediaId == incomingId) return
        if (mix?.isActive == true) abort()
    }
    fun abort() {
        val active = mix?.isActive == true || tail != null
        ++generation
        incomingId = null
        mix?.cancel(); mix = null
        tail?.release(); tail = null
        if (active && !closed) main.volume = volume()
    }
    fun close() { closed = true; watch?.cancel(); abort(); tail?.release(); tail = null; main.removeListener(this) }
}
