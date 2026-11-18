@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.SliderStyle
import com.devxrohan.smusic.db.entities.ArtistEntity
import com.devxrohan.smusic.models.MediaMetadata
import com.devxrohan.smusic.utils.makeTimeString
import com.devxrohan.smusic.ui.utils.highQualityThumbnail

@Composable
internal fun SpotifyPlayerBackdrop(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(Color.Black)) {
        AsyncImage(
            placeholder = painterResource(R.drawable.music_note),
            error = painterResource(R.drawable.music_note),
            model = thumbnailUrl?.highQualityThumbnail(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(110.dp)
                .alpha(0.42f)
                .scale(1.14f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.1f),
                            0.42f to Color.Black.copy(alpha = 0.5f),
                            1f to Color.Black,
                        )
                    )
                )
        )
    }
}

@Composable
internal fun SpotifyPlayerContent(
    mediaMetadata: MediaMetadata,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    repeatMode: Int,
    sliderStyle: SliderStyle,
    lyricsPreview: String?,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onStartRadio: () -> Unit,
    onCollapse: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenMenu: () -> Unit,
    isLiked: Boolean,
    onLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowDetails: () -> Unit,
    shuffleModeEnabled: Boolean,
    currentSongArtists: List<ArtistEntity>,
    surfaceColor: Color,
) {
    val safeDuration = duration.takeIf { it > 0 } ?: 0L
    val loadingPulseTransition = rememberInfiniteTransition(label = "loadingPulse")
    val loadingPulseAlpha by loadingPulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loadingPulseAlpha"
    )
    val playbackVisualAlpha = if (isLoading) loadingPulseAlpha else 1f
    val sliderValue = if (safeDuration > 0) position.coerceIn(0L, safeDuration).toFloat() else 0f
    val artists = mediaMetadata.artists.joinToString { it.name }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpotifyRoundIconButton(
                icon = R.drawable.expand_more,
                transparent = true,
                onClick = onCollapse,
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.now_playing).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpotifyFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    text = stringResource(R.string.ui_in_search, mediaMetadata.title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SpotifyFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier,
                )
            }
            SpotifyRoundIconButton(
                icon = R.drawable.more_vert,
                transparent = true,
                onClick = onOpenMenu,
            )
        }
    }

    Spacer(Modifier.height(78.dp))

    AsyncImage(
        placeholder = painterResource(R.drawable.music_note),
        error = painterResource(R.drawable.music_note),
        model = mediaMetadata.thumbnailUrl?.highQualityThumbnail(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
    )

    Spacer(Modifier.height(54.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mediaMetadata.title,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = SpotifyFontFamily, fontSize = 25.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = artists,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = SpotifyFontFamily, fontSize = 18.sp, fontWeight = FontWeight.Normal),
                color = Color.White.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier            )
        }
        SpotifyRoundIconButton(
            icon = if (isLiked) R.drawable.favorite else R.drawable.favorite_border,
            transparent = true,
            tint = if (isLiked) Color(0xFF1DB954) else Color.White,
            onClick = onLike
        )
    }

    Spacer(Modifier.height(18.dp))

    SpotifyStyledSlider(
        value = sliderValue,
        duration = safeDuration,
        sliderStyle = sliderStyle,
        isPlaying = isPlaying,
        onSeek = onSeek,
        onSeekFinished = onSeekFinished,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(playbackVisualAlpha),
    )

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = makeTimeString(position.coerceAtLeast(0L)),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.68f),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = makeTimeString(safeDuration),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.68f),
        )
    }

    Spacer(Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpotifyPlainIconButton(
            if (shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle,
            onShuffle,
            if (shuffleModeEnabled) Color(0xFF1DB954) else Color.White,
        )
        SpotifyPlainIconButton(
            R.drawable.skip_previous,
            onPrevious,
            if (canSkipPrevious) Color.White else Color.White.copy(alpha = 0.32f),
        )
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(CircleShape)
                .background(Color.White)
                .alpha(playbackVisualAlpha)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(if (isPlaying && !isLoading) R.drawable.pause else R.drawable.play),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.Black),
                modifier = Modifier.size(38.dp)
            )
        }
        SpotifyPlainIconButton(
            R.drawable.skip_next,
            onNext,
            if (canSkipNext) Color.White else Color.White.copy(alpha = 0.32f),
        )
        SpotifyPlainIconButton(
            if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat,
            onRepeat,
            if (repeatMode == Player.REPEAT_MODE_OFF) Color.White else Color(0xFF1DB954),
        )
    }

    Spacer(Modifier.height(22.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpotifyRoundIconButton(icon = R.drawable.info, transparent = true, onClick = onShowDetails)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            SpotifyRoundIconButton(icon = R.drawable.playlist_add, transparent = true, onClick = onAddToPlaylist)
            SpotifyRoundIconButton(icon = R.drawable.queue_music, transparent = true, onClick = onOpenQueue)
        }
    }

    Spacer(Modifier.height(26.dp))

    SpotifyLyricsPreviewCard(
        lyrics = lyricsPreview,
        position = position,
        surfaceColor = surfaceColor,
        onClick = onOpenLyrics,
    )

    Spacer(Modifier.height(12.dp))

    mediaMetadata.artists.forEach { artist ->
        val artistEntity = currentSongArtists.find { it.id == artist.id }
        val thumbnailUrl = artistEntity?.thumbnailUrl?.takeIf { it.isNotBlank() } ?: mediaMetadata.thumbnailUrl
        SpotifyArtistCard(
            artist = artist.name,
            thumbnailUrl = thumbnailUrl,
            onClick = {
                artist.id?.let { artistId ->
                    onOpenArtist(artistId)
                }
            },
        )
        Spacer(Modifier.height(12.dp))
    }

    Spacer(Modifier.height(12.dp))

    SpotifyDetailsCard(
        mediaMetadata = mediaMetadata,
        artists = artists,
        surfaceColor = surfaceColor,
        onClick = onOpenMenu,
    )
}

@Composable
internal fun SpotifyCollapsedHeader(
    mediaMetadata: MediaMetadata,
    surfaceColor: Color,
    isLiked: Boolean,
    onLike: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor.copy(alpha = 0.96f))
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .padding(horizontal = 36.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            placeholder = painterResource(R.drawable.music_note),
            error = painterResource(R.drawable.music_note),
            model = mediaMetadata.thumbnailUrl?.highQualityThumbnail(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(45.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mediaMetadata.title,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpotifyFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = mediaMetadata.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SpotifyFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Normal),
                color = Color.White.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier,
            )
        }
        Spacer(Modifier.width(22.dp))
        SpotifyRoundIconButton(
            icon = if (isLiked) R.drawable.favorite else R.drawable.favorite_border,
            transparent = true,
            tint = if (isLiked) Color(0xFF1DB954) else Color.White,
            onClick = onLike
        )
        Spacer(Modifier.width(18.dp))
        SpotifyRoundIconButton(icon = R.drawable.more_vert, transparent = true, onClick = onOpenMenu)
    }
}
