package com.devxrohan.smusic.spotify

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.devxrohan.smusic.ui.component.SwitchPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class SpotifyRemovalAction(val visible: Boolean, val show: () -> Unit)

@Composable
internal fun rememberSpotifyRemoval(kind: String, id: String, title: String, onDismiss: () -> Unit): SpotifyRemovalAction {
    val context = LocalContext.current
    val bridge = remember(context) { spotifyLibraryBridge(context) }
    val links = if (kind == "playlist" || kind == "playlist_track") emptyMap()
        else bridge.links.collectAsState(initial = emptyMap()).value
    val visible = when (kind) {
        "playlist" -> id.startsWith("SP:")
        "playlist_track" -> SpotifySyncPolicy.parsePlaylistTrackToken(id) != null
        else -> links.any { (key, value) -> key.startsWith("$kind:") && value == id }
    }
    var open by remember(id) { mutableStateOf(false) }
    var blockFuture by remember(id) { mutableStateOf(false) }
    var deleting by remember(id) { mutableStateOf(false) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    if (open) AlertDialog(
        onDismissRequest = { if (!deleting) open = false },
        title = { Text("Remove $title?") },
        text = {
            Column {
                Text("Remove from SMusic. Your Spotify library stays unchanged.")
                SwitchPreference(title = { Text("Block future sync") },
                    description = "Keep this item from returning during Spotify sync.",
                    checked = blockFuture, onCheckedChange = { blockFuture = it }, isEnabled = !deleting)
                if (deleting) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = !deleting, onClick = {
            deleting = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { bridge.remove(kind, id, blockFuture) }
                    open = false
                    onDismiss()
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { error = "Could not remove this item. Try again."
                } finally { deleting = false }
            }
        }) { Text("Remove") } },
        dismissButton = { TextButton(enabled = !deleting, onClick = { open = false }) { Text("Cancel") } },
    )
    return SpotifyRemovalAction(visible) { blockFuture = false; error = null; open = true }
}

@Composable
internal fun rememberSpotifyPlaylistTrackRemoval(
    item: com.devxrohan.smusic.db.entities.PlaylistSong?, title: String, onDismiss: () -> Unit,
): SpotifyRemovalAction {
    val token = item?.map?.let { SpotifySyncPolicy.playlistTrackToken(it.playlistId, it.id, it.setVideoId) }.orEmpty()
    return rememberSpotifyRemoval("playlist_track", token, title, onDismiss)
}
