@file:OptIn(ExperimentalFoundationApi::class)

package com.devxrohan.smusic.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import coil.compose.AsyncImage
import com.devxrohan.smusic.R
import com.devxrohan.smusic.db.entities.Playlist

internal val LibraryCardThumbnailSize = 72.dp

@Composable
internal fun playlistFeatureCountText(
    playlist: Playlist,
    autoPlaylist: Boolean,
): String =
    if (autoPlaylist) {
        ""
    } else if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
        pluralStringResource(
            R.plurals.n_song,
            playlist.playlist.remoteSongCount,
            playlist.playlist.remoteSongCount
        )
    } else {
        pluralStringResource(R.plurals.n_song, playlist.songCount, playlist.songCount)
    }

@Composable
internal fun playlistFeaturePlaceholderIcon(
    playlist: Playlist,
    autoPlaylist: Boolean,
): Int =
    when (playlist.playlist.name) {
        stringResource(R.string.liked) -> R.drawable.favorite_border
        stringResource(R.string.offline) -> R.drawable.offline
        stringResource(R.string.cached_playlist) -> R.drawable.cached
        stringResource(R.string.filter_local) -> R.drawable.folder
        else -> if (autoPlaylist) R.drawable.trending_up else R.drawable.queue_music
    }

@Composable
internal fun PlaylistThumbnail(
    thumbnails: List<String>,
    size: androidx.compose.ui.unit.Dp,
    shape: Shape,
    placeHolder: @Composable () -> Unit,
) {
    when (thumbnails.size) {
        0 -> Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            placeHolder()
        }

        1 -> AsyncImage(
            placeholder = painterResource(R.drawable.music_note),
            error = painterResource(R.drawable.music_note),
            model = browseArtworkUrl(thumbnails[0]),
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(shape),
            contentScale = ContentScale.Crop,
        )

        else -> Box(
            modifier = Modifier
                .size(size)
                .clip(shape),
        ) {
            listOf(
                Alignment.TopStart,
                Alignment.TopEnd,
                Alignment.BottomStart,
                Alignment.BottomEnd,
            ).fastForEachIndexed { index, alignment ->
                AsyncImage(
                    placeholder = painterResource(R.drawable.music_note),
                    error = painterResource(R.drawable.music_note),
                    model = browseArtworkUrl(thumbnails.getOrNull(index)),
                    contentDescription = null,
                    modifier = Modifier
                        .align(alignment)
                        .size(size / 2),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}
