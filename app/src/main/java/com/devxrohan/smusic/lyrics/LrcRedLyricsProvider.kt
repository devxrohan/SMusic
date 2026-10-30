package com.devxrohan.smusic.lyrics

import android.content.Context
import com.devxrohan.smusic.constants.EnableLrcRedKey
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

/**
 * lrc.red provider.
 *
 * lrc.red returns Apple-style TTML with per-syllable timestamps. We keep the
 * TTML intact so SMusicLyricsUtils/TTMLParser can expose the exact word timing
 * to the Classic player and the full karaoke lyrics screen.
 */
object LrcRedLyricsProvider : LyricsProvider {
    override val name = "lrc.red"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableLrcRedKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val query = URLEncoder.encode("$title $artist".trim(), "UTF-8")
            val searchUrl = "https://lrc.red/search.json?q=$query"
            val json = httpGet(searchUrl) ?: error("lrc.red search failed")
            val hits = parseHits(json)
            val best = bestHit(hits, title, artist, duration)
                ?: error("No matching lrc.red recording")
            val isrc = best.isrc.orEmpty().trim().uppercase(Locale.ROOT)
            if (!ISRC.matches(isrc)) error("Invalid lrc.red ISRC")
            httpGet("https://lrc.red/s/$isrc.ttml")
                ?.takeIf { it.contains("<tt", ignoreCase = true) && it.contains("<p", ignoreCase = true) }
                ?: error("lrc.red TTML unavailable")
        }
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        getLyrics(id, title, artist, duration).onSuccess(callback)
    }

    private fun httpGet(url: String): String? {
        val connection = (java.net.URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
            useCaches = true
            setRequestProperty("Accept", "application/json, application/ttml+xml, text/xml, */*")
            setRequestProperty("User-Agent", "SMusic/1.1")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseHits(body: String): List<JSONObjectHit> {
        val root = JSONObject(body)
        val hits = root.optJSONArray("hits") ?: return emptyList()
        return (0 until hits.length()).mapNotNull { index ->
            hits.optJSONObject(index)?.let(::JSONObjectHit)
        }
    }

    private fun bestHit(hits: List<JSONObjectHit>, title: String, artist: String, duration: Int): JSONObjectHit? {
        val wantedTitle = coreTitle(title)
        val wantedVersion = versionWords(title)
        val wantedArtists = artistSet(artist)
        val durationSec = duration.takeIf { it > 0 }?.toDouble() ?: 0.0

        fun distance(hit: JSONObjectHit): Double =
            if (durationSec > 0 && hit.duration > 0) abs(hit.duration - durationSec) else 0.0

        return hits.filter { hit ->
            val hitTitle = hit.title ?: return@filter false
            val hitIsrc = hit.isrc?.uppercase(Locale.ROOT) ?: return@filter false
            ISRC.matches(hitIsrc) &&
                coreTitle(hitTitle) == wantedTitle &&
                versionWords(hitTitle) == wantedVersion &&
                (wantedArtists.isEmpty() || artistSet(hit.artist.orEmpty()).any { it in wantedArtists }) &&
                (durationSec <= 0 || hit.duration <= 0 || distance(hit) <= 3.0)
        }.minByOrNull(::distance)
    }

    private fun coreTitle(title: String): String = normalize(
        title.replace(BRACKETED, " ").substringBefore(" - ")
    ).ifEmpty { normalize(title) }

    private fun versionWords(title: String): Set<String> {
        val extras = BRACKETED.findAll(title).joinToString(" ") { it.value } +
            " " + title.substringAfter(" - ", "")
        return normalize(extras).split(' ').filter { it in VERSION_WORDS }.toSet()
    }

    private fun artistSet(artist: String): Set<String> = artist
        .split(ARTIST_SEPARATORS)
        .map(::normalize)
        .filter { it.isNotEmpty() }
        .toSet()

    private fun normalize(value: String): String = java.text.Normalizer
        .normalize(value, java.text.Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .replace(WHITESPACE, " ")
        .trim()

    private data class JSONObjectHit(
        val isrc: String?,
        val title: String?,
        val artist: String?,
        val duration: Double,
    ) {
        constructor(obj: org.json.JSONObject) : this(
            isrc = obj.optString("isrc").takeIf { it.isNotBlank() },
            title = obj.optString("title").takeIf { it.isNotBlank() },
            artist = obj.optString("artist").takeIf { it.isNotBlank() },
            duration = obj.optDouble("duration", 0.0),
        )
    }

    private val ISRC = Regex("""[A-Z]{2}[A-Z0-9]{3}\d{7}""")
    private val BRACKETED = Regex("""[(\[][^)\]]*[)\]]""")
    private val COMBINING_MARKS = Regex("""\p{Mn}+""")
    private val WHITESPACE = Regex("""\s+""")
    private val ARTIST_SEPARATORS = Regex(
        """\s*(?:,|&|;|/|\s+and\s+|\s+x\s+|\s+with\s+|\s+feat\.?\s+|\s+ft\.?\s+)\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val VERSION_WORDS = setOf(
        "live", "remix", "remixed", "mix", "acoustic", "unplugged", "instrumental",
        "karaoke", "cappella", "acapella", "demo", "edit", "version", "cover",
        "sped", "slowed", "reverb", "nightcore", "lofi", "orchestral", "extended",
    )
}
