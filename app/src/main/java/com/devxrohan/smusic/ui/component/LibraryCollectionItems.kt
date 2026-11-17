@file:OptIn(ExperimentalFoundationApi::class)

package com.devxrohan.smusic.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmapOrNull
import coil.imageLoader
import coil.request.ImageRequest
import com.devxrohan.smusic.R
import com.devxrohan.smusic.db.entities.Playlist
import com.devxrohan.smusic.ui.theme.extractThemeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LibraryPlaylistFeatureCard(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    autoPlaylist: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val subtitleText = playlistFeatureCountText(playlist = playlist, autoPlaylist = autoPlaylist)
    val thumbnailSize = LibraryCardThumbnailSize
    val thumbnailShape = RoundedCornerShape(18.dp)
    val context = LocalContext.current
    val primaryThumbnailUrl = playlist.thumbnails.getOrNull(0)
    var extractedGlowColor by remember(primaryThumbnailUrl) { mutableStateOf(Color.Transparent) }
    val glowColor by animateColorAsState(
        targetValue = extractedGlowColor,
        animationSpec = tween(240),
        label = "playlistItemGlow",
    )

    LaunchedEffect(primaryThumbnailUrl) {
        if (primaryThumbnailUrl == null) return@LaunchedEffect
        val bitmap =
            runCatching {
                context.imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(primaryThumbnailUrl)
                        .build()
                ).drawable?.toBitmapOrNull()
            }.getOrNull() ?: return@LaunchedEffect
        extractedGlowColor = withContext(Dispatchers.Default) { bitmap.extractThemeColor() }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = shape,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            glowColor.copy(alpha = 0.16f),
                            Color.Transparent,
                        )
                    )
                )
                .padding(12.dp),
        ) {
            PlaylistThumbnail(
                thumbnails = playlist.thumbnails,
                size = thumbnailSize,
                placeHolder = {
                    Icon(
                        painter = painterResource(
                            playlistFeaturePlaceholderIcon(
                                playlist,
                                autoPlaylist
                            )
                        ),
                        contentDescription = null,
                        tint = LocalContentColor.current.copy(alpha = 0.8f),
                        modifier = Modifier.size(thumbnailSize / 2),
                    )
                },
                shape = thumbnailShape,
            )

            Spacer(Modifier.width(16.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = playlist.playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitleText.isNotBlank()) {
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                trailingContent()
            }
        }
    }
}

enum class GridPosition {
    LEFT, RIGHT, SINGLE
}

@Composable
fun LibraryHeroFavoriteTile(
    title: String,
    @DrawableRes iconRes: Int,
    badgeText: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val animatedColor by animateColorAsState(accentColor, spring(), label = "heroPlaylistTile")

    Card(
        shape = RoundedCornerShape(
            topStart = 38.dp,
            topEnd = 12.dp,
            bottomEnd = 38.dp,
            bottomStart = 38.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 24.dp,
                            topEnd = 12.dp,
                            bottomEnd = 24.dp,
                            bottomStart = 12.dp
                        )
                    )
                    .background(animatedColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = if (iconRes == R.drawable.youtube_logo || iconRes == R.drawable.spotify) Color.Unspecified else animatedColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = badgeText.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                            )
                        )
                    },
                    shape = CircleShape,
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    border = null,
                    modifier = Modifier.height(24.dp)
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryPinnedCollectionTile(
    title: String,
    @DrawableRes iconRes: Int,
    gridPosition: GridPosition,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val animatedColor by animateColorAsState(accentColor, spring(), label = "pinnedPlaylistTile")

    val expressiveCardShape = when (gridPosition) {
        GridPosition.LEFT -> RoundedCornerShape(
            topStart = 28.dp,
            bottomStart = 28.dp,
            topEnd = 6.dp,
            bottomEnd = 6.dp
        )
        GridPosition.RIGHT -> RoundedCornerShape(
            topStart = 6.dp,
            bottomStart = 6.dp,
            topEnd = 28.dp,
            bottomEnd = 28.dp
        )
        GridPosition.SINGLE -> RoundedCornerShape(28.dp)
    }

    Card(
        shape = expressiveCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 8.dp,
                            bottomEnd = 16.dp,
                            bottomStart = 8.dp
                        )
                    )
                    .background(animatedColor.copy(alpha = 0.10f))
                    .padding(10.dp)
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = if (iconRes == R.drawable.youtube_logo || iconRes == R.drawable.spotify) Color.Unspecified else animatedColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
