@file:OptIn(ExperimentalFoundationApi::class)

package com.devxrohan.smusic.ui.component

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import coil.compose.AsyncImage
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.ListThumbnailSize
import com.devxrohan.smusic.constants.ThumbnailCornerRadius
import com.devxrohan.smusic.db.entities.Playlist
import com.devxrohan.smusic.utils.getPlaylistImageUri

@Composable
fun PlaylistListItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    trailingContent: @Composable RowScope.() -> Unit = {},
    autoPlaylist: Boolean = false,
) = ListItem(
    title = playlist.playlist.name,
    subtitle = if (autoPlaylist) {
        ""
    } else {
        if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
            pluralStringResource(
                R.plurals.n_song,
                playlist.playlist.remoteSongCount,
                playlist.playlist.remoteSongCount
            )
        } else {
            pluralStringResource(R.plurals.n_song, playlist.songCount, playlist.songCount)
        }
    },
    thumbnailContent = {
        val painter =
            when (playlist.playlist.name) {
                stringResource(R.string.liked) -> R.drawable.favorite_border
                stringResource(R.string.offline) -> R.drawable.offline
                stringResource(R.string.cached_playlist) -> R.drawable.cached
                stringResource(R.string.filter_local) -> R.drawable.folder
                "Import Playlist", stringResource(R.string.import_playlist) -> R.drawable.music_note
                "Import YouTube" -> R.drawable.youtube_logo
                else -> {
                    if (autoPlaylist) {
                        R.drawable.trending_up
                    } else {
                        R.drawable.queue_music
                    }
                }
            }
        when (playlist.thumbnails.size) {
            0 ->
                Box(
                    modifier =
                        Modifier
                            .size(ListThumbnailSize)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Icon(
                        painter = painterResource(painter),
                        contentDescription = null,
                        tint = if (painter == R.drawable.youtube_logo) Color.Unspecified else LocalContentColor.current,
                        modifier = Modifier
                            .size(ListThumbnailSize / 2)
                            .align(Alignment.Center)
                    )
                }

            1 ->
                AsyncImage(
                    placeholder = painterResource(R.drawable.music_note),
                    error = painterResource(R.drawable.music_note),
                    model = browseArtworkUrl(playlist.thumbnails[0]),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(ListThumbnailSize)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                )

            else ->
                Box(
                    modifier =
                        Modifier
                            .size(ListThumbnailSize)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius)),
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
                            model = browseArtworkUrl(playlist.thumbnails.getOrNull(index)),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .align(alignment)
                                    .size(ListThumbnailSize / 2),
                        )
                    }
                }
        }
    },
    trailingContent = trailingContent,
    modifier = modifier,
)

@Composable
fun PlaylistGridItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = { },
    fillMaxWidth: Boolean = false,
    autoPlaylist: Boolean = false,
    context: Context // Agregamos el contexto para obtener la URI de la imagen
) = GridItem(
    title = playlist.playlist.name,
    subtitle = if (autoPlaylist) {
        ""
    } else {
        if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
            pluralStringResource(
                R.plurals.n_song,
                playlist.playlist.remoteSongCount,
                playlist.playlist.remoteSongCount
            )
        } else {
            pluralStringResource(R.plurals.n_song, playlist.songCount, playlist.songCount)
        }
    },
    badges = badges,
    thumbnailContent = {
        val thumbnailUri =
            getPlaylistImageUri(context, playlist.playlist.id) // Obtener URI de la miniatura

        if (thumbnailUri != null) {
            // Si la URI de la imagen existe, la mostramos
            AsyncImage(
                placeholder = painterResource(R.drawable.music_note),
                error = painterResource(R.drawable.music_note),
                model = thumbnailUri,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ThumbnailCornerRadius)),
            )
        } else {
            // Si no hay miniatura, mostrar la imagen predeterminada
            val painter =
                when (playlist.playlist.name) {
                    stringResource(R.string.liked) -> R.drawable.favorite_border
                    stringResource(R.string.offline) -> R.drawable.offline
                    stringResource(R.string.cached_playlist) -> R.drawable.cached
                    stringResource(R.string.filter_local) -> R.drawable.folder
                    else -> {
                        if (autoPlaylist) {
                            R.drawable.trending_up
                        } else {
                            R.drawable.queue_music
                        }
                    }
                }
            val width = maxWidth
            val Libcarditem = 25.dp
            when (playlist.thumbnails.size) {
                0 ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clip(RoundedCornerShape(Libcarditem))
                    ) {
                        Icon(
                            painter = painterResource(painter),
                            contentDescription = null,
                            tint = LocalContentColor.current.copy(alpha = 0.8f),
                            modifier =
                                Modifier
                                    .size(width / 2)
                                    .align(Alignment.Center),
                        )
                    }

                1 ->
                    AsyncImage(
                        placeholder = painterResource(R.drawable.music_note),
                        error = painterResource(R.drawable.music_note),
                        model = browseArtworkUrl(playlist.thumbnails[0]),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                    )

                else ->
                    Box(
                        modifier =
                            Modifier
                                .size(width)
                                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
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
                                model = browseArtworkUrl(playlist.thumbnails.getOrNull(index)),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .align(alignment)
                                        .size(width / 2),
                            )
                        }
                    }
            }
        }
    },
    thumbnailShape = RoundedCornerShape(ThumbnailCornerRadius),
    fillMaxWidth = fillMaxWidth,
    modifier = modifier,
)
