@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.innertube.models.ArtistItem
import com.devxrohan.smusic.ui.component.MusicArtwork
import com.devxrohan.smusic.ui.component.appButtonShapes

@Composable
internal fun PulseArtistPicker(
    results: List<ArtistItem>, searching: Boolean, busy: Boolean, error: String?,
    selectedIds: Set<String>, hiddenIds: Set<String>,
    onSearch: (String) -> Unit, onChoose: (ArtistItem) -> Unit,
    onRemove: (String) -> Unit, onOpen: (String) -> Unit, onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    fun search() { if (query.trim().length >= 2) { submitted = true; onSearch(query) } }
    val availableHeight = LocalConfiguration.current.screenHeightDp.dp * .6f
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Artists you like") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = availableHeight), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Choose artists to guide Pulse. Your likes, follows and listening history stay separate.", style = MaterialTheme.typography.bodyMedium) }
                item {
                    OutlinedTextField(query, { query = it }, singleLine = true,
                        label = { Text("Search artists") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }))
                }
                item {
                    FilledTonalButton(shapes = appButtonShapes(), enabled = !searching && query.trim().length >= 2,
                        onClick = { search() }, modifier = Modifier.fillMaxWidth()) { Text("Search") }
                }
                if (searching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                if (submitted && !searching && error == null && results.isEmpty()) item { Text("No artists found. Try another name.") }
                items(results, key = { it.id }) { artist ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().clickable { onOpen(artist.id) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MusicArtwork(artist.thumbnail, artist = true, modifier = Modifier.size(48.dp).clip(CircleShape))
                            Text(artist.title, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                        val chosen = artist.id in selectedIds
                        val hidden = artist.id in hiddenIds
                        FilledTonalButton(shapes = appButtonShapes(), enabled = !busy && (!hidden || chosen),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { if (chosen) onRemove(artist.id) else onChoose(artist) }) {
                            Text(if (chosen) "Remove from Pulse" else if (hidden) "Hidden artist" else "Add to Pulse")
                        }
                        if (hidden) Text("Allow this artist in Hidden artists first.", style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                    }
                }
            }
        }, confirmButton = { TextButton(shapes = appButtonShapes(), onClick = onDismiss) { Text("Done") } })
}
