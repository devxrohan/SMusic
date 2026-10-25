package com.devxrohan.smusic.constants

import androidx.datastore.preferences.core.booleanPreferencesKey

object AndroidAutoConstants {
    val AndroidAutoEnabledKey = booleanPreferencesKey("android_auto_enabled")
    val AndroidAutoShowLikedSongsKey = booleanPreferencesKey("android_auto_show_liked_songs")
    val AndroidAutoShowDownloadedKey = booleanPreferencesKey("android_auto_show_downloaded")
    val AndroidAutoShowYouTubePlaylistsKey = booleanPreferencesKey("android_auto_show_youtube_playlists")
    val AndroidAutoShowHistoryKey = booleanPreferencesKey("android_auto_show_history")
    val AndroidAutoSimplifiedModeKey = booleanPreferencesKey("android_auto_simplified_mode")
}
