package com.devxrohan.smusic.utils

import com.devxrohan.smusic.db.DatabaseDao
import com.devxrohan.smusic.db.entities.PlaylistEntity
import com.devxrohan.smusic.db.entities.PlaylistSongMap
import com.devxrohan.smusic.innertube.YouTube

import com.devxrohan.smusic.innertube.models.SongItem
import com.devxrohan.smusic.models.toMediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.time.LocalDateTime
import java.util.UUID

object SpotifyPlaylistImporter {
    suspend fun importPlaylist(url: String, dao: DatabaseDao, onProgress: (Int, Int) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val playlistId = Regex("(?:playlist/|spotify:playlist:)([A-Za-z0-9]+)").find(url.trim())?.groupValues?.get(1)
                ?: url.trim().takeIf { it.matches(Regex("[A-Za-z0-9]{16,}")) }
                ?: throw IllegalArgumentException("Enter a valid public Spotify playlist URL or ID")
            val embed = "https://open.spotify.com/embed/playlist/$playlistId"
            val document = Jsoup.connect(embed)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36")
                .timeout(15000).get()
            val script = document.selectFirst("script#__NEXT_DATA__")?.html()
                ?: throw IllegalStateException("Spotify did not provide public playlist data. Make sure the playlist is public.")
            val root = JSONObject(script)
            val entity = root.optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("state")
                ?.optJSONObject("data")?.optJSONObject("entity")
                ?: throw IllegalStateException("Could not read Spotify playlist details")
            val playlistName = entity.optString("name").ifBlank { "Imported Spotify Playlist" }
            val trackArray = entity.optJSONArray("trackList") ?: throw IllegalStateException("This playlist has no readable tracks")
            val tracks: List<Pair<String, String>> = (0 until trackArray.length()).mapNotNull { i ->
                val item = trackArray.optJSONObject(i) ?: return@mapNotNull null
                val title = item.optString("title").trim()
                val artist = item.optString("subtitle").trim()
                if (title.isBlank()) null else title to artist
            }
            if (tracks.isEmpty()) throw IllegalStateException("No public tracks were found in this Spotify playlist")
            val playlistLocalId = "LP" + UUID.randomUUID().toString().replace("-", "").take(8)
            dao.insert(PlaylistEntity(id = playlistLocalId, name = playlistName, browseId = "sp:$playlistId", bookmarkedAt = LocalDateTime.now(), remoteSongCount = tracks.size))
            val importedIds = mutableListOf<String>()
            tracks.forEachIndexed { index, (title, artist) ->
                onProgress(index + 1, tracks.size)
                runCatching {
                    val result = YouTube.search("$title $artist".trim(), YouTube.SearchFilter.FILTER_SONG).getOrThrow()
                    val song = result.items.firstOrNull { it is SongItem } as? SongItem
                    if (song != null) {
                        dao.insert(song.toMediaMetadata())
                        importedIds += song.id
                    }
                }
            }
            importedIds.distinct().forEachIndexed { index, songId -> dao.insert(PlaylistSongMap(playlistId = playlistLocalId, songId = songId, position = index)) }
            if (importedIds.isEmpty()) throw IllegalStateException("Spotify tracks were read, but no matching YouTube Music songs were found")
            "$playlistName (${importedIds.distinct().size} songs)"
        }
    }
}
