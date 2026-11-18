@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.border
import com.devxrohan.smusic.db.MusicDatabase
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.db.entities.LyricsEntity
import com.devxrohan.smusic.models.MediaMetadata
import com.devxrohan.smusic.ui.component.BottomSheetState

@Composable
fun ConcentricWaveEffect(
    isPlaying: Boolean,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    if (colors.isEmpty()) return

    val infiniteTransition = rememberInfiniteTransition(label = "wave")

    // Slower animation (8 seconds instead of 5)
    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 8000,   // ← slow speed
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveProgress"
    )

    Canvas(modifier = modifier) {

        val circleCount = 24   // ← increased from 14 to 24
        val maxRadius = size.minDimension / 2f

        repeat(circleCount) { index ->

            // 🚫 If paused → freeze animation
            val progress = if (isPlaying) {
                (animatedProgress + index / circleCount.toFloat()) % 1f
            } else {
                0f
            }

            val radius = maxRadius * progress

            drawCircle(
                color = colors[index % colors.size]
                    .copy(alpha = if (isPlaying) 1f - progress else 0.15f),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

@Composable
fun LiquidPlayer(
    state: BottomSheetState,
    mediaMetadata: MediaMetadata?,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    repeatMode: Int,
    shuffleModeEnabled: Boolean,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onCollapse: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenMenu: () -> Unit,
    currentLyrics: LyricsEntity?,
    database: MusicDatabase,
    dynamicColor: Color? = null,
    gradientColors: List<Color> = emptyList(),
) {
    FuturisticPlayer(
        state = state,
        mediaMetadata = mediaMetadata,
        position = position,
        duration = duration,
        isPlaying = isPlaying,
        isLoading = isLoading,
        canSkipPrevious = canSkipPrevious,
        canSkipNext = canSkipNext,
        repeatMode = repeatMode,
        shuffleModeEnabled = shuffleModeEnabled,
        onSeek = onSeek,
        onSeekFinished = onSeekFinished,
        onPlayPause = onPlayPause,
        onPrevious = onPrevious,
        onNext = onNext,
        onShuffle = onShuffle,
        onRepeat = onRepeat,
        onOpenLyrics = onOpenLyrics,
        onOpenQueue = onOpenQueue,
        onCollapse = onCollapse,
        onOpenMenu = onOpenMenu,
        dynamicColor = dynamicColor,
        gradientColors = gradientColors
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudGlowPlayer(
    state: BottomSheetState,
    mediaMetadata: MediaMetadata?,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    repeatMode: Int,
    shuffleModeEnabled: Boolean,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onCollapse: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenMenu: () -> Unit,
    currentLyrics: LyricsEntity?,
    database: MusicDatabase,
    dynamicColor: Color? = null,
    gradientColors: List<Color> = emptyList(),
) {
    CloudGlowPlayerScreen(
        state = state,
        mediaMetadata = mediaMetadata,
        position = position,
        duration = duration,
        isPlaying = isPlaying,
        isLoading = isLoading,
        canSkipPrevious = canSkipPrevious,
        canSkipNext = canSkipNext,
        repeatMode = repeatMode,
        shuffleModeEnabled = shuffleModeEnabled,
        onSeek = onSeek,
        onSeekFinished = onSeekFinished,
        onPlayPause = onPlayPause,
        onPrevious = onPrevious,
        onNext = onNext,
        onShuffle = onShuffle,
        onRepeat = onRepeat,
        onOpenLyrics = onOpenLyrics,
        onOpenQueue = onOpenQueue,
        onCollapse = onCollapse,
        onOpenMenu = onOpenMenu,
        dynamicColor = dynamicColor,
        gradientColors = gradientColors
    )
}

@Composable
fun ConcentricGlowRings(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "rings")
    val pulseScale1 by transition.animateFloat(
        initialValue = 1f,
        targetValue = 2.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1Scale"
    )
    val pulseAlpha1 by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1Alpha"
    )

    val pulseScale2 by transition.animateFloat(
        initialValue = 1f,
        targetValue = 2.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearOutSlowInEasing),
            initialStartOffset = StartOffset(1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2Scale"
    )
    val pulseAlpha2 by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearOutSlowInEasing),
            initialStartOffset = StartOffset(1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2Alpha"
    )

    Box(
        modifier = modifier.size(180.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isPlaying) {
            // Outer pulsing ring 1
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .graphicsLayer {
                        scaleX = pulseScale1
                        scaleY = pulseScale1
                        alpha = pulseAlpha1
                    }
                    .border(
                        width = 1.5.dp,
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF00E5FF), Color(0xFF0055FF).copy(alpha = 0.1f), Color.Transparent),
                        ),
                        shape = CircleShape
                    )
            )

            // Outer pulsing ring 2
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .graphicsLayer {
                        scaleX = pulseScale2
                        scaleY = pulseScale2
                        alpha = pulseAlpha2
                    }
                    .border(
                        width = 1.5.dp,
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF00E5FF), Color(0xFF0055FF).copy(alpha = 0.1f), Color.Transparent),
                        ),
                        shape = CircleShape
                    )
            )
        }

        // Static glowing base ring
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0055FF).copy(alpha = 0.3f),
                            Color(0xFF0F1B5F).copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                    ),
                    shape = CircleShape
                )
        )
    }
}

@Composable
fun LiquidControlBarBackground(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.height(64.dp).fillMaxWidth()) {
        val h = size.height
        val w = size.width
        val r = h / 2f
        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(w / 2f - 60.dp.toPx(), 0f)
            // Dip down in the center
            cubicTo(
                w / 2f - 30.dp.toPx(), 0f,
                w / 2f - 35.dp.toPx(), h * 0.45f,
                w / 2f, h * 0.45f
            )
            cubicTo(
                w / 2f + 35.dp.toPx(), h * 0.45f,
                w / 2f + 30.dp.toPx(), 0f,
                w / 2f + 60.dp.toPx(), 0f
            )
            lineTo(w - r, 0f)
            // Right cap
            arcTo(
                rect = Rect(w - 2 * r, 0f, w, h),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(w / 2f + 60.dp.toPx(), h)
            // Dip up in the center
            cubicTo(
                w / 2f + 30.dp.toPx(), h,
                w / 2f + 35.dp.toPx(), h * 1.45f,
                w / 2f, h * 1.45f
            )
            cubicTo(
                w / 2f - 35.dp.toPx(), h * 1.45f,
                w / 2f - 30.dp.toPx(), h,
                w / 2f - 60.dp.toPx(), h
            )
            lineTo(r, h)
            // Left cap
            arcTo(
                rect = Rect(0f, 0f, 2 * r, h),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            close()
        }

        // Draw background glass
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.05f)
        )
        // Draw soft glowing cyan/blue outer border (wide)
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF00E5FF).copy(alpha = 0.25f),
                    Color(0xFF0055FF).copy(alpha = 0.25f),
                    Color.Transparent
                )
            ),
            style = Stroke(width = 4.dp.toPx())
        )
        // Draw sharp glowing cyan/blue inner border (thin)
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF00E5FF).copy(alpha = 0.65f),
                    Color(0xFF0055FF).copy(alpha = 0.65f)
                )
            ),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}
