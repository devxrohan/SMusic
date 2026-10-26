package com.devxrohan.smusic.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.LocalDatabase
import com.devxrohan.smusic.R
import com.devxrohan.smusic.utils.SpotifyPlaylistImporter
import kotlinx.coroutines.launch

@Composable
fun SpotifyImportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var success by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        title = { Text(if (importing) "Importing Spotify playlist" else stringResource(R.string.import_from_spotify)) },
        text = {
            Column {
                if (importing) {
                    LinearProgressIndicator(progress = { if (total > 0) progress.toFloat() / total else 0f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Text(stringResource(R.string.ui_matching_songs, progress, total))
                } else if (success != null) {
                    Text(stringResource(R.string.ui_imported, success!!))
                } else {
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.spotify_playlist_url)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text(stringResource(R.string.ui_public_spotify_playlists_only_songs_are), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            if (success != null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
            else if (!importing) TextButton(enabled = url.isNotBlank(), onClick = {
                importing = true; error = null
                scope.launch {
                    val result = SpotifyPlaylistImporter.importPlaylist(url, database) { current, max -> progress = current; total = max }
                    result.onSuccess { success = it }.onFailure { error = it.localizedMessage ?: "Could not import this playlist" }
                    importing = false
                }
            }) { Text(stringResource(R.string.import_playlist)) }
        },
        dismissButton = { if (!importing && success == null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
