@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.devxrohan.smusic.ui.component

import com.devxrohan.smusic.ui.component.touchHaptic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Discrete settings controls share one native tick track and notch feedback. */
@Composable
internal fun DottedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thumb: @Composable (SliderState) -> Unit = {
        SliderDefaults.Thumb(interactionSource, colors = colors, enabled = enabled)
    },
    track: (@Composable (SliderState) -> Unit)? = null,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val divisions = if (steps > 0) steps + 1 else 20
    fun bucket(v: Float): Int {
        val span = valueRange.endInclusive - valueRange.start
        return if (span > 0f) (((v - valueRange.start) / span) * divisions).roundToInt() else 0
    }
    var lastBucket by remember(valueRange, steps) { mutableIntStateOf(bucket(value)) }
    val snappedSteps = if (steps > 0) steps else 19
    Slider(
        value = value, onValueChange = { next ->
            val index = bucket(next)
            if (index != lastBucket) {
                view.context.touchHaptic(tick = true)
                lastBucket = index
            }
            onValueChange(next)
        },
        modifier = modifier, enabled = enabled, valueRange = valueRange, steps = snappedSteps,
        onValueChangeFinished = onValueChangeFinished, colors = colors,
        interactionSource = interactionSource, thumb = thumb,
        track = { state -> SliderDefaults.Track(state, colors = colors, enabled = enabled) },
    )
}
