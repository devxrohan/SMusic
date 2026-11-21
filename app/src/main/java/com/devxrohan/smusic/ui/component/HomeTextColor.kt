package com.devxrohan.smusic.ui.component

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Home-only media text override; other destinations keep their selected theme. */
internal val LocalHomeTextColor = staticCompositionLocalOf<Color?> { null }

@androidx.compose.runtime.Composable
internal fun homeContentColor(): Color =
    if (androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < .5f) Color.White else Color.Black
