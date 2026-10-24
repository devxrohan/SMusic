package com.devxrohan.smusic.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.viewModelScope
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.models.PlaylistItem
import com.devxrohan.smusic.innertube.models.AlbumItem
import com.devxrohan.smusic.innertube.models.SongItem
import com.devxrohan.smusic.innertube.models.ArtistItem
import com.devxrohan.smusic.innertube.models.WatchEndpoint
import com.devxrohan.smusic.innertube.models.YTItem
import com.devxrohan.smusic.innertube.pages.ExplorePage
import com.devxrohan.smusic.innertube.pages.HomePage
import com.devxrohan.smusic.innertube.utils.completedLibraryPage
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.db.entities.Album
import com.devxrohan.smusic.db.entities.Artist
import com.devxrohan.smusic.db.entities.LocalItem
import com.devxrohan.smusic.db.entities.Playlist
import com.devxrohan.smusic.db.entities.Song
import com.devxrohan.smusic.models.SimilarRecommendation
import com.devxrohan.smusic.playback.DownloadUtil
import androidx.media3.exoplayer.offline.Download
import com.devxrohan.smusic.constants.InnerTubeCookieKey
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.AccountSyncGate
import com.devxrohan.smusic.utils.getSuspend
import com.devxrohan.smusic.utils.get
import com.devxrohan.smusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** A Pulse-ranked row whose items come straight from YouTube (the account's endpoints when connected). */
data class PulseYtShelf(val id: String, val title: String, val label: String, val items: List<YTItem>)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
    private val pulseEngine: com.devxrohan.smusic.pulse.PulseEngine,
    private val downloadUtil: DownloadUtil,
    private val syncUtils: com.devxrohan.smusic.utils.SyncUtils,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    // Render the Home skeleton immediately while the first local/provider data is prepared.
    val isLoading = MutableStateFlow(true)

    /** SMusic's own Quick Picks (local history / cache / downloads). Never mixed with YouTube's. */
    val quickPicks = MutableStateFlow<List<Song>?>(null)
    /** YouTube Music's own Quick Picks. Only filled while a YouTube account is connected. */
    val youtubeQuickPicks = MutableStateFlow<List<SongItem>>(emptyList())
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val recentActivity = MutableStateFlow<List<YTItem>?>(null)
    val recentPlaylistsDb = MutableStateFlow<List<Playlist>?>(null)

    /** Pulse rows built from the connected account's own YouTube results (account mode only). */
    val pulseYtShelves = MutableStateFlow<List<PulseYtShelf>>(emptyList())

    /** Additive Pulse shelves. Empty for new users, so Home looks exactly like stock SMusic. */
    val pulseShelves = MutableStateFlow<List<com.devxrohan.smusic.pulse.PulseShelf>>(emptyList())
    /** "Discover Your Next Favorite": unfamiliar music from the source that sits close to the user's taste. */
    val pulseDiscover = MutableStateFlow<List<YTItem>>(emptyList())

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    private val homeRandom = kotlin.random.Random(System.currentTimeMillis())
    private var loadJob: Job? = null
    private var refreshJob: Job? = null
    private var accountLoadJob: Job? = null

    /**
     * Bumped every time the account session changes (sign in / sign out). Every publish checks it, so
     * a request that was started for the previous account can never write its feed back after the
     * session changed (that was the "account feed stays after sign-out until restart" glitch).
     */
    @Volatile private var sessionEpoch = 0
    @Volatile private var refreshCycle = 0

    private data class Session(val isJioSaavn: Boolean, val signedIn: Boolean)

    private suspend fun readSession(): Session {
        val provider = context.dataStore.getSuspend(com.devxrohan.smusic.constants.MusicProviderKey, "YT")
        // The saved cookie is the single source of truth. Re-apply it to the shared client on every load.
        val cookie = context.dataStore.getSuspend(InnerTubeCookieKey, "")
        val signedIn = cookie.isNotBlank() && runCatching {
            "SAPISID" in com.devxrohan.smusic.innertube.utils.parseCookieString(cookie)
        }.getOrDefault(false)
        YouTube.cookie = cookie.takeIf { signedIn }
        return Session(isJioSaavn = provider == "JIOSAAVN", signedIn = signedIn)
    }

    /** runCatching that never swallows coroutine cancellation (it used to let cancelled loads keep publishing). */
    private suspend inline fun <T> attempt(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    private fun providerMatches(id: String, isJioSaavn: Boolean): Boolean =
        if (isJioSaavn) id.startsWith("JS:") else !id.startsWith("JS:")

    private val quickPicksTitle = Regex("(?i)quick\\s*picks?|त्वरित|क्विक")

    /** The real YouTube Music "Quick picks" shelf: a list-style shelf of songs (it used to be dropped by the parser). */
    private fun findQuickPicksSection(sections: List<HomePage.Section>): HomePage.Section? {
        val lists = sections.filter { it.isList && it.items.isNotEmpty() && it.items.all { item -> item is SongItem } }
        return lists.firstOrNull { quickPicksTitle.containsMatchIn(it.title) }
    }

    /**
     * Rebuilds EVERY row of Home in the current mode.
     *
     *  - Signed in: YouTube is primary. Every feed row, the real Quick picks shelf, similar rows and the
     *    Pulse rows come from authenticated (account) YouTube requests; Pulse only ranks its own rows and
     *    never reorders YouTube's feed or queue.
     *  - Guest: requests are anonymous and Pulse is primary - it shapes rows from the user's own taste.
     *
     * progressive = true  : first load / account switch - every row appears as soon as it is ready.
     * progressive = false : pull-to-refresh - rows are prepared off-screen and swapped in together at the
     *                       first paint (first page of the feed + local rows), then slower rows fill in.
     */
    private suspend fun loadAll(progressive: Boolean, onFirstPaint: (() -> Unit)? = null) = coroutineScope {
        val epoch = sessionEpoch
        val session = readSession()
        val isGuest = !session.signedIn
        val isJio = session.isJioSaavn
        val cycle = refreshCycle
        val startedAt = System.currentTimeMillis()

        fun alive() = isActive && epoch == sessionEpoch
        val staged = mutableListOf<() -> Unit>()
        var direct = progressive
        fun publish(block: () -> Unit) {
            if (!alive()) return
            if (direct) block() else staged += block
        }
        fun flushNow() {
            if (!alive() || direct) return
            staged.forEach { it() }
            staged.clear()
            direct = true
            isLoading.value = false
            onFirstPaint?.invoke()
        }
        fun remaining(ms: Long) = (ms - (System.currentTimeMillis() - startedAt)).coerceAtLeast(150L)

        // ---------- 1. Start every network request immediately (they all run in parallel) ----------
        // Only the first feed pages are awaited; the rest is loaded in the background (see below).
        val homeD = if (isJio) null else async(Dispatchers.IO) {
            withTimeoutOrNull(9_000) { YouTube.home(maxContinuations = 1).onFailure { reportException(it) }.getOrNull() }
        }
        val exploreD = if (isJio) null else async(Dispatchers.IO) {
            withTimeoutOrNull(9_000) { YouTube.explore().onFailure { reportException(it) }.getOrNull() }
        }
        val playlistsD = if (isJio || !session.signedIn) null else async(Dispatchers.IO) {
            withTimeoutOrNull(9_000) {
                YouTube.library("FEmusic_liked_playlists").completedLibraryPage()
                    .onFailure { reportException(it) }.getOrNull()
                    ?.items?.filterIsInstance<PlaylistItem>()?.filterNot { it.id == "SE" }
            }
        }
        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 2
        val similarD = if (isJio) null else async(Dispatchers.IO) { fetchSimilarCandidates(fromTimeStamp, accountMode = !isGuest) }
        val jioD = if (!isJio) null else async(Dispatchers.IO) {
            withTimeoutOrNull(9_000) { com.devxrohan.smusic.jiosaavn.JioSaavnApi.getTrendingSongs().getOrNull() }
        }

        // Pulse housekeeping must never delay a refresh (own scope: coroutineScope would wait for it).
        viewModelScope.launch(Dispatchers.IO) {
            attempt { pulseEngine.backfillIfNeeded() }
            attempt {
                pulseEngine.syncUserSignals(
                    downloadUtil.downloads.value.filterValues { it.state == Download.STATE_COMPLETED }.keys
                )
            }
        }

        // ---------- 2. Local rows (database only: fast) while the network is in flight ----------
        val pulseUsedIds = mutableSetOf<String>()
        val discoverPool = mutableListOf<YTItem>()

        val historyIds: Set<String> = if (isGuest) attempt {
            database.events().first().map { it.event.songId }.toSet()
        }.getOrDefault(emptySet()) else emptySet()

        // Quick Picks: SMusic's own, from local history/cache/downloads. YouTube's are a separate row.
        val databaseQuickPicks = attempt { database.quickPicks().first() }.getOrDefault(emptyList())
            .filter { providerMatches(it.id, isJio) }
        val playedFallback = if (databaseQuickPicks.isEmpty() || !isGuest) {
            attempt { database.mostPlayedSongs(0L, limit = 80, offset = 0).first() }.getOrDefault(emptyList())
                .filter { providerMatches(it.id, isJio) }
        } else emptyList()
        val downloadedQuickPicks = downloadUtil.downloads.value
            .filterValues { it.state == Download.STATE_COMPLETED }.keys
            .filter { providerMatches(it, isJio) }
            .mapNotNull { id -> attempt { database.song(id).first() }.getOrNull() }
        val newQuickPicks = (databaseQuickPicks + playedFallback + downloadedQuickPicks)
            .distinctBy { it.id }.shuffled(homeRandom).take(20)
        publish {
            quickPicks.value = newQuickPicks
            if (newQuickPicks.isNotEmpty()) isLoading.value = false
        }

        val newForgotten: List<Song> = if (isGuest) attempt {
            database.forgottenFavorites().first()
                .filter { it.id in historyIds && providerMatches(it.id, isJio) }
                .shuffled(homeRandom).take(20)
        }.getOrDefault(emptyList()) else emptyList()
        publish { forgottenFavorites.value = newForgotten }

        val keepSongs = attempt { database.mostPlayedSongs(fromTimeStamp, limit = 50, offset = 0).first() }
            .getOrDefault(emptyList()).filter { providerMatches(it.id, isJio) }.shuffled(homeRandom).take(10)
        val keepArtists = attempt { database.mostPlayedArtists(fromTimeStamp, limit = 50, offset = 0).first() }
            .getOrDefault(emptyList())
            .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null && providerMatches(it.id, isJio) }
            .shuffled(homeRandom).take(5)
        val keepAlbums = attempt { database.mostPlayedAlbums(fromTimeStamp, limit = 30, offset = 0).first() }
            .getOrDefault(emptyList())
            .filter { it.album.thumbnailUrl != null && providerMatches(it.id, isJio) }
            .shuffled(homeRandom).take(5)
        val originalKeepListening: List<LocalItem> = (keepSongs + keepArtists + keepAlbums).shuffled(homeRandom)
        val newKeepListening: List<LocalItem> = if (!isGuest) originalKeepListening else attempt {
            val songPool = database.mostPlayedSongs(0L, limit = 160, offset = 0).first().filter { providerMatches(it.id, isJio) }
            val artistPool = database.mostPlayedArtists(0L, limit = 50, offset = 0).first()
                .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null && providerMatches(it.id, isJio) }
            val albumPool = database.mostPlayedAlbums(0L, limit = 50, offset = 0).first()
                .filter { it.album.thumbnailUrl != null && providerMatches(it.id, isJio) }
            pulseEngine.blendKeepListening(originalKeepListening, songPool, artistPool, albumPool, pulseUsedIds)
        }.getOrDefault(originalKeepListening)
        publish {
            keepListening.value = newKeepListening
            allLocalItems.value = (newQuickPicks + newForgotten + newKeepListening).filter { it is Song || it is Album }
        }

        // Pulse shelf planning can scan a large library. Run it concurrently so the first provider page
        // and the fast local rows render immediately instead of waiting for recommendation bookkeeping.
        val newShelvesD = async(Dispatchers.IO) {
            if (!isGuest) emptyList() else attempt {
                val shelfPool = database.allSongs().first()
                    .filter { providerMatches(it.id, isJio) && it.id in historyIds }
                val poolArtistIds = shelfPool.flatMap { it.artists }.map { it.id }.toSet()
                val shelfArtists = database.artistsByPlayTimeAsc().first().asReversed().take(200)
                    .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null && providerMatches(it.id, isJio) && it.id in poolArtistIds }
                val poolAlbumIds = shelfPool.mapNotNull { it.album?.id }.toSet()
                val shelfAlbums = database.albumsByPlayTimeAsc().first().asReversed().take(200)
                    .filter { it.album.thumbnailUrl != null && providerMatches(it.id, isJio) && it.id in poolAlbumIds }
                attempt { pulseEngine.syncSmartPlaylists(shelfPool) }
                pulseEngine.planShelves(shelfPool, mutableSetOf(), shelfArtists, shelfAlbums)
            }.getOrDefault(emptyList())
        }

        // ---------- 3. Network rows ----------
        if (isJio) {
            jioD?.await()?.let { songs ->
                publish {
                    homePage.value = HomePage(
                        sections = listOf(HomePage.Section("Trending Songs", "JioSaavn", null, null, songs))
                    )
                    explorePage.value = ExplorePage(emptyList(), emptyList())
                    similarRecommendations.value = emptyList()
                    youtubeQuickPicks.value = emptyList()
                    pulseYtShelves.value = emptyList()
                }
            }
            val plannedShelves = newShelvesD.await()
            publish { pulseShelves.value = plannedShelves }
            flushNow()
        } else {
            // First page of the feed decides the first paint. On refresh we wait at most ~2s for it.
            val firstPage = if (progressive) homeD?.await() else withTimeoutOrNull(remaining(2_000)) { homeD?.await() }
            val similarFirst = if (progressive) null else withTimeoutOrNull(remaining(2_000)) { similarD?.await() }

            // --- Similar To rows (+ Pulse ranking, de-duplicated across rows). Pulse row in both modes. ---
            val canonicalKeys = HashSet<String>()
            fun norm(value: String) = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .lowercase()
                .replace(Regex("\\([^)]*\\)"), " ")
                .replace(Regex("\\b(official audio|official video|lyrics|lyric video|audio|video|visualizer|remastered)\\b"), " ")
                .replace(Regex("[^a-z0-9]+"), "")
            fun canonicalKey(item: YTItem): String = when (item) {
                // Same recording often appears under different artist metadata/credits. Track title is a safer
                // duplicate key than title+artists, which allowed the same song to appear several times.
                is SongItem -> "song:" + norm(item.title) + ":" + (item.duration?.div(8)?.toString() ?: "unknown")
                is AlbumItem -> "album:" + norm(item.title) + ":" + item.artists.orEmpty().joinToString("|") { norm(it.name) }
                is ArtistItem -> "artist:" + norm(item.title)
                else -> item.id
            }
            suspend fun buildSimilar(raw: List<SimilarRecommendation>): List<SimilarRecommendation> = raw.mapNotNull { rec ->
                attempt {
                    discoverPool.addAll(rec.items)
                    pulseEngine.learnRelated(rec.title, rec.items)
                    val candidates = pulseEngine.rankSimilar(rec.items, pulseUsedIds, limit = 20)
                    val unique = candidates.filter { canonicalKeys.add(canonicalKey(it)) }.take(12)
                    if (unique.size < 6) null else rec.copy(items = unique)
                }.getOrNull()
            }
            var similarRows: List<SimilarRecommendation> = emptyList()
            if (similarFirst != null) {
                similarRows = buildSimilar(similarFirst)
                val rows = similarRows
                publish { if (rows.isNotEmpty() || similarRecommendations.value.isNullOrEmpty()) similarRecommendations.value = rows }
            }

            // --- Account playlists ---
            val newAccountPlaylists = if (progressive || playlistsD?.isCompleted == true) playlistsD?.await() else null
            publish {
                if (!session.signedIn) accountPlaylists.value = emptyList()
                else if (newAccountPlaylists != null) accountPlaylists.value = newAccountPlaylists
            }

            // --- Home feed ---
            suspend fun prepareSections(sections: List<HomePage.Section>, rotate: Int): List<HomePage.Section> =
                sections.filterNot { quickPicksTitle.containsMatchIn(it.title) }.mapNotNull { section ->
                    val distinctItems = section.items.distinctBy(::canonicalKey)
                    if (distinctItems.isEmpty()) null
                    else if (!isGuest || section.isList) section.copy(items = distinctItems) // account order stays intact
                    else {
                        // Guest: Pulse is primary. Rank the shelf by the user's taste, then rotate so each refresh differs.
                        val ranked = attempt { pulseEngine.rankSimilar(distinctItems, mutableSetOf(), limit = distinctItems.size) }
                            .getOrDefault(emptyList())
                        val rankedIds = ranked.map { canonicalKey(it) }.toSet()
                        val ordered = ranked + distinctItems.filterNot { canonicalKey(it) in rankedIds }
                        val k = if (ordered.size > 1) (rotate * 3) % ordered.size else 0
                        section.copy(items = ordered.drop(k) + ordered.take(k))
                    }
                }

            // Canonical tracks already displayed in the first-page account/guest feed; continuation pages
            // use this same set so duplicate recordings under alternate artist credits are suppressed.
            val homeFeedKeys = mutableSetOf<String>()
            var activePage: HomePage? = firstPage
            suspend fun handleFirstPage(page: HomePage) {
                var sections = page.sections
                var realQuickPicks: List<SongItem> = emptyList()
                val actualQuickPicks = sections.firstOrNull { quickPicksTitle.containsMatchIn(it.title) }
                if (!isGuest) {
                    // Signed-in YouTube's own Quick picks is a dedicated row, never duplicated in the feed.
                    val qp = findQuickPicksSection(sections)
                    if (qp != null) {
                        realQuickPicks = qp.items.filterIsInstance<SongItem>().distinctBy { it.id }.take(24)
                        sections = sections.filterNot { it === qp }
                    }
                } else if (actualQuickPicks != null) {
                    // Anonymous YouTube also returns a Quick picks list. Do not show it as a fourth
                    // competing Quick Picks row in guest mode; guest recommendations come from Pulse.
                    sections = sections.filterNot { it === actualQuickPicks }
                }
                realQuickPicks.forEach { homeFeedKeys.add(canonicalKey(it)) }
                val preparedSections = prepareSections(sections, cycle).mapNotNull { section ->
                    val unique = section.items.filter { homeFeedKeys.add(canonicalKey(it)) }
                    if (unique.isEmpty()) null else section.copy(items = unique)
                }
                val prepared = page.copy(sections = preparedSections)
                val providerItems = (realQuickPicks + prepared.sections.flatMap { it.items }).distinctBy { canonicalKey(it) }
                discoverPool.addAll(providerItems)
                val pickedPool: List<YTItem> = providerItems + similarRows.flatMap { it.items }

                val newDiscover = attempt { pulseEngine.discoverNext(discoverPool, pulseUsedIds) }.getOrDefault(emptyList())
                    .ifEmpty { if (isGuest) providerItems.shuffled(homeRandom).take(12) else emptyList() }
                val newPulseYt: List<PulseYtShelf> = if (isGuest) emptyList() else {
                    val picked = attempt { pulseEngine.pickedForYou(pickedPool, pulseUsedIds, limit = 24) }
                        .getOrDefault(emptyList())
                    // Keep account-backed Pulse shelves varied: no artist can dominate the whole row,
                    // and the same track title cannot leak in under alternate artist credits.
                    val usedTrackKeys = mutableSetOf<String>()
                    val perArtist = mutableMapOf<String, Int>()
                    val balanced = picked.filter { item ->
                        val key = canonicalKey(item)
                        if (!usedTrackKeys.add(key)) return@filter false
                        if (item is SongItem) {
                            val artistKey = item.artists.firstOrNull()?.id ?: item.artists.firstOrNull()?.name.orEmpty()
                            val count = perArtist[artistKey] ?: 0
                            if (count >= 2) return@filter false
                            perArtist[artistKey] = count + 1
                        }
                        true
                    }
                    if (balanced.size >= 12) {
                        balanced.chunked(6).mapIndexedNotNull { index, items ->
                            if (items.size < 6) null else PulseYtShelf(
                                id = "pulse_picked_${index + 1}",
                                title = if (index == 0) "Picked for you" else "Your Pulse Mix",
                                label = "Pulse · from your YouTube account",
                                items = items
                            )
                        }
                    } else if (balanced.size >= 6) {
                        listOf(PulseYtShelf("pulse_picked", "Picked for you", "Pulse · from your YouTube account", balanced.take(12)))
                    } else emptyList()
                }
                publish {
                    homePage.value = prepared
                    isLoading.value = false
                    youtubeQuickPicks.value = if (session.signedIn) realQuickPicks else emptyList()
                    pulseDiscover.value = newDiscover
                    pulseYtShelves.value = newPulseYt
                    allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                        prepared.sections.flatMap { it.items } + explorePage.value?.newReleaseAlbums.orEmpty()
                }
            }
            if (firstPage != null) handleFirstPage(firstPage) else {
                publish { if (!session.signedIn) { youtubeQuickPicks.value = emptyList(); pulseYtShelves.value = emptyList() } }
            }

            // First paint: local rows + first feed pages are swapped in together and the spinner stops here.
            flushNow()

            // ---------- 4. Slower parts fill in without blocking the spinner ----------
            // If the initial provider request failed/timed out, retry automatically after local rows
            // are already visible. This removes the old requirement to pull-to-refresh after reopening Home.
            if (firstPage == null && alive()) {
                var late = withTimeoutOrNull(7_000) { homeD?.await() }
                if (late == null && alive()) {
                    late = withTimeoutOrNull(7_000) {
                        YouTube.home(maxContinuations = 1).onFailure { reportException(it) }.getOrNull()
                    }
                }
                if (late != null && alive()) {
                    activePage = late
                    handleFirstPage(late)
                }
            }
            // Remaining feed pages. Fetch one continuation at a time and append each page as soon as
            // it arrives; never hold every additional Home row until the slowest continuation finishes.
            val firstMoreToken = activePage?.continuation
            if (firstMoreToken != null) launch(Dispatchers.IO) {
                var token: String? = firstMoreToken
                var pageIndex = 0
                val seenPageKeys = mutableSetOf<String>()
                while (token != null && pageIndex < 8 && alive()) {
                    val next = withTimeoutOrNull(7_000) {
                        YouTube.homeMore(token!!, maxContinuations = 1).onFailure { reportException(it) }.getOrNull()
                    } ?: break
                    val prepared = prepareSections(next.sections, cycle + pageIndex + 1)
                    publish {
                        val current = homePage.value
                        if (current != null) {
                            val known = current.sections.map { section ->
                                section.title.lowercase() + "|" + (section.items.firstOrNull()?.let(::canonicalKey) ?: "empty")
                            }.toMutableSet()
                            val additions = prepared.mapNotNull { section ->
                                val uniqueItems = section.items.filter { homeFeedKeys.add(canonicalKey(it)) }
                                if (uniqueItems.isEmpty()) return@mapNotNull null
                                val cleaned = section.copy(items = uniqueItems)
                                val key = cleaned.title.lowercase() + "|" + (cleaned.items.firstOrNull()?.let(::canonicalKey) ?: "empty")
                                if (key !in known && seenPageKeys.add(key)) cleaned else null
                            }
                            if (additions.isNotEmpty()) {
                                homePage.value = current.copy(sections = current.sections + additions, continuation = next.continuation)
                            } else if (current.continuation != next.continuation) {
                                homePage.value = current.copy(continuation = next.continuation)
                            }
                        }
                    }
                    token = next.continuation
                    pageIndex++
                    if (next.sections.isEmpty() && token == null) break
                }
                if (alive()) homePage.value = homePage.value?.copy(continuation = null)
            }

            // The first screen is already on screen; publish Pulse shelves whenever their async planning finishes.
            val plannedShelves = newShelvesD.await()
            publish { pulseShelves.value = plannedShelves }

            if (similarFirst == null) {
                val rows = buildSimilar(similarD?.await().orEmpty())
                publish { if (rows.isNotEmpty() || similarRecommendations.value.isNullOrEmpty()) similarRecommendations.value = rows }
            }
            if (!(progressive || playlistsD?.isCompleted == true)) {
                val pls = playlistsD?.await()
                publish { if (session.signedIn && pls != null) accountPlaylists.value = pls }
            }

            // Explore new releases.
            val explore = exploreD?.await()
            if (explore != null) {
                val bookmarked = attempt { database.artistsBookmarkedByCreateDateAsc().first() }.getOrDefault(emptyList())
                val artistIds = bookmarked.map(Artist::id).toHashSet()
                val favouriteIds = bookmarked.filter { it.artist.bookmarkedAt != null }.map { it.id }.toHashSet()
                val sortedReleases = explore.newReleaseAlbums.sortedBy { album ->
                    if (album.artists.orEmpty().any { it.id in favouriteIds }) 0
                    else if (album.artists.orEmpty().any { it.id in artistIds }) 1 else 2
                }
                val rankedReleases = if (isGuest) attempt { pulseEngine.rankNewReleases(sortedReleases) }.getOrDefault(sortedReleases) else sortedReleases
                publish { explorePage.value = explore.copy(newReleaseAlbums = rankedReleases) }
            }

        }

        // If nothing flushed yet (e.g. offline), still release the staged local rows.
        flushNow()
        if (alive()) {
            isLoading.value = false
            // Exposure bookkeeping happens after the UI already has the new rows.
            viewModelScope.launch(Dispatchers.IO) {
                attempt {
                    pulseShelves.value.forEach { shelf ->
                        pulseEngine.markShown(
                            shelf.items.map { it.id },
                            com.devxrohan.smusic.pulse.PulseExposureMeta(source = "HOME", shelf = shelf.id, position = 0, reason = shelf.title)
                        )
                    }
                    if (pulseDiscover.value.isNotEmpty()) {
                        pulseEngine.markShown(
                            pulseDiscover.value.map { it.id },
                            com.devxrohan.smusic.pulse.PulseExposureMeta(source = "HOME", shelf = "pulse_discover", position = 0, reason = "New for your taste")
                        )
                    }
                    pulseYtShelves.value.forEach { shelf ->
                        pulseEngine.markShown(
                            shelf.items.map { it.id },
                            com.devxrohan.smusic.pulse.PulseExposureMeta(source = "HOME", shelf = shelf.id, position = 0, reason = shelf.title)
                        )
                    }
                    similarRecommendations.value.orEmpty().forEach { recommendation ->
                        pulseEngine.markShown(
                            recommendation.items.map { it.id },
                            com.devxrohan.smusic.pulse.PulseExposureMeta(
                                source = "SIMILAR", shelf = "similar_${recommendation.title.id}", position = 0, reason = "Similar to this listening anchor"
                            )
                        )
                    }
                }
            }
        }
    }

    /** Fetches the raw "Similar to" rows. All anchors run in parallel with a per-request time limit. */
    private suspend fun fetchSimilarCandidates(fromTimeStamp: Long, accountMode: Boolean): List<SimilarRecommendation> = coroutineScope {
        val gate = Semaphore(6)
        val artistCandidates = attempt {
            val played = database.mostPlayedArtists(fromTimeStamp, limit = 30).first().filter { it.artist.isYouTubeArtist }
            // Account connected: the artists the user follows on YouTube (synced) are anchors too.
            val followed = if (accountMode) database.artistsBookmarkedByCreateDateAsc().first().filter { it.artist.isYouTubeArtist } else emptyList()
            (played + followed).distinctBy { it.id }
        }.getOrDefault(emptyList())
        // Shuffle first so a refresh can pick different anchors, then let Pulse rank them.
        val artistAnchors = attempt { pulseEngine.similarAnchors(artistCandidates.shuffled(homeRandom), 5) }
            .getOrElse { artistCandidates.shuffled(homeRandom).take(5) }
        val songAnchors = attempt {
            val played = database.mostPlayedSongs(fromTimeStamp, limit = 40).first().filter { it.album != null }
            // Account connected: songs liked on YouTube (synced) are anchors too.
            val liked = if (accountMode) database.likedSongsByNameAsc().first().filter { it.album != null }.shuffled(homeRandom).take(15) else emptyList()
            (played + liked).distinctBy { it.id }
        }.getOrDefault(emptyList()).shuffled(homeRandom).take(5)

        val artistJobs = artistAnchors.map { artist ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    withTimeoutOrNull(5_000) {
                        val items = mutableListOf<YTItem>()
                        YouTube.artist(artist.id).onSuccess { page ->
                            page.sections.forEach { section ->
                                items += section.items.filter { it is SongItem || it is AlbumItem || it is ArtistItem }
                                    .filterNot { item ->
                                        when (item) {
                                            is ArtistItem -> item.id == artist.id
                                            is SongItem -> item.artists.any { it.id == artist.id }
                                            is AlbumItem -> item.artists.orEmpty().any { it.id == artist.id }
                                            else -> false
                                        }
                                    }.take(24)
                            }
                        }
                        val cards = (items.filterIsInstance<SongItem>().take(16) +
                            items.filterIsInstance<AlbumItem>().take(4) +
                            items.filterIsInstance<ArtistItem>().take(2)).distinctBy { it.id }.take(22)
                        if (cards.isEmpty()) null else SimilarRecommendation(title = artist, items = cards)
                    }
                }
            }
        }
        val songJobs = songAnchors.map { song ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    withTimeoutOrNull(5_000) {
                        val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                            ?: return@withTimeoutOrNull null
                        val page = YouTube.related(endpoint).getOrNull() ?: return@withTimeoutOrNull null
                        val seedArtistIds = song.artists.map { it.id }.toSet()
                        val cards = (
                            page.songs.filter { c -> c.artists.none { it.id in seedArtistIds } }.take(16) +
                                page.albums.filter { a -> a.artists.orEmpty().none { it.id in seedArtistIds } }.take(4) +
                                page.artists.filter { it.id !in seedArtistIds }.take(2)
                            ).distinctBy { it.id }
                        if (cards.isEmpty()) null else SimilarRecommendation(title = song, items = cards)
                    }
                }
            }
        }
        (artistJobs + songJobs).mapNotNull { it.await() }
    }

    /** Pull-to-refresh: rebuilds every row (SMusic, Pulse and YouTube). The spinner stops at the first paint. */
    fun refresh() {
        if (isRefreshing.value) return
        refreshJob?.cancel()
        refreshCycle += 1
        isRefreshing.value = true
        val epoch = sessionEpoch
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Hard ceiling so nothing can hang; slow parts simply keep their old rows.
                withTimeoutOrNull(20_000) {
                    loadAll(progressive = false, onFirstPaint = { if (epoch == sessionEpoch) isRefreshing.value = false })
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                reportException(error)
            } finally {
                if (epoch == sessionEpoch) isRefreshing.value = false
            }
        }
    }

    /** Clears everything that belongs to the previous account/session so it can never be shown for the next one. */
    private fun dropSessionContent() {
        homePage.value = null
        youtubeQuickPicks.value = emptyList()
        accountPlaylists.value = emptyList()
        pulseDiscover.value = emptyList()
        similarRecommendations.value = emptyList()
        pulseYtShelves.value = emptyList()
        pulseShelves.value = emptyList()
        allYtItems.value = emptyList()
        isLoading.value = true
    }

    init {
        // Rebuild Home when the account changes. Local rows stay on screen; only account-derived
        // content is dropped, and any request still running for the old session is invalidated.
        viewModelScope.launch(Dispatchers.IO) {
            attempt { com.devxrohan.smusic.utils.migrateLegacyGuestSnapshot(context, database) }
            context.dataStore.data
                .map { prefs -> prefs[InnerTubeCookieKey].orEmpty() }
                .distinctUntilChanged()
                .collect { cookie ->
                    val signedIn = runCatching {
                        "SAPISID" in com.devxrohan.smusic.innertube.utils.parseCookieString(cookie)
                    }.getOrDefault(false)
                    sessionEpoch += 1
                    accountLoadJob?.cancel()
                    loadJob?.cancel()
                    refreshJob?.cancel()
                    isRefreshing.value = false
                    YouTube.cookie = cookie.takeIf { signedIn }
                    dropSessionContent()
                    accountLoadJob = launch(Dispatchers.IO) {
                        loadAll(progressive = true)
                        // Library sync runs after first paint; it never reshuffles Home a second time.
                        if (signedIn) {
                            attempt { syncUtils.syncAll() }
                            // Let Pulse learn from the just-synced likes/subscriptions so the account does not start cold.
                            attempt {
                                pulseEngine.syncUserSignals(
                                    downloadUtil.downloads.value.filterValues { it.state == Download.STATE_COMPLETED }.keys
                                )
                            }
                        }
                    }
                }
        }
    }
}
