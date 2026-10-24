package com.devxrohan.smusic.pulse

import java.util.Calendar

/** How much Pulse is allowed to influence Home. Never hard-switches; see [PulseParams.pulseShare]. */
enum class PulseStage { COLD_START, EARLY, LEARNING, PERSONALIZED, DEEP_PERSONALIZED }

enum class PulseTimeBucket {
    WEEKDAY_MORNING, WEEKDAY_AFTERNOON, WEEKDAY_EVENING, WEEKDAY_NIGHT,
    WEEKEND_MORNING, WEEKEND_AFTERNOON, WEEKEND_EVENING, WEEKEND_NIGHT;

    companion object {
        fun fromTimestamp(ms: Long): PulseTimeBucket {
            val cal = Calendar.getInstance().apply { timeInMillis = ms }
            val day = cal.get(Calendar.DAY_OF_WEEK)
            val weekend = day == Calendar.SATURDAY || day == Calendar.SUNDAY
            val slot = when (cal.get(Calendar.HOUR_OF_DAY)) {
                in 6..11 -> 0
                in 12..17 -> 1
                in 18..23 -> 2
                else -> 3
            }
            return entries[if (weekend) 4 + slot else slot]
        }
    }
}

/** Long-term per-artist taste. Mutated in place under the engine mutex. */
class ArtistAffinity(
    var name: String = "",
    var plays: Int = 0,
    var score: Double = 0.0,
    var firstPlayed: Long = 0L,
    var lastPlayed: Long = 0L,
    var skips: Int = 0,
    var liked: Boolean = false,
)

/** Per-song intelligence. Distinguishes "likes the artist" from "loves this song". */
class SongStats(
    var title: String = "",
    var artistIds: List<String> = emptyList(),
    var plays: Int = 0,
    var replays: Int = 0,
    var skips: Int = 0,
    var completions: Int = 0,
    var firstPlayed: Long = 0L,
    var lastPlayed: Long = 0L,
    var liked: Boolean = false,
    /** Ring of recent counted-play timestamps (oldest to newest) for ACT-R activation. */
    var recent: MutableList<Long> = ArrayList(),
    /** Severity-weighted skips: very early skips count more than late partial skips. */
    var skipSeverity: Double = 0.0,
)

/** Recommendation feedback loop: shown is not liked; only interaction changes confidence. */
class Exposure(
    var shown: Int = 0,
    var lastShown: Long = 0L,
    var played: Int = 0,
    var completed: Int = 0,
    var skipped: Int = 0,
    /** Was the item unfamiliar when first shown? Drives the discovery acceptance loop. */
    var novel: Boolean = false,
    /** Where the recommendation was exposed. Persisted so Pulse can learn surface-specific success. */
    var source: String = "",
    /** Shelf/row identifier inside the source. */
    var shelf: String = "",
    /** Zero-based position in the shelf when it was shown. */
    var position: Int = -1,
    /** Human-readable reason family used to create the recommendation. */
    var reason: String = "",
)

/** Album-level taste: replays/completions of the album's tracks. */
class AlbumStats(
    var plays: Int = 0,
    var completions: Int = 0,
    var lastPlayed: Long = 0L,
)

/** One observed playback, already resolved to SMusic ids. */
data class PulseExposureMeta(
    val source: String = "",
    val shelf: String = "",
    val position: Int = -1,
    val reason: String = "",
)

data class PulseSignal(
    val songId: String,
    val title: String,
    val artistIds: List<String>,
    val artistNames: List<String>,
    val percentPlayed: Double,
    val timestamp: Long,
    val liked: Boolean = false,
    val albumId: String? = null,
)

class PulseState {
    val artists: MutableMap<String, ArtistAffinity> = HashMap()
    val songs: MutableMap<String, SongStats> = HashMap()
    val recentRotation: MutableMap<String, Double> = HashMap()
    /** User's own session co-listening, keyed by [pairKey]. */
    val artistCooc: MutableMap<String, Double> = HashMap()
    /** Directed session transitions songA>songB. */
    val songTransitions: MutableMap<String, Double> = HashMap()
    val timeBuckets: MutableMap<PulseTimeBucket, MutableMap<String, Double>> = HashMap()
    val seenArtists: MutableSet<String> = HashSet()
    val albums: MutableMap<String, AlbumStats> = HashMap()
    /** Artist relatedness learned from the music source (e.g. "Similar To" results), weaker than own co-listening. */
    val relatedEdges: MutableMap<String, Double> = HashMap()
    /** Recent primary artists of the CURRENT listening session (oldest first); drives session-aware ranking. */
    val sessionRecent: MutableList<String> = ArrayList()
    /** Smart playlists Pulse created, and ones the user deleted (never recreated). */
    val smartPlaylistIds: MutableSet<String> = HashSet()
    val dismissedPlaylists: MutableSet<String> = HashSet()
    /** Bumped whenever the artist graph changes; invalidates the embedding cache. Not persisted. */
    var graphVersion: Int = 0
    internal var embCache: Pair<Int, Embedding>? = null
    var novelShown: Int = 0
    var novelPlayed: Int = 0
    val exposure: MutableMap<String, Exposure> = HashMap()
    /** Soft suppression with timestamp (ms). Reversible. */
    val notForMeSongs: MutableMap<String, Long> = HashMap()
    val dislikedArtists: MutableMap<String, Long> = HashMap()
    /** Explicit user block only. */
    val blockedArtists: MutableSet<String> = HashSet()
    /** Explicit artist subscriptions/follows are a strong positive preference signal. */
    val subscribedArtists: MutableSet<String> = HashSet()
    /** Completed offline downloads are a strong intent signal, but weaker than an explicit like. */
    val downloadedSongs: MutableSet<String> = HashSet()

    var discoveryAppetite: Double = PulseParams.DISCOVERY_NEUTRAL
    var totalPlays: Int = 0
    var lastRotationDecay: Long = 0L
    var sessionLastSong: String? = null
    var sessionLastArtist: String? = null
    var sessionLastTs: Long = 0L
    var schemaVersion: Int = PulseParams.SCHEMA_VERSION
    /** Version of the backfill that populated this state; 0 = never. Bump to force a rebuild. */
    var backfillVersion: Int = 0
    var lastBackfillEventId: Long = 0L

    fun confidence(): Double {
        val uniqueSongs = songs.size.coerceAtMost(250).toDouble() / 250.0
        val uniqueArtists = artists.size.coerceAtMost(80).toDouble() / 80.0
        val historyDepth = (totalPlays / 300.0).coerceIn(0.0, 1.0)
        val sessions = (sessionRecent.size / 8.0).coerceIn(0.0, 1.0)
        val feedback = if (exposure.isEmpty()) 0.0 else
            (exposure.values.sumOf { it.played + it.completed } / 80.0).coerceIn(0.0, 1.0)
        return (uniqueSongs * 0.30 + uniqueArtists * 0.25 + historyDepth * 0.25 + sessions * 0.05 + feedback * 0.15).coerceIn(0.0, 1.0)
    }

    fun stage(): PulseStage = when {
        confidence() < 0.08 -> PulseStage.COLD_START
        confidence() < 0.20 -> PulseStage.EARLY
        confidence() < 0.45 -> PulseStage.LEARNING
        confidence() < 0.72 -> PulseStage.PERSONALIZED
        else -> PulseStage.DEEP_PERSONALIZED
    }

    /** Pulse's allowed influence now: stage share, capped for returning users who've been away a long time. */
    fun effectiveShare(now: Long = System.currentTimeMillis()): Double {
        val base = PulseParams.pulseShare(stage())
        val idle = if (sessionLastTs == 0L) 0L else now - sessionLastTs
        return if (idle > PulseParams.STALE_AFTER_MS) minOf(base, 0.5) else base
    }

    fun topArtists(n: Int): List<Pair<String, Double>> =
        artists.entries.sortedByDescending { it.value.score }.take(n).map { it.key to it.value.score }

    fun isHidden(artistId: String, now: Long): Boolean {
        if (artistId in blockedArtists) return true
        val ts = dislikedArtists[artistId] ?: return false
        return now - ts < PulseParams.DISLIKE_COOLDOWN_MS
    }
}

fun pairKey(a: String, b: String): String = if (a <= b) "$a|$b" else "$b|$a"

/** Tuned as a set, adapted from the reference engine's constants. */
object PulseParams {
    const val SCHEMA_VERSION = 4
    /** Bump to re-run backfill after a learning-logic change. */
    const val BACKFILL_VERSION = 4

    val milestones = doubleArrayOf(0.15, 0.50, 0.90)
    const val COUNT_MILESTONE = 0.50
    const val SESSION_GAP_MS = 1_800_000L
    const val ALPHA_LONG = 0.15
    const val ALPHA_MED = 0.35
    const val ROTATION_DECAY_PER_DAY = 0.85
    const val ROTATION_PRUNE_FLOOR = 0.02
    const val LIKE_SCORE_FLOOR = 0.8
    const val SKIP_PERCENT = 0.25
    const val SKIP_SCORE_FACTOR = 0.97
    const val DISCOVERY_NEUTRAL = 0.30
    const val APPETITE_REGRESS = 0.02
    const val APPETITE_NOVEL_BONUS = 0.03
    const val APPETITE_MIN = 0.05
    const val APPETITE_MAX = 0.90

    const val EARLY_AT = 8
    const val LEARNING_AT = 30
    const val PERSONALIZED_AT = 120
    const val DEEP_AT = 500

    const val SONG_RING = 8
    const val ARTISTS_MAX = 600
    const val ARTISTS_KEEP = 500
    const val SONGS_MAX = 3000
    const val SONGS_KEEP = 2500
    const val COOC_MAX = 2000
    const val COOC_KEEP = 1500
    const val TRANSITIONS_MAX = 3000
    const val TRANSITIONS_KEEP = 2000
    const val ROTATION_MAX = 400
    const val ROTATION_KEEP = 300
    const val EXPOSURE_MAX = 3000
    const val EXPOSURE_KEEP = 2000
    const val SEEN_MAX = 3000

    const val ACT_DECAY = 0.5
    const val ACT_DT_FLOOR_HOURS = 0.05
    const val DISLIKE_COOLDOWN_MS = 14L * 24 * 60 * 60 * 1000
    const val NOT_FOR_ME_MS = 30L * 24 * 60 * 60 * 1000
    const val FRESHNESS_WINDOW_MS = 24L * 60 * 60 * 1000
    const val CONTEXT_CONFIDENCE_K = 8.0
    const val COOC_ANCHORS = 12
    const val MAX_CONSECUTIVE_ARTIST = 2
    const val MAX_ARTIST_PER_SHELF = 3
    const val MAX_ARTIST_PER_RADIO = 3
    const val MIN_RELEVANCE = 0.10
    const val BACKFILL_MAX_ROWS = 5000
    const val MIN_SHELF_ITEMS = 6
    const val HOME_SHELF_SIZE = 12
    const val STALE_AFTER_MS = 45L * 24 * 60 * 60 * 1000
    const val RELATED_MAX = 2500
    const val RELATED_KEEP = 1800
    const val ALBUMS_MAX = 800
    const val ALBUMS_KEEP = 600
    const val NOVEL_ACCEPT_MIN_SHOWN = 20

    /** Share of a Pulse-capable row filled by Pulse candidates; remainder stays original SMusic content. */
    fun pulseShare(stage: PulseStage): Double = when (stage) {
        PulseStage.COLD_START -> 0.0
        PulseStage.EARLY -> 0.2
        PulseStage.LEARNING -> 0.5
        PulseStage.PERSONALIZED -> 0.8
        PulseStage.DEEP_PERSONALIZED -> 0.9
    }
}
