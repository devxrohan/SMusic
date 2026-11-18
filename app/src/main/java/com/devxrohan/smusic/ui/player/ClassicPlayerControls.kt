@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.ui.component.LyricsMiniKaraokeLine
import androidx.navigation.NavController
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.PlayerHorizontalPadding
import com.devxrohan.smusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.devxrohan.smusic.lyrics.LyricsEntry
import com.devxrohan.smusic.models.MediaMetadata
import com.devxrohan.smusic.utils.makeTimeString
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun ClassicControlsContent(
    mediaMetadata: MediaMetadata,
    dominantColor: Color,
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    position: Long,
    duration: Long,
    sliderPosition: Long?,
    onSliderPositionChange: (Long?) -> Unit,
    onSliderPositionChangeFinished: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    navController: NavController,
    onCollapse: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding + 8.dp)
    ) {
        Spacer(Modifier.height(28.dp))

        Text(
            text = mediaMetadata.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(enabled = mediaMetadata.album != null) {
                mediaMetadata.album?.let { navController.navigate("album/${it.id}") }
                onCollapse()
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = mediaMetadata.artists.joinToString { it.name },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(24.dp))

        Slider(
            value = (sliderPosition ?: position).toFloat(),
            valueRange = 0f..(duration.coerceAtLeast(1L)).toFloat(),
            onValueChange = { onSliderPositionChange(it.toLong()) },
            onValueChangeFinished = onSliderPositionChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = dominantColor,
                activeTrackColor = dominantColor,
                inactiveTrackColor = dominantColor.copy(alpha = 0.25f),
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = makeTimeString(sliderPosition ?: position),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = makeTimeString(duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, dominantColor.copy(alpha = if (canSkipPrevious) 0.6f else 0.2f), CircleShape)
                    .clickable(enabled = canSkipPrevious, onClick = onPrevious),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_previous),
                    contentDescription = null,
                    tint = dominantColor.copy(alpha = if (canSkipPrevious) 1f else 0.35f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(dominantColor)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, dominantColor.copy(alpha = if (canSkipNext) 0.6f else 0.2f), CircleShape)
                    .clickable(enabled = canSkipNext, onClick = onNext),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_next),
                    contentDescription = null,
                    tint = dominantColor.copy(alpha = if (canSkipNext) 1f else 0.35f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Compact lyric strip for the Classic player. Which animation plays is based
 * purely on whether the current line itself has real word-level timing — not
 * on which lyrics screen style (Karaoke/Classic) happens to be selected, so
 * it behaves the same on both pages: a word-timed line bounces with the
 * karaoke animation, and a line with no word timing pops/fades in as one
 * complete line (see ClassicWholeLineText) instead of just snapping to the
 * new text. The playhead is frame-smoothed so provider timestamps stay
 * responsive.
 */
@Composable
internal fun ClassicLyricLineStrip(
    mediaMetadata: MediaMetadata?,
    position: Long,
    duration: Long,
    sliderPosition: Long?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val lyrics = lyricsEntity?.lyrics
    val isLoading = lyricsEntity == null

    // This used to run its own independent lyrics fetch here whenever
    // lyricsEntity was still null, racing MusicService's own
    // fetch-on-song-start for the very same song — two independent provider
    // lookups can legitimately disagree, so this strip could end up showing
    // (and caching) different lyrics than the full lyrics screen for the
    // exact same song. It now just asks the single shared fetch path to make
    // sure a fetch is happening (a no-op if one already is), and otherwise
    // purely observes lyricsEntity above like every other lyrics screen does.
    LaunchedEffect(mediaMetadata?.id, lyricsEntity) {
        val metadata = mediaMetadata ?: return@LaunchedEffect
        if (lyricsEntity == null) {
            runCatching { playerConnection.service.ensureLyricsFetched(metadata) }
        }
    }

    val lines = remember(lyrics, duration) {
        if (lyrics == null || lyrics == LYRICS_NOT_FOUND) return@remember emptyList()
        val parsed = if (com.devxrohan.smusic.lyrics.SMusicLyricsUtils.isRecognizedTimedLyrics(lyrics)) {
            com.devxrohan.smusic.lyrics.SMusicLyricsUtils.parseAnyLyrics(lyrics)
        } else {
            lyrics.lines().filter { it.isNotBlank() }.map { LyricsEntry(-1L, it.trim()) }
        }.filter { it.text.isNotBlank() || it.sungUntilMs != null }

        // For untimed text, use a line-level timeline across the track so the
        // strip still advances from line to line roughly in time with the song.
        val allUntimed = parsed.isNotEmpty() && parsed.all { it.time < 0L }
        val resolved = if (allUntimed) {
            val trackDuration = duration.takeIf { it > 0L } ?: parsed.size * 2500L
            parsed.mapIndexed { index, entry ->
                entry.copy(time = trackDuration * index / parsed.size)
            }
        } else parsed
        if (resolved.isNotEmpty() && resolved.first().time >= 0L) {
            listOf(LyricsEntry.HEAD_LYRICS_ENTRY) + resolved
        } else resolved
    }

    val livePosition = rememberClassicLyricPosition(
        reportedPosition = position,
        seekingPosition = sliderPosition,
        isPlaying = isPlaying,
        trackKey = lyricsEntity?.id ?: "classic-lyrics",
    )

    if (isLoading) {
        // Keep a real text status in the player while the provider race is
        // running. Rotate the status softly instead of showing a skeleton or
        // waiting for the lyrics page to be opened.
        val loadingLines = remember {
            listOf(
                "Loading lyrics",
                "Finding lyrics",
                "Syncing lyrics",
                "Preparing lyrics",
            )
        }
        var loadingIndex by remember(mediaMetadata?.id) { mutableIntStateOf(0) }
        LaunchedEffect(mediaMetadata?.id) {
            while (isActive) {
                delay(1400L)
                loadingIndex = (loadingIndex + 1) % loadingLines.size
            }
        }
        AnimatedContent(
            targetState = loadingLines[loadingIndex],
            transitionSpec = {
                fadeIn(tween(220, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(tween(180, easing = FastOutSlowInEasing))
            },
            label = "classicLyricsLoadingText",
            modifier = modifier
                .fillMaxWidth()
                .height(28.dp)
                // Keep the strip's touch/layout height compact, but allow the
                // karaoke word's 4dp upward float to draw outside this box.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            )
        }
        return
    }

    if (lines.isEmpty()) return

    val currentIndex = remember(lines, livePosition) {
        if (lines.all { it.time < 0L }) {
            // Plain TTML has no timestamps; show its first lyric instead of
            // treating every line as already elapsed and jumping to the end.
            0
        } else {
            com.devxrohan.smusic.lyrics.LyricsUtils.findCurrentLineIndex(
                lines,
                livePosition,
            ).coerceIn(0, lines.lastIndex)
        }
    }
    val currentLine = lines[currentIndex]

    // The strip stays left-pinned; only real word-timed entries use karaoke motion.

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            // Do not clip the word-bounce animation at the strip boundary.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        // Anchor the lyric baseline toward the seek bar instead of vertically
        // centering it in the strip; this keeps the lyric visually attached
        // while preserving the slider's own style-specific geometry.
        contentAlignment = Alignment.BottomStart,
    ) {
        val wordTimed = !currentLine.words.isNullOrEmpty()
        if (wordTimed) {
            LyricsMiniKaraokeLine(
                words = currentLine.words.orEmpty(),
                positionMs = livePosition,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // Untimed text and line-only timestamps stay as one complete
            // line — same on both lyrics screen styles. No synthetic sweep
            // is stretched over gaps.
            ClassicWholeLineText(
                text = currentLine.text,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Renders a complete lyric line when no genuine word-level timing is available,
 * or when Classic lyrics screen style is selected. Pops and fades in on every
 * line change instead of cutting straight to the new text.
 */
@Composable
private fun ClassicWholeLineText(text: String, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            // A small "pop" — scale up from slightly smaller while fading in —
            // reads as a nicer entrance than a flat crossfade for a short line.
            (fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                androidx.compose.animation.scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                )) togetherWith
                fadeOut(tween(160, easing = FastOutSlowInEasing))
        },
        label = "classicLyricLine",
        modifier = modifier.fillMaxWidth(),
    ) { animatedText ->
        Text(
            text = animatedText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
        )
    }
}

@Composable
private fun rememberClassicLyricPosition(
    reportedPosition: Long,
    seekingPosition: Long?,
    isPlaying: Boolean,
    trackKey: Any,
): Long {
    var smoothPosition by remember(trackKey) { mutableLongStateOf(reportedPosition) }
    var anchorPosition by remember(trackKey) { mutableLongStateOf(reportedPosition) }
    var anchorNanos by remember(trackKey) { mutableLongStateOf(0L) }
    var lastReported by remember(trackKey) { mutableLongStateOf(reportedPosition) }

    val latestReported by rememberUpdatedState(reportedPosition)
    val latestSeeking by rememberUpdatedState(seekingPosition)
    val latestPlaying by rememberUpdatedState(isPlaying)

    LaunchedEffect(trackKey, isPlaying) {
        anchorPosition = latestSeeking ?: latestReported
        lastReported = latestReported
        anchorNanos = 0L

        while (true) {
            withFrameNanos { now ->
                val seek = latestSeeking
                if (seek != null) {
                    smoothPosition = seek
                    anchorPosition = seek
                    lastReported = latestReported
                    anchorNanos = now
                } else {
                    val reported = latestReported
                    if (reported != lastReported) {
                        // Media3 position callbacks are an anchor, not the
                        // animation clock. Re-anchor whenever the player gives
                        // us a new reading, then let the frame clock run between
                        // readings. This removes the visible 200–500 ms lyric lag.
                        anchorPosition = reported
                        lastReported = reported
                        anchorNanos = now
                        smoothPosition = reported
                    }
                    if (latestPlaying) {
                        if (anchorNanos == 0L) anchorNanos = now
                        val elapsedMs = ((now - anchorNanos).coerceAtLeast(0L) / 1_000_000L)
                        smoothPosition = anchorPosition + elapsedMs
                    } else {
                        smoothPosition = reported
                        anchorPosition = reported
                        anchorNanos = now
                    }
                }
            }
        }
    }

    return smoothPosition
}
