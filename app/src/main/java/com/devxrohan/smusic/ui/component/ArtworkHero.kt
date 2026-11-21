@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.component

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.constants.DisableBlurKey
import com.devxrohan.smusic.utils.isLowRamDevice
import com.devxrohan.smusic.utils.rememberPreference
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

/** Glass samples only this artwork, never the player or another screen's captured layer. */
@Composable
internal fun ArtworkHero(url: String?, caption: String, modifier: Modifier = Modifier, artist: Boolean = false) {
    val context = LocalContext.current
    val disableBlur by rememberPreference(DisableBlurKey, false)
    val useGlass = !disableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !context.isLowRamDevice()
    val backdrop = if (useGlass) rememberBackdrop() else null
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Box(modifier) {
        MusicArtwork(url, Modifier.fillMaxSize().let { if (backdrop != null) it.layerBackdrop(backdrop) else it }, artist)
        val labelModifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        if (backdrop != null) {
            Box(labelModifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    blur(12.dp.toPx())
                    lens(12.dp.toPx(), size.minDimension / 2f, true)
                },
                onDrawSurface = { drawRect(scheme.surface.copy(alpha = 0.72f)) },
            ).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(caption, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
            }
        } else {
            Surface(labelModifier, shape = shape, color = scheme.surfaceContainerHigh) {
                Text(caption, Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
            }
        }
    }
}
