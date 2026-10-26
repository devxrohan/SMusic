package com.devxrohan.smusic.playback.queues

import androidx.media3.common.MediaItem
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.models.WatchEndpoint
import com.devxrohan.smusic.innertube.models.BrowseEndpoint
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.devxrohan.smusic.extensions.toMediaItem
import com.devxrohan.smusic.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = null
    private var relatedEndpoint: BrowseEndpoint? = null
    private var accountExpansionComplete = false

    override suspend fun getInitialStatus(): Queue.Status {
        val nextResult =
            withContext(IO) {
                YouTube.next(endpoint, continuation).getOrThrow()
            }
        endpoint = nextResult.endpoint
        continuation = nextResult.continuation
        relatedEndpoint = nextResult.relatedEndpoint
        return Queue.Status(
            title = nextResult.title,
            items = nextResult.items.map { it.toMediaItem() },
            mediaItemIndex = nextResult.currentIndex ?: 0,
        )
    }

    override fun hasNextPage(): Boolean = continuation != null

    /**
     * Expand a short account radio using only related results obtained through the authenticated
     * YouTube Music session. This is deliberately called only by PulseRadioQueue after verifying
     * that a YouTube account is connected; guest queues continue using Pulse's existing expansion.
     */
    suspend fun expandConnectedAccountQueue(status: Queue.Status, targetSize: Int = 50): Queue.Status {
        if (accountExpansionComplete || status.items.size >= targetSize) return status
        accountExpansionComplete = true
        val seedEndpoint = relatedEndpoint ?: return status
        val resultItems = status.items.toMutableList()
        val seen = resultItems.mapTo(linkedSetOf()) { it.mediaId }
        val frontier = ArrayDeque<String>()

        suspend fun addRelated(endpoint: BrowseEndpoint?) {
            if (endpoint == null || resultItems.size >= targetSize) return
            val related = YouTube.related(endpoint, authenticated = true).getOrNull()?.songs.orEmpty()
            for (song in related) {
                if (resultItems.size >= targetSize) break
                if (seen.add(song.id)) {
                    resultItems += song.toMediaItem()
                    frontier.addLast(song.id)
                }
            }
        }

        // The initial related shelf is normally the largest useful batch.
        addRelated(seedEndpoint)

        // If the account endpoint only returns a short shelf, walk a few of those account-derived
        // songs in parallel and collect their related shelves. Bound requests and queue size so this
        // never turns into an unbounded recommendation crawler.
        var rounds = 0
        while (resultItems.size < targetSize && frontier.isNotEmpty() && rounds < 4) {
            val seeds = buildList {
                while (size < 4 && frontier.isNotEmpty()) add(frontier.removeFirst())
            }
            if (seeds.isEmpty()) break
            rounds++
            val endpoints = coroutineScope {
                seeds.map { id -> async {
                    YouTube.next(WatchEndpoint(videoId = id)).getOrNull()?.relatedEndpoint
                } }.awaitAll()
            }
            for (endpoint in endpoints) {
                addRelated(endpoint)
                if (resultItems.size >= targetSize) break
            }
        }
        return status.copy(items = resultItems)
    }

    override suspend fun nextPage(): List<MediaItem> {
        val nextResult =
            withContext(IO) {
                YouTube.next(endpoint, continuation).getOrThrow()
            }
        endpoint = nextResult.endpoint
        continuation = nextResult.continuation
        relatedEndpoint = nextResult.relatedEndpoint
        return nextResult.items.map { it.toMediaItem() }
    }

    companion object {
        fun radio(song: MediaMetadata) = YouTubeQueue(WatchEndpoint(song.id), song)
    }
}
