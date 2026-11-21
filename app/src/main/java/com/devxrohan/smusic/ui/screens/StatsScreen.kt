@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.StatPeriod
import com.devxrohan.smusic.ui.component.AppIconButton as IconButton
import com.devxrohan.smusic.ui.component.DashboardEntrance
import com.devxrohan.smusic.ui.component.DashboardNumber
import com.devxrohan.smusic.ui.component.ExpressiveChoiceRow
import com.devxrohan.smusic.ui.component.ExpressivePreset
import com.devxrohan.smusic.ui.component.MusicArtwork
import com.devxrohan.smusic.viewmodels.StatsViewModel
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(navController: NavController, viewModel: StatsViewModel = hiltViewModel()) {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(owner, viewModel) {
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            viewModel.observeMetadata()
        }
    }
    val selected by viewModel.selectedOption.collectAsStateWithLifecycle()
    val index by viewModel.indexChips.collectAsStateWithLifecycle()
    val songs by viewModel.mostPlayedSongs.collectAsStateWithLifecycle()
    val songStats by viewModel.mostPlayedSongsStats.collectAsStateWithLifecycle()
    val artists by viewModel.mostPlayedArtists.collectAsStateWithLifecycle()
    val albums by viewModel.mostPlayedAlbums.collectAsStateWithLifecycle()
    val firstEvent by viewModel.firstEvent.collectAsStateWithLifecycle()
    val playerConnection = com.devxrohan.smusic.LocalPlayerConnection.current
    val nowPlaying by (playerConnection?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf<com.devxrohan.smusic.models.MediaMetadata?>(null) })
    var category by rememberSaveable { mutableIntStateOf(0) }
    var periodPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val overviewVisible by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == "overview" } }
    }
    val scheme = MaterialTheme.colorScheme
    val totalListeningMs = songStats.sumOf { it.timeListened ?: 0L }
    val totalPlays = songStats.sumOf { it.songCountListened }
    val periodNames = listOf("Weeks", "Months", "Years", "Time range")

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Listening stats") },
            navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(painterResource(R.drawable.arrow_back), "Back") } },
        )
    }, containerColor = scheme.background) { insets ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = insets.calculateTopPadding()),
            state = listState,
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "overview") {
                ListeningOverview(totalListeningMs, totalPlays,
                    listOf("Songs" to songs.size, "Artists" to artists.size, "Albums" to albums.size),
                    songStats.mapNotNull { it.thumbnailUrl }.ifEmpty { listOfNotNull(nowPlaying?.thumbnailUrl) }, periodNames[selected.ordinal], { periodPicker = true },
                    artworkCaption = if (totalPlays == 0 && nowPlaying?.thumbnailUrl != null) "From your player" else null,
                    motionEnabled = overviewVisible)
            }
            item(key = "range") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selected == OptionStats.CONTINUOUS) {
                        listOf("7 days", "30 days", "3 months", "6 months", "1 year", "All time").forEachIndexed { i, label ->
                            ExpressivePreset(label, index == i, { viewModel.indexChips.value = i })
                        }
                    } else {
                        ExpressivePreset("Current", index == 0, { viewModel.indexChips.value = 0 })
                        ExpressivePreset("Previous", index == 1, { viewModel.indexChips.value = 1 })
                    }
                }
            }
            item(key = "ranking-tabs") {
                ExpressiveChoiceRow(
                    labels = listOf("Songs", "Artists", "Albums"),
                    selectedIndex = category,
                    onSelect = { category = it },
                )
            }
            if (category == 0) {
                if (songStats.isEmpty()) item { EmptyStats("Your most-played music will appear here as you listen.") }
                itemsIndexed(songStats.take(50), key = { _, song -> "song:${song.id}" }) { rank, song ->
                    StatsRankingRow(rank + 1, song.title, "${song.songCountListened} plays · ${formatDuration(song.timeListened ?: 0)}", song.thumbnailUrl, MaterialTheme.shapes.medium)
                }
            } else if (category == 1) {
                if (artists.isEmpty()) item { EmptyStats("Artist insights appear as your listening history grows.") }
                itemsIndexed(artists.take(50), key = { _, artist -> "artist:${artist.id}" }) { rank, artist ->
                    StatsRankingRow(rank + 1, artist.artist.name, "${artist.songCount} plays · ${formatDuration((artist.timeListened ?: 0).toLong())}", artist.thumbnailUrl, CircleShape)
                }
            } else {
                if (albums.isEmpty()) item { EmptyStats("Album insights appear when album credits are available.") }
                itemsIndexed(albums.take(50), key = { _, album -> "album:${album.id}" }) { rank, album ->
                    StatsRankingRow(rank + 1, album.album.title, "${album.songCountListened ?: 0} plays · ${formatDuration((album.timeListened ?: 0).toLong())}", album.album.thumbnailUrl, MaterialTheme.shapes.medium)
                }
            }
            firstEvent?.let { event -> item { Text("Listening since ${event.event.timestamp.toLocalDate()}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) } }
        }
    }
    if (periodPicker) com.devxrohan.smusic.ui.screens.settings.SettingsChoiceDialog("Stats period", selected.name,
        OptionStats.entries.mapIndexed { i, value -> value.name to periodNames[i] }, { periodPicker = false }) { name ->
        val option = OptionStats.valueOf(name)
        viewModel.selectedOption.value = option
        viewModel.indexChips.value = if (option == OptionStats.CONTINUOUS) StatPeriod.entries.lastIndex else 0
        periodPicker = false
    }
}

@Composable
private fun EmptyStats(message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(R.drawable.graphic_eq), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatDuration(milliseconds: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds.coerceAtLeast(0))
    val hours = minutes / 60
    return if (hours > 0) "${hours}h ${minutes % 60}m" else "${minutes}m"
}
