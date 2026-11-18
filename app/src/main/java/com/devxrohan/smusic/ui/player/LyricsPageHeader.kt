package com.devxrohan.smusic.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.devxrohan.smusic.R
import com.devxrohan.smusic.models.MediaMetadata

@Composable
internal fun LyricsPageHeader(metadata: MediaMetadata, color: Color, shareEnabled: Boolean,
    onSelectLyrics: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AsyncImage(metadata.thumbnailUrl, null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.now_playing), style = MaterialTheme.typography.labelMedium,
                color = color.copy(alpha = 0.72f))
            Text(metadata.title, style = MaterialTheme.typography.titleSmall, color = color,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onSelectLyrics, enabled = shareEnabled) {
            Icon(painterResource(R.drawable.lyrics_share_select), stringResource(R.string.share_lyrics),
                tint = color.copy(alpha = if (shareEnabled) 1f else 0.38f))
        }
        IconButton(onClick = onMore) {
            Icon(painterResource(R.drawable.more_horiz), stringResource(R.string.more_options), tint = color)
        }
    }
}
