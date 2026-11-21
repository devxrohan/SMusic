@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import com.google.material.color.hct.Hct
import com.google.material.color.scheme.SchemeTonalSpot

/** The fixed SMusic palette used in 2.1.1; app presets and artwork seeds stay outside. */
@Composable
internal fun PlayerIdentityTheme(darkTheme: Boolean, pureBlack: Boolean, content: @Composable () -> Unit) {
    val palette = remember(darkTheme, pureBlack) {
        SchemeTonalSpot(Hct.fromInt(DefaultThemeColor.toArgb()), darkTheme, 0.0)
            .toColorScheme().pureBlack(pureBlack, darkTheme)
    }
    CompositionLocalProvider(LocalContentColor provides palette.onSurface) {
        MaterialExpressiveTheme(
            colorScheme = palette,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            motionScheme = MaterialTheme.motionScheme,
            content = content,
        )
    }
}
