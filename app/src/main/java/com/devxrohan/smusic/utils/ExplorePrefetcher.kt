package com.devxrohan.smusic.utils

import android.content.Context
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Cached section metadata is shared by the process; artwork is resolved for visible cards only. */
object ExplorePrefetcher {
    /** null = loading with nothing to show (skeleton); empty = failed with nothing to show. */
    val sections = MutableStateFlow<List<MoodAndGenres>?>(null)

    /** keyOf(endpoint) -> artwork URL. Kept separate so artwork arriving never rebuilds the grid. */
    val artwork = MutableStateFlow<Map<String, String>>(emptyMap())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val identityGate = Mutex()
    private var identityWaited = false
    private val urgent = ArrayDeque<MoodAndGenres.Item>() // on screen right now
    private val queued = HashSet<String>()
    private val fresh = ConcurrentHashMap<String, String>()
    private val failedLookups = ConcurrentHashMap<String, Long>()
    @Volatile private var visible = false

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

    fun setVisible(value: Boolean) {
        visible = value
        if (value) pump()
    }

    fun retry() {
        failedLookups.clear()
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
                if (!item.thumbnailUrl.isNullOrBlank()) return@forEach
                val lastFailure = failedLookups[key]
                if (lastFailure != null && android.os.SystemClock.elapsedRealtime() - lastFailure < 60_000) return@forEach
                if (!queued.add(key)) return@forEach
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
        }

        val upToDate = cache != null && System.currentTimeMillis() - cache.savedAt < LIST_TTL_MS
        if (upToDate) return

        awaitNetworkIdentity()
        val fetched = YouTube.moodAndGenres().onFailure { reportException(it) }.getOrNull()
        if (fetched != null) {
            sections.value = fetched
            writeCache()
        } else if (sections.value == null) {
            // Nothing cached and the request failed: stop the skeleton, the UI offers a retry.
            sections.value = emptyList()
        }
    }

    /** visitorData is filled in asynchronously at startup; a request sent before it exists can be
     * answered differently. Give it a short moment instead of racing it. */
    private suspend fun awaitNetworkIdentity() {
        if (YouTube.visitorData != null) return
        identityGate.withLock {
            if (!identityWaited) {
                withTimeoutOrNull(2_500) { while (YouTube.visitorData == null) delay(50) }
                identityWaited = true
            }
        }
    }

    // ---------- artwork ----------

    private fun pump() {
        synchronized(lock) {
            if (!visible || activeWorkers >= MAX_WORKERS || urgent.isEmpty()) return
            activeWorkers++
        }
        scope.launch {
            try {
                while (true) {
                    if (!visible) break
                    val item = synchronized(lock) { urgent.removeFirstOrNull() } ?: break
                    val key = keyOf(item.endpoint)
                    try {
                        awaitNetworkIdentity()
                        val url = YouTube.moodGenreArtwork(item.endpoint.browseId, item.endpoint.params).getOrNull()
                        if (!url.isNullOrBlank()) {
                            fresh[key] = url
                            scheduleFlush()
                        } else failedLookups[key] = android.os.SystemClock.elapsedRealtime()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failedLookups[key] = android.os.SystemClock.elapsedRealtime() }
                    finally { synchronized(lock) { queued.remove(key) } }
                    delay(PAUSE_MS)
                }
            } finally {
                synchronized(lock) { activeWorkers-- }
                // Nothing left: make sure the last URLs reach the UI and the disk.
                if (synchronized(lock) { urgent.isEmpty() && activeWorkers == 0 }) {
                    publishFresh()
                    writeCache()
                }
                if (visible) pump()
            }
        }
        pump() // start the next worker if there is more work
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
