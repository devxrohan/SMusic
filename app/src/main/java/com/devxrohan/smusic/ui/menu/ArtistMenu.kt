package com.devxrohan.smusic.ui.menu

import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.LocalDatabase
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.ArtistSongSortType
import com.devxrohan.smusic.db.entities.Artist
import com.devxrohan.smusic.extensions.toMediaItem
import com.devxrohan.smusic.playback.queues.ListQueue
import com.devxrohan.smusic.ui.component.ArtistListItem
import com.devxrohan.smusic.ui.component.GridMenu
import com.devxrohan.smusic.ui.component.GridMenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ArtistMenu(
    originalArtist: Artist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val artistState = database.artist(originalArtist.id).collectAsState(initial = originalArtist)
    val artist = artistState.value ?: originalArtist

    ArtistListItem(
        artist = artist,
        badges = {},
        trailingContent = {
            IconButton(
                onClick = {
                    val subscribe = artist.artist.bookmarkedAt == null
                    database.transaction {
                        update(artist.artist.toggleLike())
                    }
                    coroutineScope.launch(Dispatchers.IO) {
                        playerConnection.service.pulseEngine.onArtistSubscription(artist.id, subscribe)
                    }
                },
            ) {
                Icon(
                    painter = painterResource(if (artist.artist.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border),
                    tint = if (artist.artist.bookmarkedAt != null) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    contentDescription = null,
                )
            }
        },
    )

    val speedDialPin = com.devxrohan.smusic.ui.component.rememberSpeedDialCollectionPin("artist/${artist.id}", artist.title, artist.thumbnailUrl, onDismiss = onDismiss)

    HorizontalDivider()

    val spotifyRemoval = com.devxrohan.smusic.spotify.rememberSpotifyRemoval("artist", artist.id, artist.title, onDismiss)

    GridMenu(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
        contentPadding =
            PaddingValues(
                start = 8.dp,
                top = 8.dp,
                end = 8.dp,
                bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
            ),
    ) {
        if (spotifyRemoval.visible) GridMenuItem(icon = R.drawable.delete, title = R.string.delete, onClick = spotifyRemoval.show)


        GridMenuItem(icon = R.drawable.radio, title = R.string.start_radio) {
            coroutineScope.launch {
                try {
                    val page = com.devxrohan.smusic.utils.MusicPageRequests.artist(artist.id).getOrThrow()
                    val endpoint = page.artist.radioEndpoint
                    if (endpoint != null) {
                        playerConnection.playQueue(com.devxrohan.smusic.playback.queues.YouTubeQueue(endpoint))
                        onDismiss()
                    } else android.widget.Toast.makeText(context, "Radio is unavailable for this artist", android.widget.Toast.LENGTH_SHORT).show()
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { android.widget.Toast.makeText(context, "Could not start artist radio. Try again.", android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
        if (artist.songCount > 0) {
            GridMenuItem(
                icon = R.drawable.play,
                title = R.string.play,
            ) {
                coroutineScope.launch {
                    val songs =
                        withContext(Dispatchers.IO) {
                            database
                                .artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true)
                                .first()
                                .map { it.toMediaItem() }
                        }
                    playerConnection.playQueue(
                        ListQueue(
                            title = artist.artist.name,
                            items = songs,
                        ),
                    )
                }
                onDismiss()
            }
            GridMenuItem(
                icon = R.drawable.shuffle,
                title = R.string.shuffle,
            ) {
                coroutineScope.launch {
                    val songs =
                        withContext(Dispatchers.IO) {
                            database
                                .artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true)
                                .first()
                                .map { it.toMediaItem() }
                                .shuffled()
                        }
                    playerConnection.playQueue(
                        ListQueue(
                            title = artist.artist.name,
                            items = songs,
                        ),
                    )
                }
                onDismiss()
            }
        }
        if (artist.artist.isYouTubeArtist) {
            GridMenuItem(
                icon = R.drawable.share,
                title = R.string.share,
            ) {
            onDismiss()
            android.widget.Toast.makeText(context, R.string.coming_soon, android.widget.Toast.LENGTH_SHORT).show()
        }
        }
        GridMenuItem(icon = { com.devxrohan.smusic.ui.component.SpeedDialPinIcon(speedDialPin.pinned, speedDialPin.error != null) },
            title = R.string.speed_dial, enabled = !speedDialPin.busy, subtitle = speedDialPin.error,
            onClick = speedDialPin.toggle)
    }
}
