package com.devxrohan.smusic.utils

import com.devxrohan.smusic.db.DatabaseDao
import com.devxrohan.smusic.db.entities.PlaylistEntity
import com.devxrohan.smusic.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.util.UUID
import com.devxrohan.smusic.models.toMediaMetadata

object SpotifyImporter {

    suspend fun importPlaylist(
        url: String,
        dao: DatabaseDao,
        onProgress: (Int, Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Convert URL to embed URL
            val playlistId = url.substringAfter("playlist/").substringBefore("?")
            if (playlistId.isBlank()) throw IllegalArgumentException("Invalid Spotify Playlist URL")
            val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"

            // 2. Fetch HTML using Jsoup
            val doc = Jsoup.connect(embedUrl).get()

            // 3. Extract JSON state
            val nextDataElement = doc.select("script#__NEXT_DATA__").first()
                ?: throw IllegalStateException("Could not find Spotify playlist data")
            val jsonString = nextDataElement.html()

            val json = JSONObject(jsonString)
            val entity = json.getJSONObject("props")
                .getJSONObject("pageProps")
                .getJSONObject("state")
                .getJSONObject("data")
                .getJSONObject("entity")

            val playlistName = entity.optString("name", "Imported Playlist")

            // Tracks are in trackList
            val trackListArray = entity.optJSONArray("trackList")
                ?: throw IllegalStateException("Could not find track list")

            val totalTracks = trackListArray.length()
            val songIds = mutableListOf<String>()

            // 4. Find or create the Playlist in DB — Spotify playlists don't
            // carry a stable browse-id like YouTube ones do, so matching by
            // name is the safest identifier available for detecting "this
            // is the same playlist being re-imported" without a schema
            // change to store a dedicated Spotify id.
            val existingPlaylistWrapper = dao.playlistsByNameAsc().first()
                .find { it.playlist.name == playlistName }
            val targetPlaylistId = existingPlaylistWrapper?.playlist?.id ?: UUID.randomUUID().toString()
            val existingSongCount = existingPlaylistWrapper?.songCount ?: 0

            if (existingPlaylistWrapper == null) {
                val playlistEntity = PlaylistEntity(
                    id = targetPlaylistId,
                    name = playlistName,
                    bookmarkedAt = java.time.LocalDateTime.now()
                )
                dao.insert(playlistEntity)
            }

            // 5. Match songs and add to playlist
            for (i in 0 until totalTracks) {
                onProgress(i + 1, totalTracks)
                val trackObj = trackListArray.optJSONObject(i) ?: continue
                val title = trackObj.optString("title")
                val artist = trackObj.optString("subtitle")
                
                if (title.isBlank()) continue
                val query = "$title $artist"

                try {
                    // Search YouTube
                    val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    val firstSong = searchResult?.items?.firstOrNull() as? com.devxrohan.smusic.innertube.models.SongItem
                    if (firstSong != null) {
                        songIds.add(firstSong.id)
                        dao.insert(firstSong.toMediaMetadata())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 6. Save only genuinely new songs to the playlist — skip any
            // that are already in it, so re-importing the same playlist
            // adds what's new instead of duplicating everything.
            val alreadyPresentIds = if (existingPlaylistWrapper != null) {
                dao.playlistDuplicates(targetPlaylistId, songIds).toSet()
            } else {
                emptySet()
            }
            val newSongIds = songIds.filterNot { it in alreadyPresentIds }
            newSongIds.forEachIndexed { index, songId ->
                dao.insert(
                    com.devxrohan.smusic.db.entities.PlaylistSongMap(
                        songId = songId,
                        playlistId = targetPlaylistId,
                        position = existingSongCount + index
                    )
                )
            }

            playlistName
        }
    }
}
