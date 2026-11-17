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
import com.devxrohan.smusic.innertube.models.AlbumItem
import com.devxrohan.smusic.innertube.models.ArtistItem
import com.devxrohan.smusic.innertube.models.PlaylistItem
import com.devxrohan.smusic.innertube.models.SongItem
import com.devxrohan.smusic.innertube.models.YTItem
import com.devxrohan.smusic.LocalDatabase
import com.devxrohan.smusic.LocalDownloadUtil
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.ListThumbnailSize
import com.devxrohan.smusic.constants.ThumbnailCornerRadius
import com.devxrohan.smusic.utils.joinByBullet
import com.devxrohan.smusic.utils.makeTimeString
import kotlinx.coroutines.CoroutineScope

@Composable
fun YouTubeListItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val song by database.song(item.id).collectAsState(initial = null)
        val album by database.album(item.id).collectAsState(initial = null)

        if (item is SongItem &&
            song?.song?.liked == true ||
            item is AlbumItem &&
            album?.album?.bookmarkedAt != null
        ) {
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
        if (item.explicit) {
            Icon(
                painter = painterResource(R.drawable.explicit),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(18.dp)
                        .padding(end = 2.dp),
            )
        }
        if (item is SongItem) {
            val downloads by LocalDownloadUtil.current.downloads.collectAsState()
            when (downloads[item.id]?.state) {
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
    title = item.title,
    subtitle =
        when (item) {
            is SongItem -> joinByBullet(
                item.artists.joinToString { it.name },
                makeTimeString(item.duration?.times(1000L))
            )

            is AlbumItem -> joinByBullet(
                item.artists?.joinToString { it.name },
                item.year?.toString()
            )

            is ArtistItem -> null
            is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
        },
    badges = badges,
    thumbnailContent = {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(ListThumbnailSize),
        ) {
            val thumbnailShape =
                if (item is ArtistItem) CircleShape else RoundedCornerShape(ThumbnailCornerRadius)
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
                    placeholder = painterResource(R.drawable.music_note),
                    error = painterResource(R.drawable.music_note),
                    model = item.thumbnail,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(thumbnailShape),
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
                            shape = thumbnailShape,
                        ),
            )
        }
    },
    trailingContent = trailingContent,
    modifier = modifier,
    isActive = isActive,
)
@Composable
fun YouTubeSmallGridItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope? = null,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = SmallGridItem(
    title = item.title,
    thumbnailContent = {
        AsyncImage(
            placeholder = painterResource(R.drawable.music_note),
            error = painterResource(R.drawable.music_note),
            model = item.thumbnail,
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
        )
        if (item is SongItem) {
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
        }
    },
    thumbnailShape =
        when (item) {
            is ArtistItem -> CircleShape
            else -> RoundedCornerShape(ThumbnailCornerRadius)
        },
    modifier = modifier,
    isArtist =
        when (item) {
            is ArtistItem -> true
            else -> false
        },
)
