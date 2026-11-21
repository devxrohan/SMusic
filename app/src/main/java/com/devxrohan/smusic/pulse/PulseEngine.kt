package com.devxrohan.smusic.pulse

import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get
import com.devxrohan.smusic.constants.AccountModeKey
import com.devxrohan.smusic.constants.InnerTubeCookieKey
import com.devxrohan.smusic.innertube.utils.parseCookieString

import android.content.Context
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.db.entities.Artist
import com.devxrohan.smusic.db.entities.LocalItem
import com.devxrohan.smusic.db.entities.Song
import com.devxrohan.smusic.innertube.models.AlbumItem
import com.devxrohan.smusic.innertube.models.ArtistItem
import com.devxrohan.smusic.innertube.models.PlaylistItem
import com.devxrohan.smusic.innertube.models.SongItem
import com.devxrohan.smusic.innertube.models.YTItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** A Pulse-planned Home shelf of existing SMusic entity types (rendered with existing grid items). */
data class PulseShelf(
    val id: String,
    val title: String,
    val items: List<LocalItem>,
    /** Short, user-facing reason shown under the shelf title. */
    val reason: String = "",
)

data class PulseArtistInsight(val id: String, val name: String, val score: Double, val thumbnail: String? = null)

data class PulseSummary(
    val stage: PulseStage,
    val totalPlays: Int,
    val discoveryAppetite: Double,
    val topArtists: List<PulseArtistInsight>,
    val rising: List<PulseArtistInsight>,
    /** Data-driven taste groups (artist names), biggest first. */
    val tasteGroups: List<List<String>> = emptyList(),
    /** 0 = recent taste matches long-term taste, 1 = totally different lately. */
    val drift: Double = 0.0,
    val confidence: Double = 0.0,
    val personality: String = "",
    /** Human-readable signals that explain what Pulse currently believes. */
    val reasonHighlights: List<String> = emptyList(),
    /** Stable smart-mix names generated from actual listening evidence. */
    val smartMixes: List<String> = emptyList(),
    val discoveryMode: String = "AUTO",
)

/** Learns from playback and history, persists taste signals and ranks supported recommendation rows. */
@Singleton
class PulseEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    val revision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val tuningRevision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    private val mutex = Mutex()
    private var state: PulseState? = null
    private val file get() = PulseStorage.file(context.filesDir)

    /** Pulse always LEARNS (one profile, guest or account). What it is allowed to reorder depends on [accountPrimary]. */
    @Suppress("RedundantSuspendModifier")
    private suspend fun guestModeActive(): Boolean = true

    /** Preserve connected-account feed and radio order; Pulse ranks only its own shelves. */
    suspend fun accountPrimary(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val cookie = context.dataStore.data.first()[com.devxrohan.smusic.constants.InnerTubeCookieKey].orEmpty()
            cookie.isNotBlank() && "SAPISID" in com.devxrohan.smusic.innertube.utils.parseCookieString(cookie)
        }.getOrDefault(false)
    }

    private suspend fun ensureLoaded(): PulseState {
        state?.let { return it }
        val loaded = withContext(Dispatchers.IO) { PulseStorage.load(file) }
        state = loaded
        // Reconcile durable user intent every time Pulse is first loaded: likes/subscriptions live
        // in Room and completed downloads live in Media3, so Pulse should never depend on a stale cache.
        withContext(Dispatchers.IO) { syncPersistentSignals(loaded) }
        persist(loaded)
        return loaded
    }

    private fun persist(s: PulseState) {
        PulseStorage.save(file, s)
        revision.value = revision.value + 1
    }

    suspend fun setDiscoveryMode(mode: String) = mutex.withLock {
        val preference = when (mode) { "AUTO" -> null; "FAMILIAR" -> 0.15; "BALANCED" -> 0.35; "ADVENTUROUS" -> 0.60; else -> error("Unknown discovery mode") }
        val loaded = ensureLoaded()
        val previous = loaded.discoveryPreference
        loaded.discoveryPreference = preference
        try { withContext(Dispatchers.IO) { persist(loaded) } }
        catch (failure: Exception) { loaded.discoveryPreference = previous; throw failure }
        tuningRevision.value = tuningRevision.value + 1
    }

    /** Whether queue-level Pulse Similar Content expansion is enabled by the user. */
    fun similarContentEnabled(): Boolean = context.dataStore.get(com.devxrohan.smusic.constants.SimilarContent, true)


    private suspend fun syncPersistentSignals(s: PulseState) {
        val subscribedIds = database.artistsBookmarkedByNameAsc().first().map { it.id }.toSet()
        s.subscribedArtists.retainAll(subscribedIds)
        s.subscribedArtists.addAll(subscribedIds)
        database.artistsBookmarkedByNameAsc().first().forEach { artist ->
            val a = s.artists.getOrPut(artist.id) { ArtistAffinity() }
            if (artist.artist.name.isNotBlank()) a.name = artist.artist.name
            a.liked = true
            a.score = maxOf(a.score, PulseParams.LIKE_SCORE_FLOOR)
        }
        // Do not scan remote-only data here; only local Room likes are explicit user intent.
        database.allSongs().first().filter { it.song.liked }.forEach { song ->
            val st = s.songs.getOrPut(song.id) { SongStats() }
            st.title = song.title
            st.artistIds = song.artists.map { it.id }
            st.liked = true
            song.artists.forEach { artist ->
                val a = s.artists.getOrPut(artist.id) { ArtistAffinity() }
                a.name = artist.name
                a.liked = true
                a.score = maxOf(a.score, PulseParams.LIKE_SCORE_FLOOR)
            }
        }
    }

    /** Reconcile durable likes/subscriptions and the current completed-download set. */
    suspend fun syncUserSignals(downloadedSongIds: Set<String>) = safe(Unit) {
        if (!guestModeActive()) return@safe
        mutex.withLock {
            val s = ensureLoaded()
            syncPersistentSignals(s)
            s.downloadedSongs.retainAll(downloadedSongIds)
            s.downloadedSongs.addAll(downloadedSongIds)
            withContext(Dispatchers.IO) { persist(s) }
        }
    }

    // ---------- Backfill ----------

    /** Idempotent + versioned: learns from existing history once, never repeats unless BACKFILL_VERSION is bumped. */
    suspend fun backfillIfNeeded() = safe(Unit) {
        if (!guestModeActive()) return@safe
        mutex.withLock {
            val s = ensureLoaded()
            if (s.backfillVersion >= PulseParams.BACKFILL_VERSION) return@withLock
            val rebuilt = PulseState().also { fresh ->
                fresh.blockedArtists.addAll(s.blockedArtists)
                fresh.dislikedArtists.putAll(s.dislikedArtists)
                fresh.smartPlaylistIds.addAll(s.smartPlaylistIds)
                fresh.dismissedPlaylists.addAll(s.dismissedPlaylists)           // explicit user choices survive a rebuild
                fresh.notForMeSongs.putAll(s.notForMeSongs)
                fresh.positiveFeedback.putAll(s.positiveFeedback)
                s.positiveFeedback.values.flatMap { it.artistIds }.distinct().forEach { id ->
                    s.artists[id]?.let { fresh.artists[id] = ArtistAffinity(name = it.name) }
                }
                fresh.discoveryPreference = s.discoveryPreference
                fresh.discoveryAppetite = s.discoveryAppetite
            }
            val events = withContext(Dispatchers.IO) { database.events().first() }
                .take(PulseParams.BACKFILL_MAX_ROWS)
                .reversed() // oldest -> newest
            for (e in events) {
                val song = e.song
                val dur = song.song.duration * 1000L
                val pct = if (dur > 0) (e.event.playTime.toDouble() / dur).coerceAtMost(1.0) else 0.6
                PulseLearn.applySignal(rebuilt, signalOf(song, pct, toMs(e.event.timestamp)))
            }
            // Historical plays must not clear a later explicit Not for me choice.
            rebuilt.notForMeSongs.putAll(s.notForMeSongs)
            rebuilt.positiveFeedback.putAll(s.positiveFeedback)
            rebuilt.backfillVersion = PulseParams.BACKFILL_VERSION
            state = rebuilt
            withContext(Dispatchers.IO) { persist(rebuilt) }
        }
    }

    private fun toMs(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun signalOf(song: Song, pct: Double, ts: Long) = PulseSignal(
        songId = song.id,
        title = song.title,
        artistIds = song.artists.map { it.id },
        artistNames = song.artists.map { it.name },
        percentPlayed = pct,
        timestamp = ts,
        liked = song.song.liked,
        albumId = song.album?.id,
    )

    // ---------- Live learning ----------

    /** Called from the player's playback-stats callback. Early skips are learned too, not just counted plays. */
    suspend fun onPlaybackFinished(songId: String, playTimeMs: Long) = safe(Unit) {
        if (!guestModeActive()) return@safe
        if (playTimeMs < 2000L || context.dataStore.data.first()[com.devxrohan.smusic.constants.PauseListenHistoryKey] == true) return@safe
        val song = withContext(Dispatchers.IO) { database.song(songId).first() } ?: return@safe
        mutex.withLock {
            val s = ensureLoaded()
            val dur = song.song.duration * 1000L
            val pct = if (dur > 0) (playTimeMs.toDouble() / dur).coerceAtMost(1.0)
            else if (playTimeMs >= 30_000L) 0.6 else 0.1
            PulseLearn.applySignal(s, signalOf(song, pct, System.currentTimeMillis()))
            withContext(Dispatchers.IO) { persist(s) }
        }
    }

    // ---------- Feedback ----------

    suspend fun onArtistSubscription(artistId: String, subscribed: Boolean) = safe(Unit) {
        if (!guestModeActive()) return@safe
        mutex.withLock {
            val s = ensureLoaded()
            if (subscribed) s.subscribedArtists.add(artistId) else s.subscribedArtists.remove(artistId)
            s.artists.getOrPut(artistId) { ArtistAffinity() }.apply {
                liked = subscribed
                if (subscribed) score = maxOf(score, PulseParams.LIKE_SCORE_FLOOR)
            }
            withContext(Dispatchers.IO) { persist(s) }
        }
    }

    suspend fun onDownloadState(songId: String, downloaded: Boolean) = safe(Unit) {
        if (!guestModeActive()) return@safe
        mutex.withLock {
            val s = ensureLoaded()
            if (downloaded) s.downloadedSongs.add(songId) else s.downloadedSongs.remove(songId)
            withContext(Dispatchers.IO) { persist(s) }
        }
    }

    suspend fun onFavorite(song: Song, liked: Boolean) = safe(Unit) {
        if (!guestModeActive()) return@safe
        mutex.withLock {
            val s = ensureLoaded()
            PulseLearn.applyFavorite(s, song.id, song.artists.map { it.id }, liked)
            withContext(Dispatchers.IO) { persist(s) }
        }
    }

    suspend fun onTasteFeedback(tracks: List<PulseFeedbackTrack>, positive: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            safe(false) {
                val valid = tracks.filter { it.id.isNotBlank() }.distinctBy { it.id }
                if (valid.isEmpty()) return@safe false
                mutex.withLock {
                    val updated = PulseStorage.fromJson(PulseStorage.toJson(ensureLoaded()))
                    val now = System.currentTimeMillis()
                    valid.forEach { track ->
                        if (positive) PulsePreferences.apply(updated, track, now)
                        else PulseLearn.applyNotForMe(updated, track.id, track.artistIds.filter(String::isNotBlank), now)
                    }
                    persist(updated)
                    state = updated
                    true
                }
            }
        }

    suspend fun onNotForMe(song: Song) = safe(Unit) {
        if (!guestModeActive()) return@safe
        mutex.withLock {
            val s = ensureLoaded()
            PulseLearn.applyNotForMe(s, song.id, song.artists.map { it.id }, System.currentTimeMillis())
            withContext(Dispatchers.IO) { persist(s) }
        }
    }

    suspend fun onNotForMe(songId: String, artistIds: List<String>) = safe(Unit) {
        if (!guestModeActive()) return@safe
        mutex.withLock {
            val s = ensureLoaded()
            PulseLearn.applyNotForMe(s, songId, artistIds, System.currentTimeMillis())
            withContext(Dispatchers.IO) { persist(s) }
        }
    }

    suspend fun setArtistBlocked(artistId: String, blocked: Boolean) = safe(Unit) {
        if (!guestModeActive()) return@safe
        mutex.withLock {
            val s = ensureLoaded()
            if (blocked) PulseLearn.blockArtist(s, artistId) else PulseLearn.unblockArtist(s, artistId)
            withContext(Dispatchers.IO) { persist(s) }
        }
    }

    // ---------- Introspection ----------

    suspend fun summary(): PulseSummary? = safe(null) {
        if (!guestModeActive()) return@safe null
        mutex.withLock {
            val s = ensureLoaded()
            suspend fun insight(id: String, score: Double): PulseArtistInsight {
                val artist = withContext(Dispatchers.IO) { database.artist(id).first() }
                return PulseArtistInsight(id, s.artists[id]?.name.orEmpty().ifBlank { artist?.artist?.name ?: id }, score, artist?.artist?.thumbnailUrl)
            }
            PulseSummary(
                stage = s.stage(),
                totalPlays = s.totalPlays,
                discoveryAppetite = s.recommendationAppetite(),
                discoveryMode = when (s.discoveryPreference) { null -> "AUTO"; 0.15 -> "FAMILIAR"; 0.35 -> "BALANCED"; else -> "ADVENTUROUS" },
                topArtists = s.topArtists(8).filter { s.artists[it.first]?.name?.isNotBlank() == true && !s.isHidden(it.first, System.currentTimeMillis()) }
                    .map { insight(it.first, it.second) },
                tasteGroups = PulseClusters.compute(s).let { tm ->
                    tm.mass.entries.sortedByDescending { it.value }.take(4).map { (lab, _) ->
                        tm.members[lab].orEmpty().mapNotNull { id -> s.artists[id]?.name?.takeIf { it.isNotBlank() } }.take(4)
                    }.filter { it.size >= 2 }
                },
                drift = PulseRanker.drift(s),
                confidence = s.confidence(),
                personality = personalityFor(s),
                reasonHighlights = reasonHighlights(s, now = System.currentTimeMillis()),
                smartMixes = smartMixNames(s),
                rising = s.recentRotation.entries.sortedByDescending { it.value }
                    .filter { s.artists[it.key]?.name?.isNotBlank() == true && !s.isHidden(it.key, System.currentTimeMillis()) }
                    .take(5).map { insight(it.key, it.value) },
            )
        }
    }

    private fun reasonHighlights(s: PulseState, now: Long): List<String> {
        val out = ArrayList<String>(4)
        val requested = PulsePreferences.directArtists(s, now)
        s.topArtists(3, now).forEach { (id, _) ->
            val name = s.artists[id]?.name?.takeIf(String::isNotBlank) ?: return@forEach
            out += if (id in requested) "You asked for more music like $name"
                else "Built around $name and artists you repeatedly return to"
        }
        val bucket = PulseTimeBucket.fromTimestamp(now)
        val contextHits = s.timeBuckets[bucket]?.entries?.sortedByDescending { it.value }?.take(1)?.mapNotNull {
            s.artists[it.key]?.name?.takeIf { n -> n.isNotBlank() }
        }.orEmpty()
        contextHits.forEach { out += "Your current time-of-day taste leans toward $it" }
        if (s.recommendationAppetite() >= 0.58) out += "Pulse is opening more room for new artists because you accept discovery"
        else if (s.recommendationAppetite() <= 0.22) out += "Pulse is keeping discovery conservative because familiar music performs better for you"
        if (PulseRanker.drift(s) >= 0.45) out += "Your recent listening has shifted, so recent taste gets extra weight"
        return out.distinct().take(4)
    }

    private fun smartMixNames(s: PulseState): List<String> = buildList {
        if (s.stage() >= PulseStage.LEARNING) add("Pulse Favorites")
        if (s.stage() >= PulseStage.LEARNING && s.discoveryAppetite >= 0.35) add("Discovery Mix")
        if (s.stage() >= PulseStage.PERSONALIZED) add("Deep Cuts")
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (hour >= 22 || hour < 5) add("Late Night Pulse")
        if (PulseRanker.drift(s) >= 0.45) add("What's New In Your Taste")
    }.distinct().take(5)

    private fun personalityFor(s: PulseState): String {
        val appetite = s.discoveryAppetite
        val drift = PulseRanker.drift(s)
        return when {
            appetite >= 0.62 && drift >= 0.45 -> "Curious Explorer"
            appetite >= 0.62 -> "Discovery Seeker"
            appetite <= 0.22 && drift < 0.30 -> "Comfort Listener"
            drift >= 0.45 -> "Mood Shifter"
            else -> "Balanced Listener"
        }
    }

    suspend fun stage(): PulseStage = safe(PulseStage.COLD_START) { mutex.withLock { ensureLoaded().stage() } }

    // ---------- Home integration ----------

    /** Fill the stage-dependent Keep Listening share while preserving the original cold-start content. */
    suspend fun blendKeepListening(
        original: List<LocalItem>,
        songPool: List<Song>,
        artistPool: List<Artist>,
        albumPool: List<com.devxrohan.smusic.db.entities.Album> = emptyList(),
        usedIds: MutableSet<String>,
        size: Int = 20,
    ): List<LocalItem> = safe(original) {
        if (!guestModeActive()) return@safe original
        mutex.withLock {
            val s = ensureLoaded()
            val share = s.effectiveShare()
            val now = System.currentTimeMillis()
            if (share <= 0.0) return@withLock original.filterNot { isRejected(s, it, now) }
            val pool: List<LocalItem> = songPool.distinctBy { it.id } +
                artistPool.distinctBy { it.id } +
                albumPool.distinctBy { it.id }
            val cands = pool.map { item ->
                PulseCandidate(
                    item.id,
                    when (item) {
                        is Song -> PulseKind.SONG
                        is Artist -> PulseKind.ARTIST
                        is com.devxrohan.smusic.db.entities.Album -> PulseKind.ALBUM
                        else -> PulseKind.SONG
                    },
                    artistIdsOf(item)
                )
            }
            val rankedIdx = PulseRanker.rank(s, cands, PulseSurface.KEEP_LISTENING, now)
            val ranked = rankedIdx.map { pool[it] }.filter { it.id !in usedIds }
            val wanted = (size * share).toInt()
            // Keep the original mixed-content behavior: reserve room for Artists and Albums
            // when Pulse has enough quality candidates, while allowing Songs to fill the rest.
            val rankedArtists = ranked.filterIsInstance<Artist>()
            val rankedAlbums = ranked.filterIsInstance<com.devxrohan.smusic.db.entities.Album>()
            val rankedSongs = ranked.filterIsInstance<Song>()
            val artistTarget = minOf(rankedArtists.size, if (rankedArtists.isEmpty()) 0 else maxOf(1, wanted / 5))
            val albumTarget = minOf(rankedAlbums.size, if (rankedAlbums.isEmpty()) 0 else maxOf(1, wanted / 5))
            val chosenArtists = rankedArtists.take(artistTarget)
            val chosenAlbums = rankedAlbums.take(albumTarget)
            val songTarget = maxOf(0, wanted - chosenArtists.size - chosenAlbums.size)
            val chosenSongs = rankedSongs.take(songTarget)
            val chosen = (chosenArtists + chosenAlbums + chosenSongs).toSet()
            val pulsePart: List<LocalItem> = ranked.filter { it in chosen }
            if (pulsePart.size < PulseParams.MIN_SHELF_ITEMS) return@withLock original // not enough quality: fall back
            val ids = pulsePart.map { it.id }.toSet()
            // Original items stay, except ones Pulse knows the user rejected (blocked / cooled-down artists, Not For Me).
            val rest = original.filter { it.id !in ids && !isRejected(s, it, now) }.take(size - pulsePart.size)
            val merged = interleave(pulsePart, rest)
            usedIds.addAll(merged.map { it.id })
            merged
        }
    }

    /** Pulse-chosen anchors for "Similar To" (replaces purely random picks as stage grows). */
    suspend fun similarAnchors(candidates: List<Artist>, max: Int): List<Artist> = safe(candidates.take(max)) {
        if (!guestModeActive()) return@safe candidates.take(max)
        mutex.withLock {
            val s = ensureLoaded()
            val share = s.effectiveShare()
            val now = System.currentTimeMillis()
            if (share <= 0.0 || candidates.isEmpty()) return@withLock candidates.filterNot { s.isHidden(it.id, now) }.take(max)
            val cands = candidates.map { PulseCandidate(it.id, PulseKind.ARTIST, listOf(it.id)) }
            val ranked = PulseRanker.rank(s, cands, PulseSurface.KEEP_LISTENING, now).map { candidates[it] }
            ranked.take(max)
        }
    }

    /** Rank mixed Similar To results with novelty targets, removing blocked artists and duplicate items. */
    suspend fun rankSimilar(items: List<YTItem>, usedIds: MutableSet<String>, limit: Int = 16): List<YTItem> = safe(items) {
        if (!guestModeActive()) return@safe items
        mutex.withLock {
            val s = ensureLoaded()
            val now = System.currentTimeMillis()
            if (s.effectiveShare() <= 0.0) return@withLock items.filter { item -> artistIdsOf(item).none { s.isHidden(it, now) } && s.notForMeSongs[item.id]?.let { now - it < PulseParams.NOT_FOR_ME_MS } != true }
            val fresh = items.filter { it.id !in usedIds }.filter(::looksLikeMusic).distinctBy { it.id }
            val cands = fresh.map { PulseCandidate(it.id, kindOf(it), artistIdsOf(it)) }
            val relevance = PulseRanker.relevanceScores(s, cands, PulseSurface.SIMILAR, now)
            val relevant = cands.withIndex().filter { relevance[it.index] >= PulseParams.MIN_RELEVANCE }
            val relevantItems = relevant.map { fresh[it.index] }
            val relevantCands = relevant.map { it.value }
            val out = PulseRanker.rank(s, relevantCands, PulseSurface.SIMILAR, now)
                .map { relevantItems[it] }.take(limit)
            usedIds.addAll(out.map { it.id })
            if (out.size < PulseParams.MIN_SHELF_ITEMS) {
                val fallback = items.distinctBy { it.id }.filter { item -> artistIdsOf(item).none { s.isHidden(it, now) } && s.notForMeSongs[item.id]?.let { now - it < PulseParams.NOT_FOR_ME_MS } != true }
                PulseRanker.rank(s, fallback.map { PulseCandidate(it.id, kindOf(it), artistIdsOf(it)) }, PulseSurface.SIMILAR, now).map { fallback[it] }.take(limit)
            } else out
        }
    }

    /** Rank previously fetched candidates for Picked for You; omit the shelf during cold start. */
    suspend fun pickedForYou(pool: List<YTItem>, usedIds: MutableSet<String>, limit: Int = 12): List<YTItem> = safe(emptyList()) {
        mutex.withLock {
            val s = ensureLoaded()
            if (s.stage() < PulseStage.EARLY || s.effectiveShare() <= 0.0) return@withLock emptyList()
            val now = System.currentTimeMillis()
            val fresh = pool.asSequence()
                .filter { it.id !in usedIds && it !is PlaylistItem }
                .filter(::looksLikeMusic).distinctBy { it.id }
                .filter { item -> artistIdsOf(item).none { s.isHidden(it, now) } }.toList()
            val cands = fresh.map { PulseCandidate(it.id, kindOf(it), artistIdsOf(it)) }
            val relevance = PulseRanker.relevanceScores(s, cands, PulseSurface.SHELF, now)
            val relevant = cands.withIndex().filter { relevance[it.index] >= PulseParams.MIN_RELEVANCE }
            val items = relevant.map { fresh[it.index] }
            if (items.size < PulseParams.MIN_SHELF_ITEMS) return@withLock emptyList()
            val out = PulseRanker.rank(s, relevant.map { it.value }, PulseSurface.SHELF, now).map { items[it] }.take(limit)
            if (out.size < PulseParams.MIN_SHELF_ITEMS) emptyList() else out.also { usedIds.addAll(it.map { x -> x.id }) }
        }
    }

    /** Additive Pulse shelves. A shelf is returned only if it has enough good, de-duplicated items. */
    suspend fun planShelves(
        songPool: List<Song>,
        usedIds: MutableSet<String>,
        artistPool: List<Artist> = emptyList(),
        albumPool: List<com.devxrohan.smusic.db.entities.Album> = emptyList(),
    ): List<PulseShelf> = safe(emptyList()) {
        if (!guestModeActive()) return@safe emptyList()
        mutex.withLock {
            val s = ensureLoaded()
            val stage = s.stage()
            if (stage == PulseStage.COLD_START) return@withLock emptyList()
            val now = System.currentTimeMillis()
            val pool = songPool.distinctBy { it.id }.filter { song -> song.artists.none { s.isHidden(it.id, now) } }
            val shelves = ArrayList<PulseShelf>()

            fun shelfReason(shelfId: String): String = when {
                shelfId == "on_repeat" -> "Songs you've been coming back to"
                shelfId == "rediscover" -> "Favorites that deserve another spin"
                shelfId == "made_for_you" -> "Ranked from your long-term taste"
                shelfId == "deep_cuts" -> "Less-played tracks from artists you already love"
                shelfId == "your_mix" -> "A balanced mix of familiar and fresh"
                shelfId == "right_now" -> "Matched to what you usually play around now"
                shelfId.startsWith("because_") -> "Connected to your recent listening"
                shelfId == "adjacent_picks" -> "Artists close to your listening graph"
                shelfId == "fresh_discovery" -> "New artists that still fit your taste"
                shelfId == "artists_like" -> "Artists Pulse thinks you'll enjoy"
                shelfId == "albums_for_you" -> "Albums selected from your taste profile"
                else -> "Picked by Pulse"
            }

            fun addShelf(shelfId: String, title: String, ordered: List<Song>, n: Int = PulseParams.HOME_SHELF_SIZE) {
                val items = ordered.filter { it.id !in usedIds }.distinctBy { it.id }.take(n)
                if (items.size >= PulseParams.MIN_SHELF_ITEMS) {
                    usedIds.addAll(items.map { it.id })
                    shelves += PulseShelf(shelfId, title, items, shelfReason(shelfId))
                }
            }
            fun ranked(songs: List<Song>, surface: PulseSurface) =
                PulseRanker.rank(s, songs.map { PulseCandidate(it.id, PulseKind.SONG, it.artists.map { a -> a.id }) }, surface, now)
                    .map { songs[it] }

            // On Repeat: songs genuinely replayed recently
            addShelf("on_repeat", context.getString(com.devxrohan.smusic.R.string.pulse_shelf_on_repeat),
                pool.filter { (s.songs[it.id]?.plays ?: 0) >= 3 && (now - (s.songs[it.id]?.lastPlayed ?: 0L)) < 14L * 86_400_000 }
                    .sortedByDescending { PulseRanker.activation(s, it.id, now) })

            if (stage >= PulseStage.EARLY) {
                // Rediscover: loved but not played in 3+ weeks
                addShelf("rediscover", context.getString(com.devxrohan.smusic.R.string.pulse_shelf_rediscover),
                    pool.filter {
                        val st = s.songs[it.id]
                        st != null && st.plays >= 3 && now - st.lastPlayed > 21L * 86_400_000
                    }.let { ranked(it, PulseSurface.SHELF) })

                addShelf("made_for_you", context.getString(com.devxrohan.smusic.R.string.pulse_shelf_made_for_you), ranked(pool, PulseSurface.SHELF))
            }
            if (stage >= PulseStage.PERSONALIZED) {
                // Deep cuts: less-played songs by top artists
                val top = s.topArtists(8).map { it.first }.toSet()
                addShelf("deep_cuts", context.getString(com.devxrohan.smusic.R.string.pulse_shelf_deep_cuts),
                    pool.filter { song -> song.artists.any { it.id in top } && (s.songs[song.id]?.plays ?: 0) in 0..2 }
                        .let { ranked(it, PulseSurface.SHELF) })
            }
            // Your Mix: songs closest to your sound-space right now (session + recent + long-term), across artists
            if (stage >= PulseStage.PERSONALIZED) {
                addShelf("your_mix", context.getString(com.devxrohan.smusic.R.string.pulse_shelf_your_mix),
                    ranked(pool.filter { (s.songs[it.id]?.plays ?: 0) in 0..6 }, PulseSurface.RADIO), PulseParams.HOME_SHELF_SIZE)
            }
            // For You Right Now: artists you actually play in this time-of-day bucket (needs >= 20 plays there)
            s.timeBuckets[PulseTimeBucket.fromTimestamp(now)]?.takeIf { it.values.sum() >= 20.0 }?.let { hist ->
                addShelf("right_now", context.getString(com.devxrohan.smusic.R.string.pulse_shelf_right_now),
                    pool.filter { song -> song.artists.any { (hist[it.id] ?: 0.0) >= 2.0 } }
                        .let { ranked(it, PulseSurface.SHELF) }, PulseParams.HOME_SHELF_SIZE)
            }
            // Because You Played [top recent artist]
            s.recentRotation.entries.maxByOrNull { it.value }?.key?.let { artistId ->
                val name = s.artists[artistId]?.name?.takeIf { it.isNotBlank() }
                if (name != null && stage >= PulseStage.EARLY) {
                    addShelf("because_$artistId", context.getString(com.devxrohan.smusic.R.string.pulse_shelf_because, name),
                        pool.filter { song -> song.artists.none { it.id == artistId } && song.artists.any { a ->
                            s.artistCooc[pairKey(a.id, artistId)] != null
                        } }.let { ranked(it, PulseSurface.SHELF) }, PulseParams.HOME_SHELF_SIZE)
                }
            }
            // Adjacent Picks: artists strongly connected to the user's own listening graph.
            if (stage >= PulseStage.LEARNING) {
                val anchors = s.topArtists(PulseParams.COOC_ANCHORS).map { it.first }.toSet()
                addShelf("adjacent_picks", "Your Next Picks",
                    pool.filter { song -> song.artists.any { a ->
                        a.id !in anchors && anchors.any { x ->
                            (s.artistCooc[pairKey(a.id, x)] ?: 0.0) >= 1.0 ||
                                (s.relatedEdges[pairKey(a.id, x)] ?: 0.0) >= 0.4
                        }
                    } }.let { ranked(it, PulseSurface.DISCOVER) })
            }
            // Fresh discovery from the local library: unfamiliar artists that still sit inside the learned taste space.
            if (stage >= PulseStage.LEARNING) {
                addShelf("fresh_discovery", "New For Your Taste",
                    pool.filter { song -> song.artists.isNotEmpty() && song.artists.all { it.id !in s.seenArtists } }
                        .let { ranked(it, PulseSurface.DISCOVER) })
            }

            // Artists You May Like: known artists with real affinity but not in heavy rotation right now
            if (stage >= PulseStage.LEARNING) {
                val artistCands = artistPool.distinctBy { it.id }.filter { !s.isHidden(it.id, now) && it.id !in usedIds }
                val rankedArtists = PulseRanker.rank(
                    s, artistCands.map { PulseCandidate(it.id, PulseKind.ARTIST, listOf(it.id)) },
                    PulseSurface.DISCOVER, now
                ).map { artistCands[it] }.take(PulseParams.HOME_SHELF_SIZE)
                if (rankedArtists.size >= PulseParams.MIN_SHELF_ITEMS) {
                    usedIds.addAll(rankedArtists.map { it.id })
                    shelves += PulseShelf("artists_like", context.getString(com.devxrohan.smusic.R.string.pulse_shelf_artists), rankedArtists, shelfReason("artists_like"))
                }
            }
            // Albums For You: only with enough confidence (personalized+)
            if (stage >= PulseStage.PERSONALIZED) {
                val albumCands = albumPool.distinctBy { it.id }
                    .filter { a -> a.artists.none { s.isHidden(it.id, now) } && a.id !in usedIds }
                val rankedAlbums = PulseRanker.rank(
                    s, albumCands.map { PulseCandidate(it.id, PulseKind.ALBUM, it.artists.map { x -> x.id }) },
                    PulseSurface.SHELF, now
                ).map { albumCands[it] }.take(PulseParams.HOME_SHELF_SIZE)
                if (rankedAlbums.size >= PulseParams.MIN_SHELF_ITEMS) {
                    usedIds.addAll(rankedAlbums.map { it.id })
                    shelves += PulseShelf("albums_for_you", context.getString(com.devxrohan.smusic.R.string.pulse_shelf_albums), rankedAlbums, shelfReason("albums_for_you"))
                }
            }
            shelves
        }
    }

    // ---------- Smart Radio / AutoMix ----------

    /** Reorder radio candidates without moving the seed; only blocked artists are removed. */
    suspend fun <T> rankForRadio(
        items: List<T>,
        idOf: (T) -> String,
        artistIdsOf: (T) -> List<String>,
        seedId: String? = null,
        seedArtistIds: List<String> = emptyList(),
    ): List<T> = safe(items) {
        if (!guestModeActive()) return@safe items
        // Account connected: the queue/AutoMix stays exactly as YouTube's account radio returned it.
        if (accountPrimary()) return@safe items
        mutex.withLock {
            val s = ensureLoaded()
            val now = System.currentTimeMillis()
            if (s.effectiveShare() <= 0.0 || items.size < 3) return@withLock items.filter { item -> idOf(item) == seedId || (artistIdsOf(item).none { s.isHidden(it, now) } && s.notForMeSongs[idOf(item)]?.let { now - it < PulseParams.NOT_FOR_ME_MS } != true) }
            val body = items.filter { idOf(it) != seedId }.distinctBy { idOf(it) }
            val cands = body.map { PulseCandidate(idOf(it), PulseKind.SONG, artistIdsOf(it)) }
            val scored = PulseRanker.relevanceScores(s, cands, PulseSurface.RADIO, now, seedArtistIds).mapIndexed { index, value -> index to value }
            val relevantIndexes = scored.filter { it.second >= PulseParams.MIN_RELEVANCE }.map { it.first }.toSet()
            val filteredBody = if (relevantIndexes.size >= 5) body.filterIndexed { i, _ -> i in relevantIndexes } else body
            val filteredCands = filteredBody.map { PulseCandidate(idOf(it), PulseKind.SONG, artistIdsOf(it)) }
            val ranked = PulseRanker.rank(s, filteredCands, PulseSurface.RADIO, now, lastSongId = seedId, seedArtistIds = seedArtistIds).map { filteredBody[it] }
            val head = items.filter { idOf(it) == seedId }.take(1)
            val out = head + ranked
            if (out.size < 2) items else out
        }
    }

    /** Familiar favorites to weave into a radio (current favorite -> related -> familiar -> discovery rhythm). */
    suspend fun familiarFavorites(songPool: List<Song>, excludeIds: Set<String>, n: Int): List<Song> = safe(emptyList()) {
        if (!guestModeActive()) return@safe emptyList()
        mutex.withLock {
            val s = ensureLoaded()
            if (s.effectiveShare() <= 0.0) return@withLock emptyList()
            val now = System.currentTimeMillis()
            val pool = songPool.filter { it.id !in excludeIds && it.artists.none { a -> s.isHidden(a.id, now) } }
            PulseRanker.rank(s, pool.map { PulseCandidate(it.id, PulseKind.SONG, it.artists.map { a -> a.id }) },
                PulseSurface.KEEP_LISTENING, now).map { pool[it] }.take(n)
        }
    }

    // ---------- Smart playlists ----------

    private var lastPlaylistSync = 0L

    /** Refresh local-only Pulse playlists when sufficient listening and time-bucket evidence exists. */
    suspend fun syncSmartPlaylists(songPool: List<Song>) = safe(Unit) {
        if (!guestModeActive()) return@safe
        val plan: List<Triple<String, String, List<Song>>> = mutex.withLock {
            val s = ensureLoaded()
            val now = System.currentTimeMillis()
            if (s.stage() < PulseStage.LEARNING || now - lastPlaylistSync < 6L * 3_600_000) return@withLock emptyList()
            lastPlaylistSync = now
            val pool = songPool.distinctBy { it.id }.filter { it.artists.none { a -> s.isHidden(a.id, now) } }
            fun byIds(f: (SongStats) -> Boolean, order: Comparator<Song>? = null): List<Song> =
                pool.filter { s.songs[it.id]?.let(f) == true }.let { l -> if (order != null) l.sortedWith(order) else l }
            fun bucketSongs(buckets: List<PulseTimeBucket>): List<Song> {
                val total = buckets.sumOf { b -> s.timeBuckets[b]?.values?.sum() ?: 0.0 }
                if (total < 20.0) return emptyList() // never overfit a few plays
                val art = HashMap<String, Double>()
                buckets.forEach { b -> s.timeBuckets[b]?.forEach { (k, v) -> art[k] = (art[k] ?: 0.0) + v } }
                return pool.filter { song -> song.artists.any { (art[it.id] ?: 0.0) >= 2.0 } }
                    .sortedByDescending { song -> song.artists.maxOf { art[it.id] ?: 0.0 } }
            }
            val knownArtists = s.artists.filter { it.value.plays >= 5 }.keys
            listOf(
                Triple("pulse_on_repeat", "Pulse On Repeat",
                    byIds({ it.plays >= 3 && now - it.lastPlayed < 14L * 86_400_000 },
                        compareByDescending { PulseRanker.activation(s, it.id, now) })),
                Triple("pulse_rediscover", "Rediscover",
                    byIds({ it.plays >= 3 && now - it.lastPlayed > 21L * 86_400_000 })),
                Triple("pulse_favorites", "Pulse Favorites",
                    byIds({ it.liked || it.replays >= 2 || it.completions >= 3 },
                        compareByDescending { (s.songs[it.id]?.replays ?: 0) + (s.songs[it.id]?.completions ?: 0) })),
                Triple("pulse_discovery_mix", "Discovery Mix",
                    pool.filter { song -> song.artists.any { it.id !in knownArtists } && (s.songs[song.id]?.skips ?: 0) == 0 }),
                Triple("pulse_late_night", "Late Night Pulse",
                    bucketSongs(listOf(PulseTimeBucket.WEEKDAY_NIGHT, PulseTimeBucket.WEEKEND_NIGHT))),
                Triple("pulse_weekend", "Weekend Pulse",
                    bucketSongs(listOf(PulseTimeBucket.WEEKEND_MORNING, PulseTimeBucket.WEEKEND_AFTERNOON,
                        PulseTimeBucket.WEEKEND_EVENING, PulseTimeBucket.WEEKEND_NIGHT))),
            ).map { (id, name, songs) -> Triple("LP_PULSE_$id".uppercase(), name, songs.take(40)) }
                .filter { it.third.size >= 10 }
        }
        if (plan.isEmpty()) return@safe
        withContext(Dispatchers.IO) {
            plan.forEach { (id, name, songs) ->
                val existing = database.playlist(id).first()
                val known = mutex.withLock { ensureLoaded().let { st -> (id in st.smartPlaylistIds) to (id in st.dismissedPlaylists) } }
                if (known.second) return@forEach                      // user deleted it before: never recreate
                if (existing == null && known.first) {                // we made it, it's gone now: user deleted it
                    mutex.withLock { ensureLoaded().dismissedPlaylists.add(id) }
                    return@forEach
                }
                if (existing == null) {
                    database.insert(com.devxrohan.smusic.db.entities.PlaylistEntity(id = id, name = name))
                    mutex.withLock { ensureLoaded().smartPlaylistIds.add(id) }
                }
                database.clearPlaylist(id)
                songs.forEachIndexed { i, song ->
                    database.insert(com.devxrohan.smusic.db.entities.PlaylistSongMap(playlistId = id, songId = song.id, position = i))
                }
            }
            mutex.withLock { persist(ensureLoaded()) }
        }
    }

    /** Fresh Sounds For You: Pulse-ranks new-release albums. Cold start returns them untouched. */
    suspend fun rankNewReleases(albums: List<AlbumItem>): List<AlbumItem> = safe(albums) {
        if (!guestModeActive()) return@safe albums
        mutex.withLock {
            val s = ensureLoaded()
            if (s.effectiveShare() <= 0.0 || albums.size < 3) return@withLock albums
            val now = System.currentTimeMillis()
            val cands = albums.map { PulseCandidate(it.id, PulseKind.ALBUM, artistIdsOf(it)) }
            PulseRanker.rank(s, cands, PulseSurface.SHELF, now).map { albums[it] }
        }
    }

    /** Explicitly blocked artists, for the Pulse screen. */
    suspend fun blockedArtists(): List<PulseArtistInsight> = safe(emptyList()) {
        if (!guestModeActive()) return@safe emptyList()
        mutex.withLock {
            val s = ensureLoaded()
            s.blockedArtists.map { PulseArtistInsight(it, s.artists[it]?.name.orEmpty().ifBlank { it }, 0.0) }
        }
    }

    /** Forget learned state (keeping explicit blocks / Not-For-Me) and re-learn from History. */
    suspend fun rebuildFromHistory() = safe(Unit) {
        if (!guestModeActive()) return@safe
        mutex.withLock { ensureLoaded().backfillVersion = 0 }
        backfillIfNeeded()
    }

    /** Records which artists the music source relates to this anchor (feeds taste groups + adjacency). */
    suspend fun learnRelated(anchor: LocalItem, items: List<YTItem>) = safe(Unit) {
        if (!guestModeActive()) return@safe
        mutex.withLock {
            val s = ensureLoaded()
            val related = items.flatMap { artistIdsOf(it) }.toSet()
            // Provider-side similarity is useful evidence, but weaker than the user's own listening graph.
            PulseLearn.learnRelated(s, artistIdsOf(anchor), related, weight = 0.08)
            withContext(Dispatchers.IO) { persist(s) }
        }
    }

    /** Radio/AutoMix results are real "related" evidence from the source: learn weak seed<->item artist links. */
    suspend fun learnFromRadio(seedArtistIds: List<String>, itemArtistIds: List<List<String>>) = safe(Unit) {
        if (!guestModeActive()) return@safe
        if (seedArtistIds.isEmpty()) return@safe
        mutex.withLock {
            val s = ensureLoaded()
            PulseLearn.learnRelated(s, seedArtistIds, itemArtistIds.flatten().toSet(), weight = 0.08)
            withContext(Dispatchers.IO) { persist(s) }
        }
    }

    /** Choose unfamiliar candidates near the taste embedding while excluding played and hidden artists. */
    suspend fun discoverNext(pool: List<YTItem>, usedIds: MutableSet<String>, limit: Int = 12): List<YTItem> = safe(emptyList()) {
        if (!guestModeActive()) return@safe emptyList()
        mutex.withLock {
            val s = ensureLoaded()
            if (s.stage() < PulseStage.LEARNING) return@withLock emptyList()
            val now = System.currentTimeMillis()
            val novel = pool.asSequence().filter { it.id !in usedIds && it !is PlaylistItem }.distinctBy { it.id }
                .filter { looksLikeMusic(it) }
                .filter { item ->
                    val ids = artistIdsOf(item)
                    ids.isNotEmpty() && ids.none { it in s.seenArtists || s.isHidden(it, now) }
                }.toList()
            if (novel.size < PulseParams.MIN_SHELF_ITEMS) return@withLock emptyList()
            val cands = novel.map { PulseCandidate(it.id, kindOf(it), artistIdsOf(it)) }
            val out = PulseRanker.rank(s, cands, PulseSurface.DISCOVER, now).map { novel[it] }.take(limit)
            if (out.size < PulseParams.MIN_SHELF_ITEMS) emptyList() else out.also { usedIds.addAll(it.map { x -> x.id }) }
        }
    }

    suspend fun markShown(ids: Collection<String>, meta: PulseExposureMeta = PulseExposureMeta()) = safe(Unit) {
        if (!guestModeActive()) return@safe
        if (ids.isEmpty()) return@safe
        mutex.withLock {
            val s = ensureLoaded()
            PulseLearn.recordShown(s, ids, System.currentTimeMillis(), meta)
            withContext(Dispatchers.IO) { persist(s) }
        }
    }

    /** Explains the strongest current reason family for an item. Used by advanced Pulse UI/debugging. */
    suspend fun explain(itemId: String): String? = safe(null) {
        if (!guestModeActive()) return@safe null
        mutex.withLock {
            val s = ensureLoaded()
            val e = s.exposure[itemId]
            when {
                PulsePreferences.isActive(s.positiveFeedback[itemId], System.currentTimeMillis()) -> "You asked for more like this"
                e?.reason?.isNotBlank() == true -> e.reason
                s.songs[itemId]?.replays?.let { it >= 2 } == true -> "You replay this often"
                s.songs[itemId]?.liked == true -> "You liked this"
                s.songs[itemId]?.completions?.let { it >= 2 } == true -> "You finish this regularly"
                s.exposure[itemId]?.novel == true -> "New for you, but close to your taste"
                else -> null
            }
        }
    }

    // ---------- Backup / restore ----------

    suspend fun exportJson(): ByteArray? = safe(null) {
        mutex.withLock { PulseStorage.toJson(ensureLoaded()).toString().toByteArray(Charsets.UTF_8) }
    }

    /** Restores state from a backup; an unreadable blob is ignored and Pulse rebuilds from History. */
    suspend fun importJson(bytes: ByteArray) = safe(Unit) {
        mutex.withLock {
            val restored = runCatching {
                PulseStorage.fromJson(org.json.JSONObject(bytes.toString(Charsets.UTF_8)))
            }.getOrNull()
            state = restored ?: PulseState() // fresh => backfillVersion 0 => rebuild from History
            withContext(Dispatchers.IO) { persist(state!!) }
        }
    }

    // ---------- helpers ----------

    private fun isRejected(s: PulseState, item: LocalItem, now: Long): Boolean {
        if (artistIdsOf(item).any { s.isHidden(it, now) }) return true
        val ts = s.notForMeSongs[item.id]
        return item is Song && ts != null && now - ts < PulseParams.NOT_FOR_ME_MS
    }

    private fun artistIdsOf(item: LocalItem): List<String> = when (item) {
        is Song -> item.artists.map { it.id }
        is Artist -> listOf(item.id)
        is com.devxrohan.smusic.db.entities.Album -> item.artists.map { it.id }
        else -> emptyList()
    }

    private fun artistIdsOf(item: YTItem): List<String> = when (item) {
        is SongItem -> item.artists.mapNotNull { it.id }
        is ArtistItem -> listOf(item.id)
        is AlbumItem -> item.artists.orEmpty().mapNotNull { it.id }
        is PlaylistItem -> emptyList()
    }

    /** Cheap music-only guard for resolved results: unknown duration is kept; Shorts/podcast lengths are dropped. */
    private fun looksLikeMusic(item: YTItem): Boolean =
        if (item is SongItem) item.duration?.let { it in 30..900 } ?: true else true

    private fun kindOf(item: YTItem) = when (item) {
        is SongItem -> PulseKind.SONG
        is ArtistItem -> PulseKind.ARTIST
        is AlbumItem -> PulseKind.ALBUM
        is PlaylistItem -> PulseKind.PLAYLIST
    }

    private fun <T> interleave(a: List<T>, b: List<T>): List<T> {
        val out = ArrayList<T>(a.size + b.size)
        val ia = a.iterator(); val ib = b.iterator()
        while (ia.hasNext() || ib.hasNext()) {
            if (ia.hasNext()) out += ia.next()
            if (ib.hasNext()) out += ib.next()
        }
        return out
    }

    private suspend fun <T> safe(fallback: T, block: suspend () -> T): T =
        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Throwable) { fallback }
}
