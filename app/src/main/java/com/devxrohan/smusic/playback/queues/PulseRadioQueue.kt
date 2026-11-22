package com.devxrohan.smusic.playback.queues

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.MediaItem
import com.devxrohan.smusic.constants.SimilarContent
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.extensions.metadata
import com.devxrohan.smusic.extensions.toMediaItem
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.models.WatchEndpoint
import com.devxrohan.smusic.models.MediaMetadata
import com.devxrohan.smusic.pulse.PulseEngine
import com.devxrohan.smusic.utils.get

/** Preserve provider pagination and expand seed-aware radio only when Similar Content is enabled. */
class PulseRadioQueue(
    private val seed: MediaMetadata,
    private val pulse: PulseEngine,
    private val database: MusicDatabase,
    private val settings: DataStore<Preferences>? = null,
) : Queue {
    private val inner = YouTubeQueue.radio(seed)
    private val seedArtists = seed.artists.mapNotNull { it.id }
    private val expandedIds = linkedSetOf(seed.id)
    private var authenticated = false
    private var seedExpansionComplete = false
    override val preloadItem: MediaMetadata? = seed

    private fun similarContentEnabled(): Boolean = settings?.get(SimilarContent, true) ?: pulse.similarContentEnabled()

    override suspend fun getInitialStatus(): Queue.Status {
        authenticated = pulse.hasConnectedYouTubeAccount()
        val raw = inner.getInitialStatus()
        val status = if (raw.items.any { it.mediaId == seed.id }) raw
            else raw.copy(items = listOf(seed.toMediaItem()) + raw.items, mediaItemIndex = 0)
        status.items.forEach { expandedIds.add(it.mediaId) }
        val items = smart(status.items)
        val index = if (items === status.items) status.mediaItemIndex else items.indexOfFirst { it.mediaId == seed.id }.coerceAtLeast(0)
        return status.copy(items = items, mediaItemIndex = index)
    }

    override fun hasNextPage(): Boolean = inner.hasNextPage() ||
        (similarContentEnabled() && !seedExpansionComplete)

    override suspend fun nextPage(): List<MediaItem> {
        val provider = if (inner.hasNextPage()) {
            runCatching { inner.nextPage() }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else emptyList() }
        } else emptyList()
        provider.forEach { expandedIds.add(it.mediaId) }
        return smart(provider)
    }

    private suspend fun smart(
        items: List<MediaItem>,
    ): List<MediaItem> = runCatching {
        val music = items.filter { mi ->
            mi.mediaId == seed.id || (mi.metadata?.duration ?: -1).let { it <= 0 || it in 30..900 }
        }.ifEmpty { items }

        val candidatePool = ArrayList<MediaItem>(music)
        // Exhaust the seed's radio before using its related shelf; never walk recommendations of recommendations.
        if (music.isEmpty() && !inner.hasNextPage() && similarContentEnabled() && !seedExpansionComplete) {
            seedExpansionComplete = true
            val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
            val related = endpoint?.let { YouTube.related(it, authenticated = authenticated).getOrNull()?.songs.orEmpty() }.orEmpty()
            related.take(20).map { it.toMediaItem() }.forEach { item ->
                if (expandedIds.add(item.mediaId)) {
                    candidatePool.add(item)
                }
            }
        }

        if (candidatePool.isEmpty()) return@runCatching emptyList<MediaItem>()
        val ranked = pulse.rankForRadio(
            items = candidatePool.distinctBy { it.mediaId },
            idOf = { it.mediaId },
            artistIdsOf = { mi -> mi.metadata?.artists?.mapNotNull { it.id }.orEmpty() },
            seedId = seed.id,
            seedArtistIds = seedArtists,
            preserveSeedProximity = true,
        )
        ranked
    }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else items }
}
