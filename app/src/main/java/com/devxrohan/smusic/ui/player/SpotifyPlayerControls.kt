@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.constants.PlayerBackgroundStyle
import com.devxrohan.smusic.constants.SliderStyle
import com.devxrohan.smusic.ui.component.PlayerSliderTrack
import com.devxrohan.smusic.ui.theme.PlayerSliderColors
import me.saket.squiggles.SquigglySlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpotifyStyledSlider(
    value: Float,
    duration: Long,
    sliderStyle: SliderStyle,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val valueRange = 0f..duration.coerceAtLeast(1L).toFloat()
    val sliderColors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color.White,
        inactiveTrackColor = Color.White.copy(alpha = 0.26f),
    )

    when (sliderStyle) {
        SliderStyle.DEFAULT -> {
            Slider(
                value = value,
                valueRange = valueRange,
                onValueChange = { onSeek(it.toLong()) },
                onValueChangeFinished = onSeekFinished,
                colors = sliderColors,
                modifier = modifier,
            )
        }

        SliderStyle.SQUIGGLY -> {
            SquigglySlider(
                value = value,
                valueRange = valueRange,
                onValueChange = { onSeek(it.toLong()) },
                onValueChangeFinished = onSeekFinished,
                colors = PlayerSliderColors.getSliderColors(
                    textButtonColor = Color.White,
                    playerBackground = PlayerBackgroundStyle.DEFAULT,
                    useDarkTheme = true,
                ),
                modifier = modifier,
                squigglesSpec = SquigglySlider.SquigglesSpec(
                    amplitude = if (isPlaying) 2.dp else 0.dp,
                    strokeWidth = 3.dp,
                ),
            )
        }

        SliderStyle.SLIM -> {
            Slider(
                value = value,
                valueRange = valueRange,
                onValueChange = { onSeek(it.toLong()) },
                onValueChangeFinished = onSeekFinished,
                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        colors = sliderColors,
                    )
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
internal fun SpotifyRoundIconButton(
    icon: Int,
    transparent: Boolean = false,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (transparent) Color.Transparent else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(23.dp)
        )
    }
}

@Composable
internal fun SpotifyPlainIconButton(
    icon: Int,
    onClick: () -> Unit,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(30.dp)
        )
    }
}
