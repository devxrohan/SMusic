package com.devxrohan.smusic.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.LocalDatabase
import com.devxrohan.smusic.R
import com.devxrohan.smusic.utils.SpotifyImporter
import kotlinx.coroutines.launch

@Composable
fun SpotifyImportDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!isImporting) onDismiss()
        },
        icon = {
            Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.spotify),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = androidx.compose.ui.graphics.Color.Unspecified
            )
        },
        title = { Text(if (isImporting) stringResource(R.string.importing_playlist) else stringResource(R.string.import_from_spotify)) },
        text = {
            Column {
                if (resultMessage != null) {
                    Text(resultMessage!!)
                } else if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                } else if (isImporting) {
                    LinearProgressIndicator(
                        progress = { if (total > 0) progress.toFloat() / total.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    Text(stringResource(R.string.processing_song, progress, total))
                } else {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.spotify_playlist_url)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            if (resultMessage != null || errorMessage != null) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.ok))
                }
            } else if (!isImporting) {
                TextButton(
                    onClick = {
                        if (url.isNotBlank()) {
                            isImporting = true
                            errorMessage = null
                            coroutineScope.launch {
                                val result = SpotifyImporter.importPlaylist(
                                    url = url,
                                    dao = database,
                                    onProgress = { current, max ->
                                        progress = current
                                        total = max
                                    }
                                )
                                result.onSuccess { playlistName ->
                                    resultMessage = context.getString(R.string.spotify_import_success, playlistName)
                                }.onFailure { error ->
                                    errorMessage = context.getString(R.string.spotify_import_failed, error.message ?: "")
                                }
                                isImporting = false
                            }
                        }
                    },
                    enabled = url.isNotBlank()
                ) {
                    Text(stringResource(R.string.import_playlist))
                }
            }
        },
        dismissButton = {
            if (!isImporting && resultMessage == null && errorMessage == null) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
