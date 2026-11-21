package com.devxrohan.smusic.ui.theme

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.devxrohan.smusic.constants.DisableAnimationsKey
import com.devxrohan.smusic.utils.rememberPreference

private data class PaletteBlend(val start: ColorScheme, val end: ColorScheme, val fraction: Float)

/** One frame clock for the palette, retaining the displayed colour on interrupted transitions. */
@Composable
internal fun animateAppColorScheme(target: ColorScheme): ColorScheme {
    val reduceMotion by rememberPreference(DisableAnimationsKey, false)
    if (reduceMotion) return target
    var blend by remember { mutableStateOf(PaletteBlend(target, target, 1f)) }
    val current = interpolateAppPalette(blend.start, blend.end, blend.fraction)
    val displayed by rememberUpdatedState(current)
    LaunchedEffect(target) {
        if (blend.end === target && blend.fraction >= 1f) return@LaunchedEffect
        val start = displayed
        if (samePalette(start, target)) {
            blend = PaletteBlend(target, target, 1f)
            return@LaunchedEffect
        }
        animate(initialValue = 0f, targetValue = 1f, animationSpec = tween(1100)) { fraction, _ ->
            blend = PaletteBlend(start, target, fraction)
        }
    }
    return current
}

/** sRGB channel blending avoids repeated colour-space conversions on every frame. */
private fun blendRole(start: Color, end: Color, fraction: Float): Color {
    if (start == end) return start
    val a = start.toArgb()
    val b = end.toArgb()
    fun channel(shift: Int): Int {
        val first = (a ushr shift) and 255
        val last = (b ushr shift) and 255
        return (first + (last - first) * fraction + .5f).toInt().coerceIn(0, 255) shl shift
    }
    return Color(channel(24) or channel(16) or channel(8) or channel(0))
}

internal fun interpolateAppPalette(start: ColorScheme, end: ColorScheme, fraction: Float): ColorScheme {
    if (fraction <= 0f) return start
    if (fraction >= 1f) return end
    return start.copy(
        primary = blendRole(start.primary, end.primary, fraction),
        onPrimary = blendRole(start.onPrimary, end.onPrimary, fraction),
        primaryContainer = blendRole(start.primaryContainer, end.primaryContainer, fraction),
        onPrimaryContainer = blendRole(start.onPrimaryContainer, end.onPrimaryContainer, fraction),
        inversePrimary = blendRole(start.inversePrimary, end.inversePrimary, fraction),
        secondary = blendRole(start.secondary, end.secondary, fraction),
        onSecondary = blendRole(start.onSecondary, end.onSecondary, fraction),
        secondaryContainer = blendRole(start.secondaryContainer, end.secondaryContainer, fraction),
        onSecondaryContainer = blendRole(start.onSecondaryContainer, end.onSecondaryContainer, fraction),
        tertiary = blendRole(start.tertiary, end.tertiary, fraction),
        onTertiary = blendRole(start.onTertiary, end.onTertiary, fraction),
        tertiaryContainer = blendRole(start.tertiaryContainer, end.tertiaryContainer, fraction),
        onTertiaryContainer = blendRole(start.onTertiaryContainer, end.onTertiaryContainer, fraction),
        background = blendRole(start.background, end.background, fraction),
        onBackground = blendRole(start.onBackground, end.onBackground, fraction),
        surface = blendRole(start.surface, end.surface, fraction),
        onSurface = blendRole(start.onSurface, end.onSurface, fraction),
        surfaceVariant = blendRole(start.surfaceVariant, end.surfaceVariant, fraction),
        onSurfaceVariant = blendRole(start.onSurfaceVariant, end.onSurfaceVariant, fraction),
        surfaceTint = blendRole(start.surfaceTint, end.surfaceTint, fraction),
        inverseSurface = blendRole(start.inverseSurface, end.inverseSurface, fraction),
        inverseOnSurface = blendRole(start.inverseOnSurface, end.inverseOnSurface, fraction),
        error = blendRole(start.error, end.error, fraction),
        onError = blendRole(start.onError, end.onError, fraction),
        errorContainer = blendRole(start.errorContainer, end.errorContainer, fraction),
        onErrorContainer = blendRole(start.onErrorContainer, end.onErrorContainer, fraction),
        outline = blendRole(start.outline, end.outline, fraction),
        outlineVariant = blendRole(start.outlineVariant, end.outlineVariant, fraction),
        scrim = blendRole(start.scrim, end.scrim, fraction),
        surfaceBright = blendRole(start.surfaceBright, end.surfaceBright, fraction),
        surfaceDim = blendRole(start.surfaceDim, end.surfaceDim, fraction),
        surfaceContainer = blendRole(start.surfaceContainer, end.surfaceContainer, fraction),
        surfaceContainerHigh = blendRole(start.surfaceContainerHigh, end.surfaceContainerHigh, fraction),
        surfaceContainerHighest = blendRole(start.surfaceContainerHighest, end.surfaceContainerHighest, fraction),
        surfaceContainerLow = blendRole(start.surfaceContainerLow, end.surfaceContainerLow, fraction),
        surfaceContainerLowest = blendRole(start.surfaceContainerLowest, end.surfaceContainerLowest, fraction),
        primaryFixed = blendRole(start.primaryFixed, end.primaryFixed, fraction),
        primaryFixedDim = blendRole(start.primaryFixedDim, end.primaryFixedDim, fraction),
        onPrimaryFixed = blendRole(start.onPrimaryFixed, end.onPrimaryFixed, fraction),
        onPrimaryFixedVariant = blendRole(start.onPrimaryFixedVariant, end.onPrimaryFixedVariant, fraction),
        secondaryFixed = blendRole(start.secondaryFixed, end.secondaryFixed, fraction),
        secondaryFixedDim = blendRole(start.secondaryFixedDim, end.secondaryFixedDim, fraction),
        onSecondaryFixed = blendRole(start.onSecondaryFixed, end.onSecondaryFixed, fraction),
        onSecondaryFixedVariant = blendRole(start.onSecondaryFixedVariant, end.onSecondaryFixedVariant, fraction),
        tertiaryFixed = blendRole(start.tertiaryFixed, end.tertiaryFixed, fraction),
        tertiaryFixedDim = blendRole(start.tertiaryFixedDim, end.tertiaryFixedDim, fraction),
        onTertiaryFixed = blendRole(start.onTertiaryFixed, end.onTertiaryFixed, fraction),
        onTertiaryFixedVariant = blendRole(start.onTertiaryFixedVariant, end.onTertiaryFixedVariant, fraction),
    )
}

private fun samePalette(start: ColorScheme, end: ColorScheme): Boolean =
    start.primary == end.primary &&
    start.onPrimary == end.onPrimary &&
    start.primaryContainer == end.primaryContainer &&
    start.onPrimaryContainer == end.onPrimaryContainer &&
    start.inversePrimary == end.inversePrimary &&
    start.secondary == end.secondary &&
    start.onSecondary == end.onSecondary &&
    start.secondaryContainer == end.secondaryContainer &&
    start.onSecondaryContainer == end.onSecondaryContainer &&
    start.tertiary == end.tertiary &&
    start.onTertiary == end.onTertiary &&
    start.tertiaryContainer == end.tertiaryContainer &&
    start.onTertiaryContainer == end.onTertiaryContainer &&
    start.background == end.background &&
    start.onBackground == end.onBackground &&
    start.surface == end.surface &&
    start.onSurface == end.onSurface &&
    start.surfaceVariant == end.surfaceVariant &&
    start.onSurfaceVariant == end.onSurfaceVariant &&
    start.surfaceTint == end.surfaceTint &&
    start.inverseSurface == end.inverseSurface &&
    start.inverseOnSurface == end.inverseOnSurface &&
    start.error == end.error &&
    start.onError == end.onError &&
    start.errorContainer == end.errorContainer &&
    start.onErrorContainer == end.onErrorContainer &&
    start.outline == end.outline &&
    start.outlineVariant == end.outlineVariant &&
    start.scrim == end.scrim &&
    start.surfaceBright == end.surfaceBright &&
    start.surfaceDim == end.surfaceDim &&
    start.surfaceContainer == end.surfaceContainer &&
    start.surfaceContainerHigh == end.surfaceContainerHigh &&
    start.surfaceContainerHighest == end.surfaceContainerHighest &&
    start.surfaceContainerLow == end.surfaceContainerLow &&
    start.surfaceContainerLowest == end.surfaceContainerLowest &&
    start.primaryFixed == end.primaryFixed &&
    start.primaryFixedDim == end.primaryFixedDim &&
    start.onPrimaryFixed == end.onPrimaryFixed &&
    start.onPrimaryFixedVariant == end.onPrimaryFixedVariant &&
    start.secondaryFixed == end.secondaryFixed &&
    start.secondaryFixedDim == end.secondaryFixedDim &&
    start.onSecondaryFixed == end.onSecondaryFixed &&
    start.onSecondaryFixedVariant == end.onSecondaryFixedVariant &&
    start.tertiaryFixed == end.tertiaryFixed &&
    start.tertiaryFixedDim == end.tertiaryFixedDim &&
    start.onTertiaryFixed == end.onTertiaryFixed &&
    start.onTertiaryFixedVariant == end.onTertiaryFixedVariant
