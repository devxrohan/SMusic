package com.devxrohan.smusic.lyrics

import android.content.Context
import com.devxrohan.smusic.constants.EnableBiniLyricsKey
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Recording-aware Apple TTML lookup through the BiniLyrics catalogue. */
object BiniLyricsProvider : LyricsProvider {
    override val name: String = "BiniLyrics"
    private const val BASE = "https://lyrics-api.binimum.org/"
    private const val DURATION_TOLERANCE_SECONDS = 5
    data class Hit(val isrc: String, val lyricsUrl: String)

    private val hitsByVideoId = ConcurrentHashMap<String, Hit>()
    fun isrcFor(videoId: String): String? = hitsByVideoId[videoId]?.isrc

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS).build()
    }

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBiniLyricsKey] ?: true

    suspend fun identify(
        videoId: String,
        title: String,
        artist: String,
        duration: Int,
    ): Hit? = withContext(Dispatchers.IO) {
        hitsByVideoId[videoId]?.let { return@withContext it }
        val cleanTitle = title.forLyricsSearch()
        val cleanArtist = artist.artistForLyricsSearch()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return@withContext null

        val requestUrl = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("track", cleanTitle)
            .addQueryParameter("artist", cleanArtist)
            .apply { if (duration > 0) addQueryParameter("duration", duration.toString()) }
            .build()
        val request = Request.Builder().url(requestUrl).header("Accept", "application/json")
            .header("User-Agent", "SMusic").build()
        val body = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        }.getOrNull() ?: return@withContext null
        val results = runCatching { JSONObject(body).optJSONArray("results") }.getOrNull()
            ?: return@withContext null

        val wantedTitle = cleanTitle.lyricsCoreTitle()
        val wantedVersion = cleanTitle.lyricsVersionWords()
        val wantedArtists = cleanArtist.lyricsArtistKeys()
        val best = (0 until results.length()).mapNotNull { index ->
            val hit = results.optJSONObject(index) ?: return@mapNotNull null
            val hitTitle = hit.optString("track_name").takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val hitArtist = hit.optString("artist_name")
            val hitIsrc = hit.optString("isrc").uppercase(Locale.ROOT)
            if (!hitIsrc.matches(ISRC)) return@mapNotNull null

            // Match any credited artist, not the entire comma-joined artist
            // string. Catalogue order and YouTube collaborator order vary.
            val hitArtists = hitArtist.lyricsArtistKeys()
            if (wantedArtists.isNotEmpty() && hitArtists.intersect(wantedArtists).isEmpty()) {
                return@mapNotNull null
            }
            if (hitTitle.lyricsCoreTitle() != wantedTitle ||
                hitTitle.lyricsVersionWords() != wantedVersion) return@mapNotNull null

            val hitDuration = hit.optInt("duration", 0)
            val distance = if (duration > 0 && hitDuration > 0) abs(duration - hitDuration) else 0
            if (distance > DURATION_TOLERANCE_SECONDS) return@mapNotNull null
            val document = hit.optString("lyricsUrl").ifBlank { hit.optString("lyrics_url") }
            if (document.isBlank()) return@mapNotNull null
            val artistMatchCount = hitArtists.intersect(wantedArtists).size
            Candidate(Hit(hitIsrc, document), distance, artistMatchCount)
        }.minWithOrNull(
            compareBy<Candidate> { it.durationDistance }
                .thenByDescending { it.artistMatchCount },
        )?.hit ?: return@withContext null

        if (videoId.isNotBlank()) {
            hitsByVideoId[videoId] = best
            if (hitsByVideoId.size > 100) {
                hitsByVideoId.keys.firstOrNull { it != videoId }?.let(hitsByVideoId::remove)
            }
        }
        best
    }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val hit = hitsByVideoId[id] ?: identify(id, title, artist, duration)
                ?: error("No matching BiniLyrics recording")
            val request = Request.Builder().url(hit.lyricsUrl)
                .header("Accept", "application/ttml+xml, text/xml, */*")
                .header("User-Agent", "SMusic").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("BiniLyrics document HTTP ${response.code}")
                response.body?.string()?.takeIf { it.contains("<tt", true) && it.contains("<p", true) }
                    ?: error("Invalid BiniLyrics TTML")
            }
        }
    }

    private data class Candidate(
        val hit: Hit,
        val durationDistance: Int,
        val artistMatchCount: Int,
    )

    private fun String.lyricsCoreTitle(): String {
        val core = replace(BRACKETED, " ").substringBefore(" - ")
        return core.lyricsMatchKey().ifEmpty { lyricsMatchKey() }
    }

    private fun String.lyricsVersionWords(): Set<String> {
        val lower = lowercase(Locale.ROOT)
        val bracketed = BRACKETED.findAll(lower).joinToString(" ") { it.value }
        val suffix = lower.substringAfter(" - ", "")
        return "$bracketed $suffix".lyricsMatchKey().split(' ').filter { it in VERSION_WORDS }.toSet()
    }

    private val ISRC = Regex("""[A-Z]{2}[A-Z0-9]{3}\d{7}""")
    private val BRACKETED = Regex("""[(\[][^)\]]*[)\]]""")
    private val VERSION_WORDS = setOf(
        "live", "remix", "remixed", "mix", "acoustic", "unplugged", "instrumental",
        "karaoke", "cappella", "acapella", "demo", "edit", "version", "cover",
        "sped", "slowed", "reverb", "nightcore", "lofi", "orchestral", "extended",
    )
}
