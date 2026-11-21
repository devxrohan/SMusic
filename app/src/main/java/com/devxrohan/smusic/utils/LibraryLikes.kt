package com.devxrohan.smusic.utils

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.content.Context
import androidx.datastore.preferences.core.edit
import com.devxrohan.smusic.spotify.SMusicLikedIdsKey

/** Records explicit app actions separately from imported account favourites. */
internal suspend fun recordSMusicLikes(context: Context, ids: Set<String>, liked: Boolean) {
    context.dataStore.edit { preferences ->
        val previous = preferences[SMusicLikedIdsKey].orEmpty()
        preferences[SMusicLikedIdsKey] = if (liked) previous + ids else previous - ids
        if (preferences[com.devxrohan.smusic.constants.SpotifyWriteBackKey] == true) {
            val pending = runCatching { Json.decodeFromString<Map<String, Boolean>>(preferences[com.devxrohan.smusic.spotify.SpotifyPendingLikesKey].orEmpty()) }.getOrDefault(emptyMap()).toMutableMap()
            ids.forEach { pending[it] = liked }
            preferences[com.devxrohan.smusic.spotify.SpotifyPendingLikesKey] = Json.encodeToString(pending)
        }
        if (!liked) {
            val youtube = preferences[RemoteSeenLikedSongsKey].orEmpty().lineSequence().filter(String::isNotBlank).toSet()
            preferences[RemoteSeenLikedSongsKey] = (youtube - ids).joinToString("\n")
        }
    }
    if (context.dataStore.getSuspend(com.devxrohan.smusic.constants.SpotifyWriteBackKey, false) == true)
        com.devxrohan.smusic.spotify.spotifyServices(context).likeDispatcher().requestDelivery()
}

internal suspend fun recordSpotifySavedIntent(context: Context, kind: String, id: String, liked: Boolean) {
    context.dataStore.edit { preferences ->
        if (preferences[com.devxrohan.smusic.constants.SpotifyWriteBackKey] != true) return@edit
        val key = "$kind:$id"
        val intent = runCatching { Json.decodeFromString<Map<String, Boolean>>(preferences[com.devxrohan.smusic.spotify.SpotifySavedIntentKey].orEmpty()) }.getOrDefault(emptyMap())
        preferences[com.devxrohan.smusic.spotify.SpotifySavedIntentKey] = Json.encodeToString(intent + (key to liked))
        preferences[com.devxrohan.smusic.spotify.SpotifyExportExclusionsKey] = preferences[com.devxrohan.smusic.spotify.SpotifyExportExclusionsKey].orEmpty() - key
    }
    if (context.dataStore.getSuspend(com.devxrohan.smusic.constants.SpotifyWriteBackKey, false) == true)
        com.devxrohan.smusic.spotify.spotifyServices(context).likeDispatcher().requestDelivery()
}
