@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets
import com.devxrohan.smusic.R
import com.devxrohan.smusic.db.entities.Song
import com.devxrohan.smusic.models.toMediaMetadata
import com.devxrohan.smusic.pulse.*
import com.devxrohan.smusic.ui.component.AppIconButton as IconButton
import com.devxrohan.smusic.ui.component.ExpressiveChoiceRow
import com.devxrohan.smusic.ui.component.MusicArtwork
import com.devxrohan.smusic.ui.component.appButtonShapes
import com.devxrohan.smusic.viewmodels.PulseViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PulseScreen(navController: NavController, viewModel: PulseViewModel = hiltViewModel()) {
    val player = com.devxrohan.smusic.LocalPlayerConnection.current
    val database = com.devxrohan.smusic.LocalDatabase.current
    val nowPlaying by (player?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf<com.devxrohan.smusic.models.MediaMetadata?>(null) })
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val mixes by viewModel.mixes.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val controls by viewModel.controls.collectAsStateWithLifecycle()
    val artistResults by viewModel.artistResults.collectAsStateWithLifecycle()
    val artistSearchBusy by viewModel.artistSearchBusy.collectAsStateWithLifecycle()
    val artistSearchError by viewModel.artistSearchError.collectAsStateWithLifecycle()
    var artistPicker by rememberSaveable { mutableStateOf(false) }
    var discoveryPicker by remember { mutableStateOf(false) }
    var rebuildConfirmation by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val pulseSeed = recommendations.firstOrNull()
    fun playRecommendation(song: Song) {
        val connection = player ?: return
        connection.playQueue(com.devxrohan.smusic.playback.queues.PulseRadioQueue(
            song.toMediaMetadata(), connection.service.pulseEngine, database,
        ))
    }

    val windowWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val sidePadding = 16.dp + ((windowWidth - 840).coerceAtLeast(0) / 2).dp
    Scaffold(topBar = {
        TopAppBar(title = { Text("Pulse", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = {
            IconButton(onClick = { navController.navigateUp() }) { Icon(painterResource(R.drawable.arrow_back), "Back") }
        }, actions = {
            IconButton(onClick = { navController.navigate("stats") }) { Icon(painterResource(R.drawable.settings_statistics_rounded), "Listening stats") }
        })
    }) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding()),
            contentPadding = PaddingValues(start = sidePadding, end = sidePadding, bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "profile") {
                PulseProfileHeader(summary, loaded, busy,
                    onExplore = { navController.navigate("home") { launchSingleTop = true } },
                    onPlay = if (player != null && pulseSeed != null) {
                        { playRecommendation(pulseSeed) }
                    } else null)

            }
            if (summary != null) item {
                ExpressiveChoiceRow(
                    labels = listOf("Your taste", "Tune Pulse"),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
            }
            error?.let { message -> item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(shapes = appButtonShapes(), onClick = viewModel::retry, enabled = !busy) { Text("Try again") }
                }
            } }
            val s = summary
            if (s != null && tab == 0) {
                if (recommendations.isNotEmpty()) item {
                    Text("From your rotation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    PulseRotationShelf(recommendations, nowPlaying?.id, ::playRecommendation)
                }
                if (mixes.isNotEmpty()) item {
                    PulsePanel("Made from your listening") {
                        mixes.forEach { mix ->
                            ListItem(headlineContent = { Text(mix.playlist.name) }, supportingContent = { Text("${mix.songCount} songs") },
                                leadingContent = { MusicArtwork(mix.thumbnails.firstOrNull(), modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.medium)) },
                                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent), modifier = Modifier.clickable { navController.navigate("local_playlist/${mix.id}") })
                        }
                    }
                }
                if (s.topArtists.isNotEmpty()) item {
                    Text("At the heart of your taste", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(s.topArtists, key = { it.id }) { artist ->
                            Surface(onClick = { navController.navigate("artist/${artist.id}") }, shape = MaterialTheme.shapes.large, color = androidx.compose.ui.graphics.Color.Transparent) {
                                Column(Modifier.width(132.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    MusicArtwork(artist.thumbnail, artist = true, modifier = Modifier.size(96.dp).clip(CircleShape))
                                    Text(artist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
                if (s.rising.isNotEmpty()) item {
                    PulsePanel("In your rotation now") {
                        PulseArtistRotation(s.rising) { navController.navigate("artist/$it") }
                    }
                }
                if (s.tasteGroups.isNotEmpty()) item {
                    PulsePanel("Your taste connections") {
                        PulseTasteConnections(s.tasteGroups)
                    }
                }
                if (s.reasonHighlights.isNotEmpty()) item {
                    PulsePanel("Why these recommendations?") {
                        s.reasonHighlights.forEach { reason ->
                            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(painterResource(R.drawable.pulse_waveform), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                Text(reason, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else if (s != null) {
                item {
                    PulsePanel("Make discovery yours") {
                        Text("Choose how far Pulse explores beyond your favourites.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(enabled = !busy) { discoveryPicker = true }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Discovery balance", style = MaterialTheme.typography.titleSmall)
                                Text(when (s.discoveryMode) { "FAMILIAR" -> "Familiar favourites"; "BALANCED" -> "Balanced discovery"; "ADVENTUROUS" -> "More discovery"; else -> "Automatic · learns from you" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(painterResource(R.drawable.expand_more), "Change discovery balance")
                        }
                        FilledTonalButton(shapes = appButtonShapes(), onClick = { artistPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Choose artists you like")
                        }
                        PulseTuningControls(s.artistVariety, s.recentCooldownHours, !busy, viewModel::setRecommendationTuning)
                    }
                }
                item {
                    PulsePanel("Feedback & priorities") {
                        PulseFeedbackPanel(controls, s.confidence, s.drift, !busy,
                            viewModel::feedback, viewModel::clearFeedback, viewModel::block, viewModel::unblock,
                            viewModel::removeArtist, onOpen = { entry ->
                                if (entry.track == null) navController.navigate("artist/${entry.id}")
                                else player?.let { connection ->
                                    val track = entry.track
                                    connection.playQueue(com.devxrohan.smusic.playback.queues.PulseRadioQueue(
                                        com.devxrohan.smusic.models.MediaMetadata(entry.id, entry.title,
                                            track.artistIds.mapIndexed { index, id -> com.devxrohan.smusic.models.MediaMetadata.Artist(id,
                                                track.artistNames.getOrNull(index).orEmpty()) },
                                            duration = -1, thumbnailUrl = entry.thumbnailUrl), connection.service.pulseEngine, database))
                                }
                            }, resolveArtwork = viewModel::resolveControlArtwork)
                    }
                }
                item {
                    PulsePanel("Your listening, your control") {
                        Text("Likes, completed songs and repeat listens strengthen your taste. Early skips and Not for me help Pulse learn what to avoid.", style = MaterialTheme.typography.bodyMedium)
                        TextButton(shapes = appButtonShapes(), onClick = { navController.navigate("settings/privacy") }) { Text("History & privacy") }
                        OutlinedButton(shapes = appButtonShapes(), enabled = !busy, onClick = { rebuildConfirmation = true }, modifier = Modifier.fillMaxWidth()) { Text("Relearn from listening history") }
                    }
                }
            }
        }
    }
    if (artistPicker) PulseArtistPicker(artistResults, artistSearchBusy, busy, artistSearchError ?: error,
        controls.entries.filter { it.kind == PulseControlKind.CHOSEN }.map { it.id }.toSet(),
        controls.entries.filter { it.kind == PulseControlKind.ARTIST }.map { it.id }.toSet(),
        viewModel::searchArtists, viewModel::chooseArtist, viewModel::removeArtist,
        onOpen = { artistPicker = false; viewModel.stopArtistSearch(); navController.navigate("artist/$it") },
        onDismiss = { artistPicker = false; viewModel.stopArtistSearch() })
    if (discoveryPicker) com.devxrohan.smusic.ui.screens.settings.SettingsChoiceDialog("Discovery balance", summary?.discoveryMode ?: "AUTO",
        listOf("AUTO" to "Automatic · learns from you", "FAMILIAR" to "Familiar favourites", "BALANCED" to "Balanced discovery", "ADVENTUROUS" to "More discovery"), { discoveryPicker = false }) { viewModel.setDiscoveryMode(it); discoveryPicker = false }
    if (rebuildConfirmation) AlertDialog(onDismissRequest = { rebuildConfirmation = false }, title = { Text("Relearn your taste?") },
        text = { Text("Rebuild Pulse from your saved listening history. Your feedback, hidden artists and discovery choice stay saved.") },
        confirmButton = { TextButton(shapes = appButtonShapes(), onClick = { viewModel.rebuild(); rebuildConfirmation = false }) { Text("Relearn") } }, dismissButton = { TextButton(shapes = appButtonShapes(), onClick = { rebuildConfirmation = false }) { Text("Cancel") } })
}

@Composable
private fun PulsePanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Surface(color = MaterialTheme.colorScheme.surfaceContainer,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}
