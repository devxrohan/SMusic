package com.devxrohan.smusic.utils

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.models.BrowseEndpoint
import com.devxrohan.smusic.innertube.pages.MoodAndGenres
import com.devxrohan.smusic.ui.utils.resize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Explore data, owned by the process (not by a screen), so it starts loading the moment the app
 * opens instead of when the user first reaches the Explore tab.
 *
 *  1. App start: read the disk cache (instant) -> refresh the list only if older than 6h.
 *  2. Artwork URLs: every card that has no URL yet is queued. Cards that are on screen jump the
 *     queue ([prioritize]); everything else is fetched in the background.
 *  3. As soon as a URL is known, the (small) image itself is downloaded into Coil's disk cache,
 *     so by the time the user opens Explore the pictures are already on the device.
 *
 * Every URL is persisted, so each artwork is looked up only once, ever.
 */
object ExplorePrefetcher {
    /** null = loading with nothing to show (skeleton); empty = failed with nothing to show. */
    val sections = MutableStateFlow<List<MoodAndGenres>?>(null)

    /** keyOf(endpoint) -> artwork URL. Kept separate so artwork arriving never rebuilds the grid. */
    val artwork = MutableStateFlow<Map<String, String>>(emptyMap())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val urgent = ArrayDeque<MoodAndGenres.Item>() // on screen right now
    private val background = ArrayDeque<MoodAndGenres.Item>() // everything else
    private val queued = HashSet<String>()
    private val fresh = ConcurrentHashMap<String, String>()
    private val preloaded = ConcurrentHashMap.newKeySet<String>()

    private lateinit var appContext: Context
    private lateinit var cacheFile: File
    private var started = false
    private var activeWorkers = 0
    private var flushScheduled = false
    private var lastSave = 0L

    fun keyOf(endpoint: BrowseEndpoint) = "${endpoint.browseId}|${endpoint.params.orEmpty()}"

    /** Cards are small: ask the server for a small image instead of decoding a huge one. */
    fun cardImageUrl(url: String): String = if (url.contains("ytimg.com")) url else url.resize(360, 360)

    /** Call once from Application.onCreate (after YouTube locale/proxy are configured). */
    fun start(context: Context) {
        synchronized(lock) {
            if (started) return
            started = true
        }
        appContext = context.applicationContext
        cacheFile = File(appContext.filesDir, CACHE_FILE)
        scope.launch { load(force = false) }
    }

    fun retry() {
        sections.value = null
        scope.launch { load(force = true) }
    }

    /** Called by the screen for the cards that are on (or next to) the screen. */
    fun prioritize(items: List<MoodAndGenres.Item>) {
        val known = artwork.value
        synchronized(lock) {
            items.forEach { item ->
                val key = keyOf(item.endpoint)
                if (key in known || fresh.containsKey(key)) return@forEach
                // Already waiting in the background queue? Pull it forward.
                if (!queued.add(key)) {
                    // Already queued: only act if it is still waiting in the background queue.
                    if (!background.remove(item)) return@forEach
                }
                urgent.addLast(item)
            }
        }
        pump()
    }

    private suspend fun load(force: Boolean) {
        val cache = if (force) null else readCache()
        if (cache != null) {
            artwork.value = cache.artwork
            sections.value = cache.sections
            // Pictures that were cached as URLs but never downloaded (e.g. app was killed) get fetched now.
            cache.artwork.values.forEach { preloadImage(it) }
            queueMissing(cache.sections)
        }

        val upToDate = cache != null && System.currentTimeMillis() - cache.savedAt < LIST_TTL_MS
        if (upToDate) return

        awaitNetworkIdentity()
        val fetched = YouTube.moodAndGenres().onFailure { reportException(it) }.getOrNull()
        if (fetched != null) {
            sections.value = fetched
            writeCache()
            queueMissing(fetched)
        } else if (sections.value == null) {
            // Nothing cached and the request failed: stop the skeleton, the UI offers a retry.
            sections.value = emptyList()
        }
    }

    /**
     * visitorData is filled in asynchronously at startup; a request sent before it exists can be
     * answered differently. Give it a short moment instead of racing it.
     */
    private suspend fun awaitNetworkIdentity() {
        withTimeoutOrNull(2_500) {
            while (YouTube.visitorData == null) delay(50)
        }
    }

    // ---------- artwork ----------

    private fun queueMissing(list: List<MoodAndGenres>) {
        val known = artwork.value
        synchronized(lock) {
            list.asSequence()
                .flatMap { it.items.asSequence() }
                .forEach { item ->
                    val key = keyOf(item.endpoint)
                    if (key !in known && queued.add(key)) background.addLast(item)
                }
        }
        pump()
    }

    private fun pump() {
        synchronized(lock) {
            if (activeWorkers >= MAX_WORKERS || (urgent.isEmpty() && background.isEmpty())) return
            activeWorkers++
        }
        scope.launch {
            try {
                while (true) {
                    // Visible cards first (newest first), then the rest in order.
                    val item = synchronized(lock) { urgent.removeLastOrNull() ?: background.removeFirstOrNull() }
                        ?: break
                    awaitNetworkIdentity()
                    val url = YouTube.moodGenreArtwork(item.endpoint.browseId, item.endpoint.params).getOrNull()
                    if (!url.isNullOrBlank()) {
                        fresh[keyOf(item.endpoint)] = url
                        preloadImage(url)
                        scheduleFlush()
                    }
                    delay(PAUSE_MS)
                }
            } finally {
                synchronized(lock) { activeWorkers-- }
                // Nothing left: make sure the last URLs reach the UI and the disk.
                if (synchronized(lock) { urgent.isEmpty() && background.isEmpty() && activeWorkers == 0 }) {
                    publishFresh()
                    writeCache()
                }
            }
        }
        pump() // start the next worker if there is more work
    }

    /** Download the small card image into Coil's disk cache; no bitmap is kept in memory. */
    private fun preloadImage(url: String) {
        if (!::appContext.isInitialized) return
        val small = cardImageUrl(url)
        if (!preloaded.add(small)) return
        val loader = appContext.imageLoader
        if (loader.diskCache == null) return // image cache disabled in settings: nothing to warm
        loader.enqueue(
            ImageRequest.Builder(appContext)
                .data(small)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .build(),
        )
    }

    private fun scheduleFlush() {
        synchronized(lock) {
            if (flushScheduled) return
            flushScheduled = true
        }
        scope.launch {
            delay(FLUSH_MS)
            synchronized(lock) { flushScheduled = false }
            publishFresh()
            val now = System.currentTimeMillis()
            if (now - lastSave > SAVE_EVERY_MS) {
                lastSave = now
                writeCache()
            }
        }
    }

    private fun publishFresh() {
        if (fresh.isNotEmpty()) artwork.value = artwork.value + fresh
    }

    // ---------- disk cache ----------

    private class Cache(val savedAt: Long, val sections: List<MoodAndGenres>, val artwork: Map<String, String>)

    private fun readCache(): Cache? = runCatching {
        if (!cacheFile.exists()) return null
        val root = JSONObject(cacheFile.readText())
        if (root.optInt("v") != CACHE_VERSION) return null
        val list = root.getJSONArray("sections").let { arr ->
            List(arr.length()) { i ->
                val s = arr.getJSONObject(i)
                val items = s.getJSONArray("items").let { ia ->
                    List(ia.length()) { j ->
                        val o = ia.getJSONObject(j)
                        MoodAndGenres.Item(
                            title = o.getString("t"),
                            stripeColor = o.getLong("c"),
                            endpoint = BrowseEndpoint(
                                browseId = o.getString("b"),
                                params = o.optString("p").takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
                MoodAndGenres(title = s.getString("title"), items = items)
            }
        }
        val art = root.optJSONObject("art")?.let { o ->
            o.keys().asSequence().associateWith { o.getString(it) }
        }.orEmpty()
        if (list.isEmpty()) null else Cache(root.getLong("savedAt"), list, art)
    }.getOrNull()

    @Synchronized
    private fun writeCache() {
        val list = sections.value?.takeIf { it.isNotEmpty() } ?: return
        runCatching {
            val arr = JSONArray()
            list.forEach { section ->
                val items = JSONArray()
                section.items.forEach { item ->
                    items.put(
                        JSONObject()
                            .put("t", item.title)
                            .put("c", item.stripeColor)
                            .put("b", item.endpoint.browseId)
                            .put("p", item.endpoint.params.orEmpty()),
                    )
                }
                arr.put(JSONObject().put("title", section.title).put("items", items))
            }
            val art = JSONObject()
            (artwork.value + fresh).forEach { (k, v) -> art.put(k, v) }
            val root = JSONObject()
                .put("v", CACHE_VERSION)
                .put("savedAt", System.currentTimeMillis())
                .put("sections", arr)
                .put("art", art)
            val tmp = File(cacheFile.parentFile, "$CACHE_FILE.tmp")
            tmp.writeText(root.toString())
            tmp.renameTo(cacheFile)
        }
    }

    private const val CACHE_FILE = "explore_mood_genres.json"
    private const val CACHE_VERSION = 2
    private const val LIST_TTL_MS = 6L * 60 * 60 * 1000 // refresh the list itself every 6 hours
    private const val MAX_WORKERS = 3
    private const val PAUSE_MS = 40L
    private const val FLUSH_MS = 150L
    private const val SAVE_EVERY_MS = 3000L
}
