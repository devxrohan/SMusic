package com.devxrohan.smusic.lyrics

import android.content.Context
import com.devxrohan.smusic.constants.EnableGeniusLyricsKey
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Plain-text last-resort lyrics from Genius; never outranks a synced source. */
object GeniusLyricsProvider : LyricsProvider {
    override val name = "Genius"

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS).build()
    }

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableGeniusLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            val search = Request.Builder().url("https://genius.com/api/search/multi?q=$query")
                .header("User-Agent", "SMusic").build()
            val body = client.newCall(search).execute().use { response ->
                if (!response.isSuccessful) error("Genius search HTTP ${response.code}")
                response.body?.string() ?: error("Empty Genius search")
            }
            val sections = JSONObject(body).optJSONObject("response")?.optJSONArray("sections")
                ?: error("Genius search results missing")
            val candidates = mutableListOf<JSONObject>()
            for (i in 0 until sections.length()) {
                val section = sections.optJSONObject(i) ?: continue
                if (section.optString("type") != "song") continue
                val hits = section.optJSONArray("hits") ?: continue
                for (j in 0 until hits.length()) {
                    hits.optJSONObject(j)?.optJSONObject("result")?.let(candidates::add)
                }
            }
            val targetTitle = normalize(title)
            val targetArtist = normalize(artist)
            val match = candidates.firstOrNull { hit ->
                normalize(hit.optString("title")) == targetTitle &&
                    normalize(hit.optString("artist_names")).contains(targetArtist)
            } ?: error("No matching Genius song")
            val pageUrl = match.optString("url").takeIf(String::isNotBlank)
                ?: error("Genius page URL missing")
            val page = Request.Builder().url(pageUrl).header("User-Agent", "SMusic").build()
            val html = client.newCall(page).execute().use { response ->
                if (!response.isSuccessful) error("Genius page HTTP ${response.code}")
                response.body?.string() ?: error("Empty Genius page")
            }
            val doc = Jsoup.parse(html)
            val containers = doc.select("[data-lyrics-container=true]")
            val lines = containers.joinToString("\n") { it.wholeText().trim() }
                .lines().map(String::trim).filter(String::isNotBlank)
                .filterNot { it.equals("Embed", true) }
            lines.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: error("Genius lyrics unavailable")
        }
    }

    private fun normalize(value: String): String = java.text.Normalizer
        .normalize(value, java.text.Normalizer.Form.NFD)
        .replace(Regex("""\p{Mn}+"""), "").lowercase()
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
}
