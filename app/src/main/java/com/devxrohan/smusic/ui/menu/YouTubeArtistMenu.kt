package com.devxrohan.smusic.ui.menu

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.innertube.models.ArtistItem
import com.devxrohan.smusic.LocalDatabase
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.R
import com.devxrohan.smusic.db.entities.ArtistEntity
import com.devxrohan.smusic.playback.queues.YouTubeQueue
import com.devxrohan.smusic.ui.component.GridMenuItem
import com.devxrohan.smusic.ui.component.YouTubeListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeArtistMenu(
    artist: ArtistItem,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val libraryArtist by database.artist(artist.id).collectAsState(initial = null)

    YouTubeListItem(
        item = artist,
        trailingContent = {},
    )


    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    val speedDialPin = com.devxrohan.smusic.ui.component.rememberSpeedDialCollectionPin(
        "artist/${artist.id}", artist.title, artist.thumbnail, onDismiss)
    com.devxrohan.smusic.ui.component.GridMenu(
        modifier = Modifier.fillMaxWidth().height(216.dp).padding(horizontal = 16.dp),
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
    ) {
        artist.radioEndpoint?.let { endpoint ->
            GridMenuItem(icon = R.drawable.radio, title = R.string.start_radio) {
                playerConnection.playQueue(YouTubeQueue(endpoint))
                onDismiss()
            }
        }
        artist.shuffleEndpoint?.let { endpoint ->
            GridMenuItem(icon = R.drawable.shuffle, title = R.string.shuffle) {
                playerConnection.playQueue(YouTubeQueue(endpoint))
                onDismiss()
            }
        }
        GridMenuItem(icon = R.drawable.share, title = R.string.share) {
            onDismiss()
            android.widget.Toast.makeText(context, R.string.coming_soon, android.widget.Toast.LENGTH_SHORT).show()
        }
        GridMenuItem(icon = { com.devxrohan.smusic.ui.component.SpeedDialPinIcon(speedDialPin.pinned, speedDialPin.error != null) },
            title = R.string.speed_dial, enabled = !speedDialPin.busy, subtitle = speedDialPin.error,
            onClick = speedDialPin.toggle)
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        // Subscribe/Subscribed button
        item {
            ListItem(
                headlineContent = {
                    Text(
                        text = if (libraryArtist?.artist?.bookmarkedAt != null) stringResource(R.string.subscribed) else stringResource(
                            R.string.subscribe
                        )
                    )
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(
                            if (libraryArtist?.artist?.bookmarkedAt != null) {
                                R.drawable.subscribed
                            } else {
                                R.drawable.subscribe
                            }
                        ),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    val subscribe = libraryArtist?.artist?.bookmarkedAt == null
                    database.query {
                        val current = libraryArtist
                        if (current != null) {
                            update(current.artist.toggleLike())
                        } else {
                            insert(
                                ArtistEntity(
                                    id = artist.id,
                                    name = artist.title,
                                    channelId = artist.channelId,
                                    thumbnailUrl = artist.thumbnail,
                                ).toggleLike()
                            )
                        }
                    }
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        playerConnection.service.pulseEngine.onArtistSubscription(artist.id, subscribe)
                    }
                }
            )
        }
    }
}
