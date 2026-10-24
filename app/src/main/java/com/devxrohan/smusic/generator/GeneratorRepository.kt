package com.devxrohan.smusic.generator

import android.content.Context
import com.devxrohan.smusic.ai.AiRecommendationHelper
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.db.entities.PlaylistEntity
import com.devxrohan.smusic.db.entities.PlaylistSongMap
import com.devxrohan.smusic.db.entities.Song
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.models.SongItem
import com.devxrohan.smusic.innertube.models.WatchEndpoint
import com.devxrohan.smusic.models.MediaMetadata
import com.devxrohan.smusic.models.toMediaMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class GenerateMode(val label: String, val description: String, val storageValue: String) {
    MY_MIX("My Mix", "Your taste, mixes & local favorites", "mix"),
    SMART_AI("AI Vibe / Prompt", "Describe any custom vibe or theme", "ai"),
    SONG_RADIO("Song Radio", "YouTube Music radio from any song", "similar-tracks"),
    SIMILAR_ARTISTS("Similar Artists", "YouTube-first artist discovery", "similar-artists"),
    TAG("By Tag / Genre", "Curated genre picks & energy vibes", "tag"),
    NEVER_HEARD("Never Heard", "Fresh discoveries strictly excluding your history", "never-heard"),
    TOP("Top Tracks", "Your most played tracks in SMusic", "top"),
    RECENT("Recent Tracks", "What you've been listening to lately", "recent"),
}

val GENRE_QUICK_CHIPS = listOf(
    "Pop", "Lofi", "Hip-Hop", "Rock", "Electronic", "Indie",
    "Bollywood", "R&B", "Jazz", "Synthwave", "Metal", "Acoustic"
)

@Singleton
class GeneratorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
) {

    /**
     * Deduplicate tracks and cap artist concentration to max 3 tracks per artist.
     */
    fun precheck(tracks: List<MediaMetadata>): List<MediaMetadata> {
        val seen = mutableSetOf<String>()
        val artistCount = mutableMapOf<String, Int>()
        val result = mutableListOf<MediaMetadata>()

        for (track in tracks) {
            if (track.id.isBlank() || !seen.add(track.id)) continue
            val primaryArtist = track.artists.firstOrNull()?.name?.lowercase()?.trim() ?: "unknown"
            val count = artistCount.getOrDefault(primaryArtist, 0)
            if (count < 3) {
                artistCount[primaryArtist] = count + 1
                result.add(track)
            }
        }
        return result
    }

    suspend fun fetchMyMix(count: Int, onProgress: (String) -> Unit): List<MediaMetadata> = withContext(Dispatchers.IO) {
        onProgress("Analyzing your taste profile…")
        val mostPlayed = runCatching {
            database.mostPlayedSongs(fromTimeStamp = 0L, limit = 20).firstOrNull()
        }.getOrNull() ?: emptyList()

        val liked = runCatching {
            database.likedSongsByPlayTimeAsc().firstOrNull()?.take(20)
        }.getOrNull() ?: emptyList()

        val seeds = (mostPlayed + liked).shuffled().take(5)
        val candidateSeeds = if (seeds.isNotEmpty()) {
            seeds.map { it.toMediaMetadata() }
        } else {
            // Fallback to trending songs if local history is empty
            val search = runCatching {
                YouTube.search("trending hits", YouTube.SearchFilter.FILTER_SONG).getOrNull()
            }.getOrNull()
            search?.items?.filterIsInstance<SongItem>()?.take(5)?.map { it.toMediaMetadata() } ?: emptyList()
        }

        val pool = mutableListOf<MediaMetadata>()
        for (seed in candidateSeeds) {
            if (pool.size >= count + 15) break
            onProgress("Blending taste with ${seed.title} radio…")
            val radio = runCatching {
                YouTube.next(
                    WatchEndpoint(
                        videoId = seed.id,
                        playlistId = "RDAMVM${seed.id}"
                    )
                ).getOrNull()
            }.getOrNull()

            radio?.items?.filterIsInstance<SongItem>()?.forEach {
                pool.add(it.toMediaMetadata())
            }
        }

        val result = precheck(pool.shuffled()).take(count)
        if (result.isEmpty() && candidateSeeds.isNotEmpty()) {
            candidateSeeds.take(count)
        } else {
            result
        }
    }

    suspend fun fetchSmartAi(prompt: String, count: Int, onProgress: (String) -> Unit): List<MediaMetadata> = withContext(Dispatchers.IO) {
        onProgress("Consulting AI recommendation engine…")
        // Use AiRecommendationHelper or search with prompt
        val searchResults = runCatching {
            YouTube.search(prompt, YouTube.SearchFilter.FILTER_SONG).getOrNull()
        }.getOrNull()

        val initialTracks = searchResults?.items?.filterIsInstance<SongItem>()?.map { it.toMediaMetadata() } ?: emptyList()
        if (initialTracks.isEmpty()) return@withContext emptyList()

        val pool = mutableListOf<MediaMetadata>()
        pool.addAll(initialTracks)

        val topSeed = initialTracks.firstOrNull()
        if (topSeed != null && pool.size < count) {
            onProgress("Expanding playlist around ${topSeed.title}…")
            val radio = runCatching {
                YouTube.next(WatchEndpoint(videoId = topSeed.id, playlistId = "RDAMVM${topSeed.id}")).getOrNull()
            }.getOrNull()
            radio?.items?.filterIsInstance<SongItem>()?.forEach {
                pool.add(it.toMediaMetadata())
            }
        }

        precheck(pool).take(count)
    }

    suspend fun fetchSongRadio(
        seedVideoId: String?,
        seedTitle: String,
        seedArtist: String,
        count: Int,
        onProgress: (String) -> Unit,
    ): List<MediaMetadata> = withContext(Dispatchers.IO) {
        var videoId = seedVideoId
        if (videoId.isNullOrBlank()) {
            onProgress("Locating song on YouTube Music…")
            val query = "$seedTitle $seedArtist".trim()
            val search = runCatching { YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull() }.getOrNull()
            videoId = (search?.items?.firstOrNull() as? SongItem)?.id
        }

        if (videoId.isNullOrBlank()) {
            throw IllegalStateException("Song \"$seedTitle\" could not be located.")
        }

        onProgress("Building radio mix for $seedTitle…")
        val radio = runCatching {
            YouTube.next(WatchEndpoint(videoId = videoId, playlistId = "RDAMVM$videoId")).getOrNull()
        }.getOrNull()

        val items = radio?.items?.filterIsInstance<SongItem>()?.map { it.toMediaMetadata() } ?: emptyList()
        precheck(items).take(count)
    }

    suspend fun fetchSimilarArtists(artistQuery: String, count: Int, onProgress: (String) -> Unit): List<MediaMetadata> = withContext(Dispatchers.IO) {
        onProgress("Discovering artists related to $artistQuery…")
        val search = runCatching { YouTube.search(artistQuery, YouTube.SearchFilter.FILTER_ARTIST).getOrNull() }.getOrNull()
        val artistItem = search?.items?.firstOrNull() as? com.devxrohan.smusic.innertube.models.ArtistItem
            ?: throw IllegalStateException("Artist \"$artistQuery\" not found.")

        val page = runCatching { YouTube.artist(artistItem.id).getOrNull() }.getOrNull()
        val pool = mutableListOf<MediaMetadata>()

        // 1. Artist's own top songs
        page?.sections?.flatMap { it.items }?.filterIsInstance<SongItem>()?.take(8)?.forEach {
            pool.add(it.toMediaMetadata())
        }

        // 2. Radio on their top song to branch into similar artists
        val seedSong = pool.firstOrNull()
        if (seedSong != null) {
            onProgress("Gathering similar artist tracks…")
            val radio = runCatching {
                YouTube.next(WatchEndpoint(videoId = seedSong.id, playlistId = "RDAMVM${seedSong.id}")).getOrNull()
            }.getOrNull()
            radio?.items?.filterIsInstance<SongItem>()?.forEach {
                pool.add(it.toMediaMetadata())
            }
        }

        precheck(pool).take(count)
    }

    suspend fun fetchGenreTag(tag: String, count: Int, onProgress: (String) -> Unit): List<MediaMetadata> = withContext(Dispatchers.IO) {
        onProgress("Curating best of $tag…")
        val search = runCatching {
            YouTube.search("$tag songs mix", YouTube.SearchFilter.FILTER_SONG).getOrNull()
        }.getOrNull()

        val items = search?.items?.filterIsInstance<SongItem>()?.map { it.toMediaMetadata() } ?: emptyList()
        val pool = mutableListOf<MediaMetadata>()
        pool.addAll(items)

        if (pool.isNotEmpty() && pool.size < count) {
            val seed = pool.first()
            val radio = runCatching {
                YouTube.next(WatchEndpoint(videoId = seed.id, playlistId = "RDAMVM${seed.id}")).getOrNull()
            }.getOrNull()
            radio?.items?.filterIsInstance<SongItem>()?.forEach {
                pool.add(it.toMediaMetadata())
            }
        }

        precheck(pool.shuffled()).take(count)
    }

    suspend fun fetchNeverHeard(count: Int, onProgress: (String) -> Unit): List<MediaMetadata> = withContext(Dispatchers.IO) {
        onProgress("Analyzing listening history for exclusions…")
        val playedSongIds: Set<String> = runCatching {
            database.events().firstOrNull()?.map { it.event.songId }?.toSet()
        }.getOrNull() ?: emptySet()

        val likedSongIds: Set<String> = runCatching {
            database.likedSongsByPlayTimeAsc().firstOrNull()?.map { it.id }?.toSet()
        }.getOrNull() ?: emptySet()

        val excluded = playedSongIds + likedSongIds

        onProgress("Discovering fresh tracks outside your catalog…")
        val search = runCatching {
            YouTube.search("fresh discoveries songs", YouTube.SearchFilter.FILTER_SONG).getOrNull()
        }.getOrNull()
        val candidateSeeds = search?.items?.filterIsInstance<SongItem>() ?: emptyList()

        val pool = mutableListOf<MediaMetadata>()
        for (seed in candidateSeeds.shuffled().take(4)) {
            if (pool.size >= count + 20) break
            val radio = runCatching {
                YouTube.next(WatchEndpoint(videoId = seed.id, playlistId = "RDAMVM${seed.id}")).getOrNull()
            }.getOrNull()
            radio?.items?.filterIsInstance<SongItem>()?.forEach {
                if (it.id !in excluded) {
                    pool.add(it.toMediaMetadata())
                }
            }
        }

        precheck(pool).take(count)
    }

    suspend fun fetchTopTracks(count: Int, onProgress: (String) -> Unit): List<MediaMetadata> = withContext(Dispatchers.IO) {
        onProgress("Fetching top played tracks…")
        val top = runCatching {
            database.mostPlayedSongs(fromTimeStamp = 0L, limit = count).firstOrNull()
        }.getOrNull() ?: emptyList()
        top.map { it.toMediaMetadata() }
    }

    suspend fun fetchRecentTracks(count: Int, onProgress: (String) -> Unit): List<MediaMetadata> = withContext(Dispatchers.IO) {
        onProgress("Fetching recent playback history…")
        val recent = runCatching {
            database.recentSongs(limit = count).firstOrNull()
        }.getOrNull() ?: emptyList()
        recent.map { it.toMediaMetadata() }
    }

    suspend fun savePlaylist(
        title: String,
        tracks: List<MediaMetadata>,
        onProgress: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        onProgress("Saving playlist to your library…")
        val playlist = PlaylistEntity(
            id = PlaylistEntity.generatePlaylistId(),
            name = title,
            createdAt = LocalDateTime.now(),
            lastUpdateTime = LocalDateTime.now(),
            bookmarkedAt = LocalDateTime.now(),
            isEditable = true,
        )

        // MusicDatabase is a DAO facade rather than the RoomDatabase itself, so persist
        // through its synchronous DAO methods. Song metadata inserts also persist artists.
        database.insert(playlist)
        tracks.forEachIndexed { index, mediaMetadata ->
            onProgress("Adding song ${index + 1} of ${tracks.size}: ${mediaMetadata.title}")
            database.insert(mediaMetadata)
            database.insert(
                PlaylistSongMap(
                    playlistId = playlist.id,
                    songId = mediaMetadata.id,
                    position = index,
                )
            )
        }

        playlist.id
    }
}
