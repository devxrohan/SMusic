package com.devxrohan.smusic.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.devxrohan.smusic.ui.player.FluidBackground

@Composable
fun BlurredBackground(
    model: Any?,
    modifier: Modifier = Modifier,
    blurRadius: androidx.compose.ui.unit.Dp = 90.dp
) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxSize()
                .blur(blurRadius)
        )
    } else {
        FluidBackground(modifier = modifier.fillMaxSize())
    }
}
