package com.devxrohan.smusic.ui.component

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.R
import androidx.datastore.preferences.core.Preferences
import com.devxrohan.smusic.utils.AccountWriteConsent
import com.devxrohan.smusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AccountWriteBackPreference(account: String, key: Preferences.Key<Boolean>, enabled: Boolean = true, spotify: Boolean = false) {
    var checked by rememberPreference(key, false)
    var confirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(account) {
        if (checked && !withContext(Dispatchers.IO) { AccountWriteConsent.isApproved(context, spotify) }) checked = false
    }
    SwitchPreference(title = { Text("Send changes to $account") }, checked = checked, isEnabled = (enabled || checked) && !saving,
        icon = { Icon(painterResource(if (spotify) R.drawable.spotify else R.drawable.youtube), null, Modifier.size(28.dp), tint = Color.Unspecified) },
        description = if (spotify) "Mirror new likes, saved artists and albums, and song additions to Spotify playlists." else "Allow app likes, playlist edits and subscriptions to change your YouTube Music account.",
        onCheckedChange = { value ->
            if (value) { error = null; confirm = true }
            else { checked = false; scope.launch(Dispatchers.IO) { AccountWriteConsent.revoke(context, spotify) } }
        })
    if (confirm) AlertDialog(onDismissRequest = { if (!saving) confirm = false },
        title = { Text("Mirror likes to $account?") },
        text = { Text(error ?: if (spotify) "Hearting a song in SMusic will also Like it on Spotify, and removing the heart takes that Like away again, but never a Like that was already there. Only likes you add from now on are mirrored.\n\nThis uses the private endpoint the Spotify web player uses, not an official API. It can stop working at any time, and in rare cases unofficial access can get an account flagged."
            else "Likes, unlikes, playlist edits and subscriptions made in SMusic can also change your connected YouTube Music account.\n\nThis uses the private endpoint the YouTube Music web player uses, not an official API. It can stop working at any time, and in rare cases unofficial access can get an account flagged.") },
        confirmButton = { TextButton(enabled = !saving, onClick = {
            saving = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        AccountWriteConsent.approve(context, spotify)
                        if (spotify) com.devxrohan.smusic.spotify.spotifyServices(context).webSession().headers()
                    }
                    checked = true; confirm = false
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
                } catch (_: Exception) { error = "Could not enable account changes. Reconnect this account and try again."
                } finally { saving = false }
            }
        }) { Text("I understand") } },
        dismissButton = { TextButton(enabled = !saving, onClick = { confirm = false }) { Text("Cancel") } })

}
