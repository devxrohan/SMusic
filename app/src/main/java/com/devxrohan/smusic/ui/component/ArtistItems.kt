@file:OptIn(ExperimentalFoundationApi::class)

package com.devxrohan.smusic.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.ListThumbnailSize
import com.devxrohan.smusic.db.entities.Artist

@Composable
fun ArtistListItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            Icon(
                painter = painterResource(
                    if (artist.artist.isYouTubeArtist) R.drawable.subscribed else R.drawable.favorite
                ),
                contentDescription = null,
                tint = if (artist.artist.isYouTubeArtist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .size(18.dp)
                        .padding(end = 2.dp),
            )
        }
    },
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = artist.artist.name,
    subtitle = if (artist.songCount > 0) pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount) else null,
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            placeholder = painterResource(R.drawable.person),
            error = painterResource(R.drawable.person),
            model = artist.artist.thumbnailUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .size(ListThumbnailSize)
                    .clip(CircleShape),
        )
    },
    trailingContent = trailingContent,
    modifier = modifier,
)

@Composable
fun ArtistGridItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            Icon(
                painter = painterResource(
                    if (artist.artist.isYouTubeArtist) R.drawable.subscribed else R.drawable.favorite
                ),
                contentDescription = null,
                tint = if (artist.artist.isYouTubeArtist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .size(18.dp)
                        .padding(end = 2.dp),
            )
        }
    },
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = artist.artist.name,
    subtitle = if (artist.songCount > 0) pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount) else "",
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            placeholder = painterResource(R.drawable.person),
            error = painterResource(R.drawable.person),
            model = artist.artist.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),

            )
    },
    thumbnailShape = CircleShape,
    fillMaxWidth = fillMaxWidth,
    modifier = modifier,
)

@Composable
fun ArtistSmallGridItem(
    artist: Artist,
    modifier: Modifier = Modifier,
) = SmallGridItem(
    title = artist.artist.name,
    thumbnailContent = {
        AsyncImage(
            placeholder = painterResource(R.drawable.person),
            error = painterResource(R.drawable.person),
            model = artist.artist.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    },
    thumbnailShape = CircleShape,
    modifier = modifier,
    isArtist = true,
)
@Composable
fun LocalArtistsGrid(
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
                .fillMaxSize()
                .clip(CircleShape),
        ) {
            AsyncImage(
                placeholder = painterResource(R.drawable.person),
                error = painterResource(R.drawable.person),
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
                                shape = CircleShape,
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
        }
    },
    thumbnailShape = CircleShape,
    fillMaxWidth = fillMaxWidth,
    modifier = modifier,
)
