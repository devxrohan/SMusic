@file:OptIn(ExperimentalFoundationApi::class)

@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.devxrohan.smusic.LocalDatabase
import com.devxrohan.smusic.LocalDownloadUtil
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.ListThumbnailSize
import com.devxrohan.smusic.constants.ThumbnailCornerRadius
import com.devxrohan.smusic.db.entities.Album
import com.devxrohan.smusic.db.entities.Song
import com.devxrohan.smusic.playback.queues.LocalAlbumRadio
import com.devxrohan.smusic.ui.theme.extractThemeColor
import com.devxrohan.smusic.utils.joinByBullet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AlbumListItem(
    album: Album,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val downloadUtil = LocalDownloadUtil.current
        var songs by remember {
            mutableStateOf(emptyList<Song>())
        }

        LaunchedEffect(Unit) {
            database.albumSongs(album.id).collect {
                songs = it
            }
        }

        var downloadState by remember {
            mutableStateOf(Download.STATE_STOPPED)
        }

        LaunchedEffect(songs) {
            if (songs.isEmpty()) return@LaunchedEffect
            downloadUtil.downloads.collect { downloads ->
                downloadState =
                    if (songs.all { downloads[it.id]?.state == STATE_COMPLETED }) {
                        STATE_COMPLETED
                    } else if (songs.all {
                            downloads[it.id]?.state == STATE_QUEUED ||
                                    downloads[it.id]?.state == STATE_DOWNLOADING ||
                                    downloads[it.id]?.state == STATE_COMPLETED
                        }
                    ) {
                        STATE_DOWNLOADING
                    } else {
                        Download.STATE_STOPPED
                    }
            }
        }

        if (showLikedIcon && album.album.bookmarkedAt != null) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .size(18.dp)
                        .padding(end = 2.dp),
            )
        }

        when (downloadState) {
            STATE_COMPLETED ->
                Icon(
                    painter = painterResource(R.drawable.offline),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(18.dp)
                            .padding(end = 2.dp),
                )

            STATE_DOWNLOADING ->
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier =
                        Modifier
                            .size(16.dp)
                            .padding(end = 2.dp),
                )

            else -> {}
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = album.album.title,
    subtitle =
        joinByBullet(
            album.artists.joinToString { it.name },
            pluralStringResource(R.plurals.n_song, album.album.songCount, album.album.songCount),
            album.album.year?.toString(),
        ),
    badges = badges,
    thumbnailContent = {
        val database = LocalDatabase.current
        val coroutineScope = rememberCoroutineScope()

        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(album.album.thumbnailUrl)
                    .allowHardware(false)
                    .build(),
            contentDescription = null,
            onState = { state ->
                if (album.album.themeColor == null && state is AsyncImagePainter.State.Success) {
                    coroutineScope.launch(Dispatchers.IO) {
                        state.result.drawable.toBitmapOrNull()?.extractThemeColor()?.toArgb()
                            ?.let { color ->
                                database.query {
                                    update(album.album.copy(themeColor = color))
                                }
                            }
                    }
                }
            },
            modifier =
                Modifier
                    .size(ListThumbnailSize)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
        )

        PlayingIndicatorBox(
            isActive = isActive,
            playWhenReady = isPlaying,
            modifier =
                Modifier
                    .size(ListThumbnailSize)
                    .background(
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(ThumbnailCornerRadius),
                    ),
        )
    },
    trailingContent = trailingContent,
    modifier = modifier,
)

@Composable
fun AlbumGridItem(
    album: Album,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val downloadUtil = LocalDownloadUtil.current
        var songs by remember {
            mutableStateOf(emptyList<Song>())
        }

        LaunchedEffect(Unit) {
            database.albumSongs(album.id).collect {
                songs = it
            }
        }

        var downloadState by remember {
            mutableStateOf(Download.STATE_STOPPED)
        }

        LaunchedEffect(songs) {
            if (songs.isEmpty()) return@LaunchedEffect
            downloadUtil.downloads.collect { downloads ->
                downloadState =
                    if (songs.all { downloads[it.id]?.state == STATE_COMPLETED }) {
                        STATE_COMPLETED
                    } else if (songs.all {
                            downloads[it.id]?.state == STATE_QUEUED ||
                                    downloads[it.id]?.state == STATE_DOWNLOADING ||
                                    downloads[it.id]?.state == STATE_COMPLETED
                        }
                    ) {
                        STATE_DOWNLOADING
                    } else {
                        Download.STATE_STOPPED
                    }
            }
        }

        if (album.album.bookmarkedAt != null) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .size(18.dp)
                        .padding(end = 2.dp),
            )
        }

        when (downloadState) {
            STATE_COMPLETED ->
                Icon(
                    painter = painterResource(R.drawable.offline),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(18.dp)
                            .padding(end = 2.dp),
                )

            STATE_DOWNLOADING ->
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier =
                        Modifier
                            .size(16.dp)
                            .padding(end = 2.dp),
                )

            else -> {}
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = album.album.title,
    subtitle = album.artists.joinToString { it.name },
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            placeholder = painterResource(R.drawable.music_note),
            error = painterResource(R.drawable.music_note),
            model = album.album.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(ThumbnailCornerRadius),
                        ),
            ) {
                if (isPlaying) {
                    PlayingIndicator(
                        color = Color.White,
                        modifier = Modifier.height(24.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !isActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
        ) {
            val database = LocalDatabase.current
            val playerConnection = LocalPlayerConnection.current ?: return@AnimatedVisibility

            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable {
                            coroutineScope.launch {
                                database.albumWithSongs(album.id).first()?.let { albumWithSongs ->
                                    playerConnection.playQueue(
                                        LocalAlbumRadio(albumWithSongs)
                                    )
                                }
                            }
                        },
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    },
    thumbnailShape = RoundedCornerShape(ThumbnailCornerRadius),
    fillMaxWidth = fillMaxWidth,
    modifier = modifier,
)

@Composable
fun AlbumSmallGridItem(
    song: Song,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
) = song.song.albumName?.let {
    SmallGridItem(
        title = it,
        thumbnailContent = {
            AsyncImage(
                placeholder = painterResource(R.drawable.music_note),
                error = painterResource(R.drawable.music_note),
                model = song.song.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )

            AnimatedVisibility(
                visible = isActive,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                color = Color.Black.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(ThumbnailCornerRadius),
                            ),
                ) {
                    if (isPlaying) {
                        PlayingIndicator(
                            color = Color.White,
                            modifier = Modifier.height(24.dp),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                }
            }
        },
        thumbnailShape = RoundedCornerShape(ThumbnailCornerRadius),
        modifier = modifier,
    )
}
@Composable
fun LocalAlbumsGrid(
    title: String,
    subtitle: String,
    badges:
    @Composable()
    (RowScope.() -> Unit) = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    modifier: Modifier,
) = GridItem(
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            placeholder = painterResource(R.drawable.music_note),
            error = painterResource(R.drawable.music_note),
            model = thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(ThumbnailCornerRadius),
                        ),
            ) {
                if (isPlaying) {
                    PlayingIndicator(
                        color = Color.White,
                        modifier = Modifier.height(24.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }
    },
    thumbnailShape = RoundedCornerShape(ThumbnailCornerRadius),
    fillMaxWidth = fillMaxWidth,
    modifier = modifier,
)
