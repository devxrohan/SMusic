@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.db.entities.Song
import com.devxrohan.smusic.ui.component.MusicArtwork

/** Two tracks per shelf page keep recommendations readable without a wall of artwork cards. */
@Composable
internal fun PulseRotationShelf(songs: List<Song>, playingId: String?, onPlay: (Song) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val largeText = LocalDensity.current.fontScale >= 1.5f
        val pageWidth = if (largeText) maxWidth else (maxWidth - 24.dp).coerceAtMost(360.dp).coerceAtLeast(1.dp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(songs.chunked(2), key = { it.first().id }, contentType = { "song-pair" }) { page ->
                Column(Modifier.width(pageWidth), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val rowHeight = if (largeText) 112.dp else 92.dp
                    page.forEach { song ->
                        androidx.compose.material3.Surface(onClick = { onPlay(song) },
                            shape = MaterialTheme.shapes.large,
                            color = if (song.id == playingId) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().height(rowHeight)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MusicArtwork(song.thumbnailUrl, Modifier.size(52.dp).clip(MaterialTheme.shapes.medium))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(song.title, style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                    Text(song.artists.joinToString { it.name }, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    if (page.size == 1) Spacer(Modifier.height(rowHeight))
                }
            }
        }
    }
}
