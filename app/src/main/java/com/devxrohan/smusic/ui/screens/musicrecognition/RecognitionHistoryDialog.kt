@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens.musicrecognition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.devxrohan.smusic.R
import com.devxrohan.smusic.recognition.RecognitionHistory
import com.devxrohan.smusic.recognition.RecognizedTrack
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecognitionHistoryDialog(history: RecognitionHistory, onDismiss: () -> Unit, onOpen: (RecognizedTrack) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember(history) { mutableStateOf(false) }
    val tracks by remember(history) {
        history.tracks.onEach { loaded = true; error = null }
            .catch { loaded = true; error = "Saved songs could not be loaded" }
    }.collectAsState(initial = emptyList())
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Text("Recognized songs", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp)) }
        if (!loaded) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp))
        if (loaded && error == null && tracks.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.music_note), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                Text("Your discoveries, saved", style = MaterialTheme.typography.titleMedium)
                Text("Songs you identify will appear here. Your latest 50 discoveries stay in this list.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val visibleTracks = tracks.take(50)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp).navigationBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            itemsIndexed(visibleTracks, key = { _, track -> "${track.id}:${track.recognizedAt}:${track.title}" }) { index, track ->
                SegmentedListItem(
                    onClick = { onOpen(track) },
                    shapes = ListItemDefaults.segmentedShapes(index, visibleTracks.size),
                    leadingContent = {
                        AsyncImage(model = track.cover, placeholder = painterResource(R.drawable.music_note), error = painterResource(R.drawable.music_note),
                            contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.medium))
                    },
                    content = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        }
    }
}
