package com.devxrohan.smusic.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Low-opacity accent wash above content; no artwork allocation or blur layer. */
@Composable
internal fun Modifier.themeGlow(): Modifier {
    val accent = MaterialTheme.colorScheme.primary
    return drawWithCache {
        val glow = Brush.verticalGradient(listOf(accent.copy(alpha = .1f), Color.Transparent),
            endY = size.height * .4f)
        onDrawBehind { drawRect(glow) }
    }
}
