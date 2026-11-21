package com.devxrohan.smusic.spotify

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.devxrohan.smusic.R
import com.devxrohan.smusic.ui.component.PreferenceEntry
import com.devxrohan.smusic.ui.component.ProfileAvatar
import com.devxrohan.smusic.ui.component.SettingsGeneralCategory
import com.devxrohan.smusic.ui.component.SwitchPreference
import com.devxrohan.smusic.utils.rememberPreference

@Composable
fun SpotifyAccountPreferences(navController: NavController, viewModel: SpotifyAccountViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPlaylists by rememberPreference(ShowSpotifyPlaylistsKey, false)
    SettingsGeneralCategory(title = stringResource(R.string.spotify_account), items = buildList {
        add {
            PreferenceEntry(
                title = { Text(if (!state.isAuthenticated) stringResource(R.string.spotify_connect)
                    else state.accountName.takeIf(String::isNotBlank)?.let { stringResource(R.string.spotify_connected_as, it) }
                        ?: stringResource(R.string.spotify_account), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                description = when {
                    state.isLoading -> stringResource(R.string.spotify_loading_library)
                    !state.isAuthenticated -> stringResource(R.string.spotify_not_connected)
                    else -> stringResource(R.string.spotify_available_count, state.playlistCount)
                },
                icon = { Icon(painterResource(R.drawable.spotify), null, Modifier.size(28.dp), tint = androidx.compose.ui.graphics.Color.Unspecified) },
                trailingContent = { AnimatedVisibility(state.isLoading) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } },
                onClick = { navController.navigate("settings/spotify/login") { launchSingleTop = true } },
            )
        }
        add {
            SwitchPreference(title = { Text(stringResource(R.string.spotify_show_playlist)) },
                description = stringResource(R.string.spotify_show_playlist_desc),
                icon = { Icon(painterResource(R.drawable.spotify), null, Modifier.size(28.dp), tint = androidx.compose.ui.graphics.Color.Unspecified) }, checked = showPlaylists,
                onCheckedChange = { enabled -> showPlaylists = enabled; if (enabled) viewModel.reloadPlaylists() },
                isEnabled = state.isAuthenticated && !state.isLoading)
        }
        add {
            com.devxrohan.smusic.ui.component.AccountWriteBackPreference("Spotify", com.devxrohan.smusic.constants.SpotifyWriteBackKey,
                enabled = state.isAuthenticated && !state.isLoading, spotify = true)
        }
        if (state.isAuthenticated) {
            add { PreferenceEntry(title = { Text(stringResource(R.string.spotify_reload_playlist)) },
                description = stringResource(R.string.spotify_reload_playlist_desc),
                icon = { Icon(painterResource(R.drawable.sync), null) }, onClick = viewModel::reloadPlaylists, isEnabled = !state.isLoading) }
            add { PreferenceEntry(title = { Text("Disconnect Spotify") }, icon = { Icon(painterResource(R.drawable.logout), null) },
                onClick = { showPlaylists = false; viewModel.logout() }, isEnabled = !state.isLoading) }
        }
    })
    state.errorMessage?.let { error -> AlertDialog(onDismissRequest = viewModel::dismissError,
        title = { Text("Spotify") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.ok)) } }) }
}
