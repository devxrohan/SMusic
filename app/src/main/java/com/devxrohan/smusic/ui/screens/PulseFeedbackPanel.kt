@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import com.devxrohan.smusic.ui.component.MusicArtwork
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.R
import com.devxrohan.smusic.pulse.*
import com.devxrohan.smusic.ui.component.appButtonShapes

@Composable
internal fun PulseFeedbackPanel(
    controls: PulseControls,
    confidence: Double,
    drift: Double,
    enabled: Boolean,
    onFeedback: (PulseFeedbackTrack, Boolean) -> Unit,
    onClear: (String) -> Unit,
    onBlock: (String) -> Unit,
    onUnblock: (String) -> Unit,
    onRemoveArtist: (String) -> Unit,
    onOpen: (PulseControlEntry) -> Unit,
    resolveArtwork: suspend (String) -> Unit,
) {
    var selected by remember { mutableStateOf<PulseControlKind?>(null) }
    val groups = remember(controls) { controls.entries.groupBy { it.kind } }
    Text("Your recommendation signals", style = MaterialTheme.typography.titleMedium)
    Text("Review what gets more attention, what is held back, and what stays hidden. These signals influence each request; they are not a fixed queue order.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    PulseControlKind.entries.forEach { kind ->
        Row(Modifier.fillMaxWidth().clickable { selected = kind }.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(kind.label, style = MaterialTheme.typography.titleSmall)
                Text(when (kind) {
                    PulseControlKind.CHOSEN -> "Artists you selected, independent of follows and likes"
                    PulseControlKind.MORE -> "Explicit feedback, separate from your favourites"
                    PulseControlKind.LESS -> "Songs held back for 30 days"
                    PulseControlKind.SKIPPED -> "Learned from skipped and partially played songs"
                    PulseControlKind.RECENT -> "Recently heard songs get less priority"
                    PulseControlKind.PRIORITY -> "Artists ordered by listening and feedback"
                    PulseControlKind.ARTIST -> "Explicit blocks and temporary artist cooldowns"
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${groups[kind].orEmpty().size}", style = MaterialTheme.typography.labelLarge)
            Icon(painterResource(R.drawable.arrow_forward), "Review ${kind.label}", Modifier.size(20.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    Text("Learning from your library", style = MaterialTheme.typography.titleSmall)
    Text("${controls.learnedSongs} songs · ${controls.learnedArtists} artists heard\n" +
        "${controls.likedSongs} liked · ${controls.followedArtists} followed artists · ${controls.downloadedSongs} downloads\n" +
        "${controls.completedSongs} completed · ${controls.replayedSongs} replayed\n" +
        "Profile confidence ${(confidence * 100).toInt()}% · Recent taste shift ${(drift * 100).toInt()}%",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Pulse also learns listening times, artist connections and transitions between songs. Language matching only uses saved lyrics, so unknown-language tracks can still appear.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    selected?.let { kind ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(kind.label) },
            text = {
                val entries = groups[kind].orEmpty()
                if (entries.isEmpty()) Text("Nothing here yet. Your choices and listening will appear here.")
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val isArtist = entry.track == null
                            LaunchedEffect(entry.id, entry.thumbnailUrl) {
                                if (isArtist && entry.thumbnailUrl.isNullOrBlank()) resolveArtwork(entry.id)
                            }
                            val artwork = entry.thumbnailUrl ?: if (!isArtist && entry.id.matches(Regex("[A-Za-z0-9_-]{11}")))
                                "https://i.ytimg.com/vi/${entry.id}/hqdefault.jpg" else null
                            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable {
                                selected = null
                                onOpen(entry)
                            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MusicArtwork(artwork, artist = isArtist,
                                    modifier = Modifier.size(52.dp).clip(if (isArtist) CircleShape else MaterialTheme.shapes.medium))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (entry.subtitle.isNotBlank()) Text(entry.subtitle, style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(painterResource(if (isArtist) R.drawable.arrow_forward else R.drawable.play),
                                    if (isArtist) "Open artist" else "Play song", Modifier.size(20.dp))
                            }
                            when (kind) {
                                PulseControlKind.CHOSEN -> PulseFeedbackAction("Remove from Pulse", enabled) { onRemoveArtist(entry.id) }
                                PulseControlKind.ARTIST -> PulseFeedbackAction("Allow artist", enabled) { onUnblock(entry.id) }
                                PulseControlKind.PRIORITY -> PulseFeedbackAction("Hide artist", enabled) { onBlock(entry.id) }
                                else -> {
                                    entry.track?.let { track ->
                                        if (kind != PulseControlKind.MORE) PulseFeedbackAction("More like this", enabled) { onFeedback(track, true) }
                                        if (kind != PulseControlKind.LESS) PulseFeedbackAction("Less like this", enabled) { onFeedback(track, false) }
                                    }
                                    if (kind == PulseControlKind.MORE || kind == PulseControlKind.LESS)
                                        PulseFeedbackAction("Remove feedback", enabled) { onClear(entry.id) }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }, confirmButton = { TextButton(shapes = appButtonShapes(), onClick = { selected = null }) { Text("Done") } })
    }
}

@Composable
private fun PulseFeedbackAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(shapes = appButtonShapes(), enabled = enabled, onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}
