package com.devxrohan.smusic.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import dev.jeziellago.compose.markdowntext.MarkdownText

/** Render the original GitHub body consistently on every update surface. */
@Composable
internal fun ReleaseNotes(markdown: String, modifier: Modifier = Modifier) {
    val rendered = remember(markdown) { com.devxrohan.smusic.update.releaseNotesMarkup(markdown) }
    MarkdownText(
        markdown = rendered,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        imageLoader = LocalContext.current.imageLoader,
        isTextSelectable = true,
    )
}
