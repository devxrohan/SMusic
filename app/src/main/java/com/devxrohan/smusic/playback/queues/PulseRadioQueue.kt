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
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

/**
 * Pulse radio queue. Provider pagination remains the primary source; Pulse can additionally
 * expand the queue with seed-aware related songs when Similar Content is enabled.
 * The expansion is stateful so a 15-item provider page never becomes the hard queue limit.
 */
class PulseRadioQueue(
    private val seed: MediaMetadata,
    private val pulse: PulseEngine,
    private val database: MusicDatabase,
    private val settings: DataStore<Preferences>? = null,
) : Queue {
    private val inner = YouTubeQueue.radio(seed)
    private val seedArtists = seed.artists.mapNotNull { it.id }
    private val expandedIds = linkedSetOf(seed.id)
    private val expansionSeeds = ArrayDeque<String>()
    private val queriedSeeds = mutableSetOf<String>()
    private var expansionRounds = 0
    private val maxExpansionRounds = 12
    override val preloadItem: MediaMetadata? = seed

    /** Resolved once per queue: with a YouTube account connected the queue is pure YouTube (no Pulse extras). */
    private var accountPrimary: Boolean? = null
    private suspend fun isAccountPrimary(): Boolean =
        accountPrimary ?: pulse.accountPrimary().also { accountPrimary = it }

    private fun similarContentEnabled(): Boolean = settings?.get(SimilarContent, true) ?: pulse.similarContentEnabled()

    override suspend fun getInitialStatus(): Queue.Status {
        val status = inner.getInitialStatus()
        if (isAccountPrimary()) return inner.expandConnectedAccountQueue(status, targetSize = 50)
        status.items.forEach { expandedIds.add(it.mediaId) }
        if (similarContentEnabled()) {
            status.items.asSequence()
                .map { it.mediaId }
                .filter { it.isNotBlank() && it != seed.id }
                .take(10)
                .forEach { expansionSeeds.addLast(it) }
        }
        val items = smart(status.items, keepOrderIfNoSeed = false)
        val index = if (items === status.items) status.mediaItemIndex else items.indexOfFirst { it.mediaId == seed.id }.coerceAtLeast(0)
        return status.copy(items = items, mediaItemIndex = index)
    }

    override fun hasNextPage(): Boolean = inner.hasNextPage() ||
        (similarContentEnabled() && expansionRounds < maxExpansionRounds && expansionSeeds.isNotEmpty())

    override suspend fun nextPage(): List<MediaItem> {
        if (accountPrimary == true) return if (inner.hasNextPage()) inner.nextPage() else emptyList()
        val provider = if (inner.hasNextPage()) {
            runCatching { inner.nextPage() }.getOrDefault(emptyList())
        } else emptyList()
        provider.forEach { expandedIds.add(it.mediaId) }
        if (provider.isNotEmpty()) {
            provider.asSequence().map { it.mediaId }.filter { it.isNotBlank() }
                .take(6).forEach { if (!queriedSeeds.contains(it)) expansionSeeds.addLast(it) }
        }
        return smart(provider, keepOrderIfNoSeed = true)
    }

    private suspend fun smart(
        items: List<MediaItem>,
        keepOrderIfNoSeed: Boolean,
    ): List<MediaItem> = runCatching {
        val music = items.filter { mi ->
            mi.mediaId == seed.id || (mi.metadata?.duration ?: -1).let { it <= 0 || it in 30..900 }
        }.ifEmpty { items }

        val candidatePool = ArrayList<MediaItem>(music)
        if (similarContentEnabled() && expansionRounds < maxExpansionRounds) {
            // One/two seed-specific expansions per queue page keep network use bounded while
            // providing a real continuation after the provider's short initial page.
            repeat(2) {
                val nextSeed = if (expansionSeeds.isEmpty()) return@repeat else expansionSeeds.removeFirst()
                if (!queriedSeeds.add(nextSeed)) return@repeat
                expansionRounds++
                val endpoint = YouTube.next(WatchEndpoint(videoId = nextSeed)).getOrNull()?.relatedEndpoint
                val related = endpoint?.let { YouTube.related(it).getOrNull()?.songs.orEmpty() }.orEmpty()
                related.take(20).map { it.toMediaItem() }.forEach { item ->
                    if (expandedIds.add(item.mediaId)) {
                        candidatePool.add(item)
                        if (expansionSeeds.size < 24 && item.mediaId != seed.id) expansionSeeds.addLast(item.mediaId)
                    }
                }
            }
        }

        if (candidatePool.isEmpty()) return@runCatching emptyList<MediaItem>()
        val ranked = pulse.rankForRadio(
            items = candidatePool.distinctBy { it.mediaId },
            idOf = { it.mediaId },
            artistIdsOf = { mi -> mi.metadata?.artists?.mapNotNull { it.id }.orEmpty() },
            seedId = seed.id.takeIf { candidatePool.any { it.mediaId == seed.id } },
            seedArtistIds = seedArtists,
        )
        if (ranked === candidatePool && keepOrderIfNoSeed) {
            candidatePool.distinctBy { it.mediaId }
        } else ranked
    }.getOrDefault(items)
}
