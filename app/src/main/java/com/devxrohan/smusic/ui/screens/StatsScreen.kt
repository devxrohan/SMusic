package com.devxrohan.smusic.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets
import com.devxrohan.smusic.constants.StatPeriod
import com.devxrohan.smusic.viewmodels.StatsViewModel
import java.util.concurrent.TimeUnit
import androidx.compose.ui.res.stringResource
import com.devxrohan.smusic.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(navController: NavController, viewModel: StatsViewModel = hiltViewModel()) {
    val selected by viewModel.selectedOption.collectAsState()
    val index by viewModel.indexChips.collectAsState()
    val songs by viewModel.mostPlayedSongs.collectAsState()
    val songStats by viewModel.mostPlayedSongsStats.collectAsState()
    val artists by viewModel.mostPlayedArtists.collectAsState()
    val albums by viewModel.mostPlayedAlbums.collectAsState()
    val firstEvent by viewModel.firstEvent.collectAsState()
    val totalListeningMs = songStats.sumOf { it.timeListened ?: 0L }
    val totalPlays = songStats.sumOf { it.songCountListened }
    val maxPlays = (songStats.maxOfOrNull { it.songCountListened } ?: 1).coerceAtLeast(1)
    val periodNames = listOf("Weeks", "Months", "Years", "All time")

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.ui_your_stats), fontWeight = FontWeight.Bold) }) },
        containerColor = MaterialTheme.colorScheme.background
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = insets.calculateTopPadding()).padding(horizontal = 16.dp),
            // Same bottom space as every other page: system bar + mini player, so the last items never hide behind it.
            contentPadding = PaddingValues(bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHigh))
                )) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .13f), modifier = Modifier.size(42.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.QueryStats, null, tint = MaterialTheme.colorScheme.primary) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.ui_your_listening_story), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    if (firstEvent == null) "Your stats will grow as you listen."
                                    else "Listening since ${firstEvent!!.event.timestamp.toLocalDate()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            periodNames.forEachIndexed { i, label ->
                                FilterChip(
                                    selected = selected.ordinal == i,
                                    onClick = {
                                        viewModel.selectedOption.value = OptionStats.entries[i]
                                        viewModel.indexChips.value = if (i == 3) StatPeriod.entries.lastIndex else 0
                                    },
                                    label = { Text(label, maxLines = 1, softWrap = false) }
                                )
                            }
                        }
                        AnimatedVisibility(selected != OptionStats.CONTINUOUS) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = index == 0, onClick = { viewModel.indexChips.value = 0 }, label = { Text(stringResource(R.string.ui_current_period)) })
                                FilterChip(selected = index == 1, onClick = { viewModel.indexChips.value = 1 }, label = { Text(stringResource(R.string.ui_previous)) })
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard(stringResource(R.string.ui_listening_time), formatDuration(totalListeningMs), stringResource(R.string.ui_across_your_top_tracks), Icons.Rounded.GraphicEq, Modifier.weight(1f))
                    MetricCard(stringResource(R.string.ui_total_plays), totalPlays.toString(), stringResource(R.string.ui_recorded_plays), Icons.Rounded.MusicNote, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactMetric(stringResource(R.string.songs), songs.size.toString(), Icons.Rounded.MusicNote, Modifier.weight(1f))
                    CompactMetric(stringResource(R.string.artists), artists.size.toString(), Icons.Rounded.Person, Modifier.weight(1f))
                    CompactMetric(stringResource(R.string.albums), albums.size.toString(), Icons.Rounded.Album, Modifier.weight(1f))
                }
            }
            item { SectionHeading(stringResource(R.string.ui_most_played), stringResource(R.string.ui_your_soundtrack_for_this_period)) }
            if (songStats.isEmpty()) item { EmptyStatsCard(stringResource(R.string.ui_no_song_stats_yet_play_a)) }
            itemsIndexed(songStats.take(30), key = { _, item -> item.id }) { rank, song ->
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text((rank + 1).toString().padStart(2, '0'), style = MaterialTheme.typography.titleMedium, color = if (rank < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, modifier = Modifier.size(26.dp))
                        AsyncImage(model = song.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(song.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.ui_plays, song.songCountListened, formatDuration(song.timeListened ?: 0L)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LinearProgressIndicator(progress = { song.songCountListened.toFloat() / maxPlays }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                        }
                    }
                }
            }
            item { SectionHeading(stringResource(R.string.ui_top_artists), stringResource(R.string.ui_artists_you_came_back_to)) }
            if (artists.isEmpty()) item { EmptyStatsCard(stringResource(R.string.ui_artist_insights_appear_as_your_listening)) }
            itemsIndexed(artists.take(15), key = { _, item -> item.artist.id }) { rank, artist ->
                ImageRankRow(rank + 1, artist.artist.name, stringResource(R.string.ui_plays, artist.songCount, formatDuration((artist.timeListened ?: 0).toLong())), artist.artist.thumbnailUrl, CircleShape)
            }
            item { SectionHeading(stringResource(R.string.ui_top_albums), stringResource(R.string.ui_albums_in_your_rotation)) }
            if (albums.isEmpty()) item { EmptyStatsCard(stringResource(R.string.ui_album_insights_will_appear_when_album)) }
            itemsIndexed(albums.take(15), key = { _, item -> item.album.id }) { rank, album ->
                ImageRankRow(rank + 1, album.album.title, stringResource(R.string.ui_plays, album.songCountListened ?: 0, formatDuration((album.timeListened ?: 0).toLong())), album.album.thumbnailUrl, RoundedCornerShape(12.dp))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompactMetric(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ImageRankRow(rank: Int, title: String, subtitle: String, image: String?, shape: androidx.compose.ui.graphics.Shape) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(rank.toString().padStart(2, '0'), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.size(26.dp))
            AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(shape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(Modifier.padding(top = 5.dp, bottom = 1.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyStatsCard(message: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Text(message, Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.coerceAtLeast(0))
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
