package com.devxrohan.smusic.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmapOrNull
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/** Keeps the last successful colour during loading; stale artwork jobs are cancelled. */
@Composable
internal fun rememberArtworkTheme(url: String?, enabled: Boolean, refresh: Int): Color {
    val context = LocalContext.current
    var colour by remember { mutableStateOf(DefaultThemeColor) }
    LaunchedEffect(url, enabled, refresh) {
        if (!enabled || url.isNullOrBlank()) return@LaunchedEffect
        try {
            val result = context.imageLoader.execute(ImageRequest.Builder(context)
                .data(url).allowHardware(false).size(160).build())
            val bitmap = result.drawable?.toBitmapOrNull() ?: return@LaunchedEffect
            colour = withContext(Dispatchers.Default) { bitmap.extractThemeColor() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Preserve the last valid palette while offline. */ }
    }
    return colour
}
