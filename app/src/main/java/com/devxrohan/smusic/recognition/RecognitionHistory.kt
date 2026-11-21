package com.devxrohan.smusic.recognition

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.devxrohan.smusic.constants.PauseListenHistoryKey
import com.devxrohan.smusic.constants.PauseRecognitionHistoryKey
import com.devxrohan.smusic.constants.RecognitionHistoryKey
import com.devxrohan.smusic.shazamkit.models.RecognitionResult
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Local recognition metadata only: no microphone samples, credentials or external history service. */
@Serializable
data class RecognizedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val cover: String? = null,
    val isrc: String? = null,
    val recognizedAt: Long,
) {
    fun toResult() = RecognitionResult(
        trackId = id, title = title, artist = artist, album = album,
        coverArtUrl = cover, coverArtHqUrl = cover, genre = null, releaseDate = null,
        label = null, lyrics = null, shazamUrl = null, appleMusicUrl = null,
        spotifyUrl = null, isrc = isrc,
    )
}

class RecognitionHistory(private val store: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(RecognizedTrack.serializer())
    private fun decode(raw: String?) = if (raw == null) emptyList() else json.decodeFromString(serializer, raw)
    val tracks = store.data.map { decode(it[RecognitionHistoryKey]).take(50) }

    suspend fun add(result: RecognitionResult) {
        if (result.title.isBlank() || result.artist.isBlank()) return
        store.edit { prefs ->
            // Use the committed preference rather than the process cache for this privacy decision.
            if (prefs[PauseListenHistoryKey] == true || prefs[PauseRecognitionHistoryKey] == true) return@edit
            val track = RecognizedTrack(result.trackId, result.title, result.artist, result.album,
                (result.coverArtHqUrl ?: result.coverArtUrl)?.takeIf { it.startsWith("https://") },
                result.isrc, System.currentTimeMillis())
            val previous = decode(prefs[RecognitionHistoryKey])
            val next = listOf(track) + previous.filterNot {
                (track.id.isNotBlank() && it.id == track.id) ||
                    (it.title.equals(track.title, true) && it.artist.equals(track.artist, true))
            }
            prefs[RecognitionHistoryKey] = json.encodeToString(serializer, next.take(50))
        }
    }

    suspend fun remove(track: RecognizedTrack) {
        store.edit { prefs ->
            prefs[RecognitionHistoryKey] = json.encodeToString(serializer,
                decode(prefs[RecognitionHistoryKey]).filterNot { it == track })
        }
    }

    suspend fun clear() { store.edit { it.remove(RecognitionHistoryKey) } }
}
