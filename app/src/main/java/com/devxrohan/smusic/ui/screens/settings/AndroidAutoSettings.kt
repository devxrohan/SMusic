package com.devxrohan.smusic.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.compose.material3.TopAppBarScrollBehavior
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.AndroidAutoConstants
import com.devxrohan.smusic.ui.component.PreferenceGroupTitle
import com.devxrohan.smusic.ui.component.SettingsPage
import com.devxrohan.smusic.ui.component.SwitchPreference
import com.devxrohan.smusic.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidAutoSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (autoEnabled, onAutoEnabledChange) = rememberPreference(AndroidAutoConstants.AndroidAutoEnabledKey, true)
    val (simplifiedMode, onSimplifiedModeChange) = rememberPreference(AndroidAutoConstants.AndroidAutoSimplifiedModeKey, false)
    val (showLiked, onShowLikedChange) = rememberPreference(AndroidAutoConstants.AndroidAutoShowLikedSongsKey, true)
    val (showDownloaded, onShowDownloadedChange) = rememberPreference(AndroidAutoConstants.AndroidAutoShowDownloadedKey, true)
    val (showPlaylists, onShowPlaylistsChange) = rememberPreference(AndroidAutoConstants.AndroidAutoShowYouTubePlaylistsKey, true)
    val (showHistory, onShowHistoryChange) = rememberPreference(AndroidAutoConstants.AndroidAutoShowHistoryKey, true)

    SettingsPage(
        title = stringResource(R.string.android_auto),
        navController = navController,
        scrollBehavior = scrollBehavior,
    ) {
        PreferenceGroupTitle(title = stringResource(R.string.android_auto_general))
        SwitchPreference(
            title = { Text(stringResource(R.string.android_auto_enable)) },
            description = stringResource(R.string.android_auto_enable_desc),
            icon = { Icon(painterResource(R.drawable.car), null) },
            checked = autoEnabled,
            onCheckedChange = onAutoEnabledChange,
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.android_auto_simplified_mode)) },
            description = stringResource(R.string.android_auto_simplified_mode_desc),
            icon = { Icon(painterResource(R.drawable.car), null) },
            checked = simplifiedMode,
            onCheckedChange = onSimplifiedModeChange,
        )

        PreferenceGroupTitle(title = stringResource(R.string.android_auto_library))
        SwitchPreference(
            title = { Text(stringResource(R.string.android_auto_show_liked_songs)) },
            icon = { Icon(painterResource(R.drawable.library_music), null) },
            checked = showLiked,
            onCheckedChange = onShowLikedChange,
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.android_auto_show_downloaded)) },
            icon = { Icon(painterResource(R.drawable.download), null) },
            checked = showDownloaded,
            onCheckedChange = onShowDownloadedChange,
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.android_auto_show_youtube_playlists)) },
            description = stringResource(R.string.android_auto_show_youtube_playlists_desc),
            icon = { Icon(painterResource(R.drawable.playlist_play), null) },
            checked = showPlaylists,
            onCheckedChange = onShowPlaylistsChange,
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.android_auto_show_history)) },
            icon = { Icon(painterResource(R.drawable.history), null) },
            checked = showHistory,
            onCheckedChange = onShowHistoryChange,
        )
    }
}
