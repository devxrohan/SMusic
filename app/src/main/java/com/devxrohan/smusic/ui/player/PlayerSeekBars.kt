@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.devxrohan.smusic.constants.SliderStyle
import com.devxrohan.smusic.ui.component.PlayerSliderTrack
import me.saket.squiggles.SquigglySlider

@Composable
fun CurvedProgressSlider(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val safeDuration = duration.takeIf { it > 0 } ?: 1000L
    val progress = (position.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    // Arc geometry constants
    val startAngle = 160f
    val sweepAngle = 220f
    val radiusDp = 110.dp
    val strokeWidthDp = 4.dp

    Box(
        modifier = modifier
            .size(260.dp)
            .pointerInput(safeDuration) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                        val angle = calculateAngle(offset, center)
                        val newProgress = calculateProgressFromAngle(angle, startAngle, sweepAngle)
                        onSeek((newProgress * safeDuration).toLong())
                    },
                    onDrag = { change, _ ->
                        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                        val angle = calculateAngle(change.position, center)
                        val newProgress = calculateProgressFromAngle(angle, startAngle, sweepAngle)
                        onSeek((newProgress * safeDuration).toLong())
                    },
                    onDragEnd = {
                        onSeekFinished()
                    }
                )
            }
            .pointerInput(safeDuration) {
                detectTapGestures { offset ->
                    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                    val angle = calculateAngle(offset, center)
                    val newProgress = calculateProgressFromAngle(angle, startAngle, sweepAngle)
                    onSeek((newProgress * safeDuration).toLong())
                    onSeekFinished()
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            canvasSize = size
            val radius = radiusDp.toPx()
            val strokeWidth = strokeWidthDp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val rect = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)

            // 1. Draw background arc track (thick glowing blue)
            drawArc(
                color = Color(0xFF0F1B5F).copy(alpha = 0.55f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )

            // 2. Draw active progress track (white)
            drawArc(
                color = Color.White,
                startAngle = startAngle,
                sweepAngle = sweepAngle * progress,
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // 3. Draw white thumb dot at the end of progress
            val thumbAngleRad = Math.toRadians((startAngle + sweepAngle * progress).toDouble())
            val thumbX = center.x + radius * Math.cos(thumbAngleRad).toFloat()
            val thumbY = center.y + radius * Math.sin(thumbAngleRad).toFloat()
            drawCircle(
                color = Color.White,
                radius = 7.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
            drawCircle(
                color = Color(0xFF0F1B5F),
                radius = 3.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
        }
    }
}

// Helpers for angle mapping
private fun calculateAngle(point: Offset, center: Offset): Float {
    val dx = point.x - center.x
    val dy = point.y - center.y
    var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (angle < 0) {
        angle += 360f
    }
    return angle
}

private fun calculateProgressFromAngle(angle: Float, startAngle: Float, sweepAngle: Float): Float {
    var normalizedAngle = angle
    if (normalizedAngle < startAngle - 40f) {
        normalizedAngle += 360f
    }
    return ((normalizedAngle - startAngle) / sweepAngle).coerceIn(0f, 1f)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ClassicSeekBar(
    progress: Float,
    style: SliderStyle,
    isPlaying: Boolean,
    colors: SliderColors,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val value = clampedProgress
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnSeekFinished by rememberUpdatedState(onSeekFinished)

    // Use the exact same Material/Squiggly/PlayerSliderTrack primitives as the
    // Player settings styles. Classic only overrides their colors to white;
    // it must not grow its own thumb/track geometry.
    when (style) {
        SliderStyle.DEFAULT -> {
            Slider(
                value = value,
                onValueChange = onSeek,
                onValueChangeFinished = onSeekFinished,
                colors = colors,
                modifier = modifier.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val inset = 16.dp.toPx()
                        val usableWidth = (size.width.toFloat() - inset * 2f).coerceAtLeast(1f)
                        val fraction = ((offset.x - inset) / usableWidth).coerceIn(0f, 1f)
                        currentOnSeek(fraction)
                        currentOnSeekFinished()
                    }
                },
            )
        }
        SliderStyle.SQUIGGLY -> {
            SquigglySlider(
                value = value,
                onValueChange = onSeek,
                onValueChangeFinished = onSeekFinished,
                colors = colors,
                modifier = modifier.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val inset = 16.dp.toPx()
                        val usableWidth = (size.width.toFloat() - inset * 2f).coerceAtLeast(1f)
                        val fraction = ((offset.x - inset) / usableWidth).coerceIn(0f, 1f)
                        currentOnSeek(fraction)
                        currentOnSeekFinished()
                    }
                },
                squigglesSpec = SquigglySlider.SquigglesSpec(
                    amplitude = if (isPlaying) 2.dp else 0.dp,
                    strokeWidth = 3.dp,
                ),
            )
        }
        SliderStyle.SLIM -> {
            Slider(
                value = value,
                onValueChange = onSeek,
                onValueChangeFinished = onSeekFinished,
                colors = colors,
                thumb = { Spacer(Modifier.size(0.dp)) },
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        colors = colors,
                    )
                },
                modifier = modifier.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val inset = 16.dp.toPx()
                        val usableWidth = (size.width.toFloat() - inset * 2f).coerceAtLeast(1f)
                        val fraction = ((offset.x - inset) / usableWidth).coerceIn(0f, 1f)
                        currentOnSeek(fraction)
                        currentOnSeekFinished()
                    }
                },
            )
        }
    }
}
