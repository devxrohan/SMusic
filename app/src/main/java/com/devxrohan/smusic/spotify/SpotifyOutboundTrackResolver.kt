package com.devxrohan.smusic.spotify

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.devxrohan.smusic.db.entities.Song
import com.devxrohan.smusic.lyrics.artistForLyricsSearch
import com.devxrohan.smusic.lyrics.forLyricsSearch
import com.devxrohan.smusic.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class SpotifyOutboundTrackResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SpotifyLibraryRepository,
) {
    suspend fun resolve(song: Song): String? {
        val preferences = context.dataStore.data.first()
        if (preferences[com.devxrohan.smusic.constants.SpotifyWriteBackKey] != true || !com.devxrohan.smusic.utils.AccountWriteConsent.isApproved(context, true)) throw kotlinx.coroutines.CancellationException("Spotify uploads were switched off")
        val cached = SpotifyLibraryBridge.decodeLinks(preferences[SpotifyOutboundMatchesKey])
        cached[song.id]?.let { return it }
        val imported = SpotifyLibraryBridge.decodeLinks(preferences[SpotifyImportedLinksKey])
            .entries.firstOrNull { it.key.startsWith("track:") && it.value == song.id }?.key?.removePrefix("track:")
        if (imported != null) return imported
        val artists = song.artists.map { it.name.artistForLyricsSearch() }.filter { it.isNotBlank() }
        val title = song.title.forLyricsSearch()
        val query = "${artists.firstOrNull().orEmpty()} $title".trim()
        val candidates = repository.accountRequest { Spotify.search(query, limit = 10).getOrThrow() }.tracks?.items.orEmpty()
        val match = candidates.map { candidate -> candidate to SpotifyMapper.matchScore(candidate.name,
            candidate.artists.joinToString(" ") { it.name }, candidate.durationMs, title,
            artists.joinToString(" "), song.song.duration.takeIf { it > 0 }) }
            .maxByOrNull { it.second }?.takeIf { it.second >= 0.78 }?.first ?: return null
        context.dataStore.edit { prefs ->
            prefs[SpotifyOutboundMatchesKey] = Json.encodeToString(SpotifyLibraryBridge.decodeLinks(prefs[SpotifyOutboundMatchesKey]) + (song.id to match.id))
        }
        return match.id
    }
}
