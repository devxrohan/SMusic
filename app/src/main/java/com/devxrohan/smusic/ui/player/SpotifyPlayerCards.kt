@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.devxrohan.smusic.R
import com.devxrohan.smusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.devxrohan.smusic.lyrics.LyricsEntry
import com.devxrohan.smusic.models.MediaMetadata
import com.devxrohan.smusic.utils.makeTimeString
import com.devxrohan.smusic.ui.utils.highQualityThumbnail

@Composable
internal fun SpotifyLyricsPreviewCard(
    lyrics: String?,
    position: Long,
    surfaceColor: Color,
    onClick: () -> Unit,
) {
    val lyricEntries = remember(lyrics) {
        when {
            lyrics.isNullOrBlank() || lyrics == LYRICS_NOT_FOUND -> emptyList()
            com.devxrohan.smusic.lyrics.SMusicLyricsUtils.isRecognizedTimedLyrics(lyrics) ->
                com.devxrohan.smusic.lyrics.SMusicLyricsUtils.parseAnyLyrics(lyrics)
            else -> lyrics.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .mapIndexed { index, line -> LyricsEntry(index * 2500L, line) }
        }
    }
    val activeLineIndex = remember(lyricEntries, position) {
        lyricEntries.indexOfLast { it.time <= position }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(activeLineIndex, lyricEntries.size) {
        if (lyricEntries.isNotEmpty()) {
            listState.animateScrollToItem(activeLineIndex.coerceAtMost(lyricEntries.lastIndex))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(surfaceColor.copy(alpha = 0.9f))
            .clickable(onClick = onClick)
            .padding(15.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.lyrics),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = SpotifyFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.ui_show),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }

            Spacer(Modifier.height(26.dp))

            if (lyricEntries.isEmpty()) {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.45f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = false,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(lyricEntries.size) { index ->
                            val isCurrent = index == activeLineIndex
                            val isPast = index < activeLineIndex
                            Text(
                                text = lyricEntries[index].text,
                                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = SpotifyFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                                color = when {
                                    isCurrent -> Color.White
                                    isPast -> Color.White.copy(alpha = 0.5f)
                                    else -> Color.White.copy(alpha = 0.25f)
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        item {
                            Spacer(Modifier.height(150.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = if (lyricEntries.isEmpty()) "" else "Line Synced\nLyrics provided by LRCLIB",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SpotifyFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Normal),
                color = Color.White.copy(alpha = 0.68f),
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
internal fun SpotifyArtistCard(
    artist: String,
    thumbnailUrl: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            placeholder = painterResource(R.drawable.music_note),
            error = painterResource(R.drawable.music_note),
            model = thumbnailUrl?.highQualityThumbnail(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.82f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.1f), Color.Black.copy(alpha = 0.66f))
                    )
                )
        )
        Text(
            text = stringResource(R.string.artists),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(28.dp),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(28.dp),
        ) {
            Text(
                text = artist,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.sort_by_artist),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
internal fun SpotifyDetailsCard(
    mediaMetadata: MediaMetadata,
    artists: String,
    surfaceColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(surfaceColor.copy(alpha = 0.9f))
            .clickable(onClick = onClick)
            .padding(28.dp),
    ) {
        Column {
            Text(
                text = mediaMetadata.album?.title?.takeIf { it.isNotBlank() } ?: mediaMetadata.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = makeTimeString(mediaMetadata.duration * 1000L),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = artists,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.62f),
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.ui_description),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SpotifyFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = listOf(mediaMetadata.title, artists).joinToString(" • "),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.62f),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.ui_more),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SpotifyFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}
