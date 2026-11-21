package com.devxrohan.smusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.devxrohan.smusic.R

/** The flower is a separate layer so photos remain upright while it moves. */
@Composable
internal fun ProfileAvatar(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    animateFlower: Boolean = false,
) {
    val flower = rememberArtworkFlower()
    val color = MaterialTheme.colorScheme.primaryContainer
    var loaded by remember(model) { mutableStateOf(false) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val backdrop: @Composable () -> Unit = {
            Box(Modifier.size(size).background(color, flower))
        }
        if (animateFlower) ProfileBeatFrame(content = backdrop) else backdrop()
        Box(Modifier.size(size * .72f).clip(CircleShape), contentAlignment = Alignment.Center) {
            if (model == null || !loaded) Icon(painterResource(R.drawable.person),
                contentDescription, Modifier.size(size * .54f), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            if (model != null) AsyncImage(
                model = model, contentDescription = contentDescription,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onSuccess = { loaded = true }, onError = { loaded = false },
            )
        }
    }
}
