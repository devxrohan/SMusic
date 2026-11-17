@file:OptIn(ExperimentalFoundationApi::class)

package com.devxrohan.smusic.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import coil.compose.AsyncImage
import com.devxrohan.smusic.LocalDownloadUtil
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.ListThumbnailSize
import com.devxrohan.smusic.constants.ThumbnailCornerRadius
import com.devxrohan.smusic.db.entities.Song
import com.devxrohan.smusic.models.MediaMetadata
import com.devxrohan.smusic.utils.joinByBullet
import com.devxrohan.smusic.utils.makeTimeString

@Composable
fun SongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    isSelected: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {
        if (showLikedIcon && song.song.liked) {
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
        if (showDownloadIcon) {
            val download by LocalDownloadUtil.current.getDownload(song.id)
                .collectAsState(initial = null)
            when (download?.state) {
                STATE_COMPLETED ->
                    Icon(
                        painter = painterResource(R.drawable.offline),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(18.dp)
                                .padding(end = 2.dp),
                    )

                STATE_QUEUED, STATE_DOWNLOADING ->
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier =
                            Modifier
                                .size(16.dp)
                                .padding(end = 2.dp),
                    )

                else -> {}
            }
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = song.song.title,
    subtitle =
        joinByBullet(
            song.artists.joinToString { it.name },
            makeTimeString(song.song.duration * 1000L),
        ),
    badges = badges,
    thumbnailContent = {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(ListThumbnailSize),
        ) {
            if (albumIndex != null) {
                AnimatedVisibility(
                    visible = !isActive,
                    enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                    exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut(),
                ) {
                    if (isSelected) {
                        Icon(
                            painter = painterResource(R.drawable.done),
                            modifier = Modifier.align(Alignment.Center),
                            contentDescription = null,
                        )
                    } else {
                        Text(
                            text = albumIndex.toString(),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            } else {
                if (isSelected) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .zIndex(1000f)
                                .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                .background(Color.Black.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.done),
                            modifier = Modifier.align(Alignment.Center),
                            contentDescription = null,
                        )
                    }
                }
                AsyncImage(
                    model = song.song.thumbnailUrl,
                    contentDescription = null,
                    placeholder = painterResource(R.drawable.music_note),
                    error = painterResource(R.drawable.music_note),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                )
            }

            PlayingIndicatorBox(
                isActive = isActive,
                playWhenReady = isPlaying,
                color = if (albumIndex != null) MaterialTheme.colorScheme.onBackground else Color.White,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            color =
                                if (albumIndex != null) {
                                    Color.Transparent
                                } else {
                                    Color.Black.copy(
                                        alpha = 0.4f,
                                    )
                                },
                            shape = RoundedCornerShape(ThumbnailCornerRadius),
                        ),
            )
        }
    },
    trailingContent = trailingContent,
    modifier = modifier,
    isActive = isActive,
)

@Composable
fun SongGridItem(
    song: Song,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        if (showLikedIcon && song.song.liked) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
        if (showDownloadIcon) {
            val download by LocalDownloadUtil.current.getDownload(song.id)
                .collectAsState(initial = null)
            when (download?.state) {
                STATE_COMPLETED -> Icon(
                    painter = painterResource(R.drawable.offline),
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 2.dp)
                )

                STATE_QUEUED, STATE_DOWNLOADING -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 2.dp)
                )

                else -> {}
            }
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = song.song.title,
    subtitle = joinByBullet(
        song.artists.joinToString { it.name },
        makeTimeString(song.song.duration * 1000L)
    ),
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            placeholder = painterResource(R.drawable.music_note),
            error = painterResource(R.drawable.music_note),
            model = song.song.thumbnailUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
        )

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            modifier =
                Modifier
                    .align(Alignment.Center),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            color = Color.Black.copy(alpha = if (isPlaying) 0.4f else 0f),
                            shape = RoundedCornerShape(ThumbnailCornerRadius),
                        ),
            ) {
                if (isPlaying) {
                    PlayingIndicator(
                        color = Color.White,
                        modifier = Modifier.height(24.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !(isActive && isPlaying),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
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
    modifier = modifier
)

@Composable
fun SongSmallGridItem(
    song: Song,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
) = SmallGridItem(
    title = song.song.title,
    thumbnailContent = {
        AsyncImage(
            placeholder = painterResource(R.drawable.music_note),
            error = painterResource(R.drawable.music_note),
            model = song.song.thumbnailUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
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
                            color = Color.Black.copy(alpha = if (isPlaying) 0.4f else 0f),
                            shape = RoundedCornerShape(ThumbnailCornerRadius),
                        ),
            ) {
                if (isPlaying) {
                    PlayingIndicator(
                        color = Color.White,
                        modifier = Modifier.height(24.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !(isActive && isPlaying),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
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
    modifier = modifier,
)
@Composable
fun MediaMetadataListItem(
    mediaMetadata: MediaMetadata,
    modifier: Modifier,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = mediaMetadata.title,
    subtitle =
        joinByBullet(
            mediaMetadata.artists.joinToString { it.name },
            makeTimeString(mediaMetadata.duration * 1000L),
        ),
    thumbnailContent = {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(ListThumbnailSize),
        ) {
            if (isSelected) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .zIndex(1000f)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                            .background(Color.Black.copy(alpha = 0.5f)),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.done),
                        modifier = Modifier.align(Alignment.Center),
                        contentDescription = null,
                    )
                }
            }

            AsyncImage(
                placeholder = painterResource(R.drawable.music_note),
                error = painterResource(R.drawable.music_note),
                model = mediaMetadata.thumbnailUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ThumbnailCornerRadius)),
            )

            PlayingIndicatorBox(
                isActive = isActive,
                playWhenReady = isPlaying,
                color = Color.White,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(ThumbnailCornerRadius),
                        ),
            )
        }
    },
    trailingContent = trailingContent,
    modifier = modifier,
    isActive = isActive,
)
@Composable
fun LocalSongsGrid(
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
        ) {
            AsyncImage(
                placeholder = painterResource(R.drawable.music_note),
                error = painterResource(R.drawable.music_note),
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
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
                visible = !(isActive && isPlaying),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                ) {
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
