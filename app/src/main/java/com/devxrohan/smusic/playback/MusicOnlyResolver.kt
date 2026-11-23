@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.playback

import androidx.media3.common.MediaItem
import com.devxrohan.smusic.extensions.metadata
import com.devxrohan.smusic.extensions.toMediaItem
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.models.SongItem
import com.devxrohan.smusic.models.toMediaMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Resolves metadata only; audio is fetched once through the normal playback cache. */
internal class MusicOnlyResolver(private val downloaded: (String) -> Boolean) {
    private data class Answer(val item: MediaItem, val expires: Long)
    private val answers = LinkedHashMap<String, Answer>()
    private val gate = Mutex()

    suspend fun resolve(item: MediaItem): MediaItem = gate.withLock {
        val track = item.metadata ?: return@withLock item
        val uri = item.localConfiguration?.uri
        if (!track.id.matches(Regex("[A-Za-z0-9_-]{11}")) || uri?.scheme in listOf("file", "content") || downloaded(track.id)) return@withLock item
        if (track.musicVideoType in listOf("MUSIC_VIDEO_TYPE_ATV", "MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC")) return@withLock item
        answers[track.id]?.takeIf { it.expires > System.currentTimeMillis() }?.let { return@withLock it.item }
        val resolved = try {
            withTimeoutOrNull(6_000) {
                val identified = if (track.musicVideoType == null)
                    YouTube.queue(listOf(track.id)).getOrNull()?.firstOrNull { it.id == track.id }?.toMediaMetadata()?.let { fetched ->
                        fetched.copy(musicVideoType = fetched.musicVideoType ?: track.musicVideoType)
                    } ?: track
                    else track
                if (identified.musicVideoType in listOf("MUSIC_VIDEO_TYPE_ATV", "MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC")) return@withTimeoutOrNull item
                val candidates = YouTube.search("${MusicVersionMatch.normalized(identified.title)} ${identified.artists.firstOrNull()?.name.orEmpty()}",
                    YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items.orEmpty().filterIsInstance<SongItem>()
                candidates.firstOrNull { candidate ->
                    val type = candidate.endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
                    candidate.id != track.id && (type == "MUSIC_VIDEO_TYPE_ATV" || type == null) &&
                        MusicVersionMatch.matches(identified.title, identified.artists.map { it.name }, if (type == "MUSIC_VIDEO_TYPE_ATV" && identified.musicVideoType == "MUSIC_VIDEO_TYPE_OMV") -1 else identified.duration,
                            candidate.title, candidate.artists.map { it.name }, candidate.duration ?: -1)
                }?.let { candidate -> candidate.toMediaMetadata().copy(musicVideoType = "MUSIC_VIDEO_TYPE_ATV").toMediaItem() } ?: item
            } ?: item
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { item }
        if (answers.size >= 128) answers.remove(answers.keys.first())
        answers[track.id] = Answer(resolved, System.currentTimeMillis() + if (resolved.mediaId != track.id) 6 * 60 * 60_000L else 5 * 60_000L)
        resolved
    }
}
