package com.devxrohan.smusic.lyrics

import android.content.Context
import com.devxrohan.smusic.constants.EnablePaxsenixLyricsKey
import com.devxrohan.smusic.constants.PaxsenixLyricsApiKey
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Authenticated Spotify lyrics route using the configured lyrics API. */
object PaxsenixSpotifyLyricsProvider : LyricsProvider {
    override val name = "PaxSenix · Spotify"
    private const val API = "https://api.paxsenix.org"
    private val client by lazy { OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build() }
    @Volatile private var apiKey: String = ""
    fun setApiKey(value: String) { apiKey = value.trim().removePrefix("Bearer ").trim() }
    override fun isEnabled(context: Context): Boolean {
        setApiKey(context.dataStore[PaxsenixLyricsApiKey].orEmpty())
        return (context.dataStore[EnablePaxsenixLyricsKey] ?: true) && apiKey.isNotBlank()
    }

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val key = apiKey
            require(key.isNotBlank()) { "PaxSenix API key is required for Spotify lyrics" }
            val query = "${title.forLyricsSearch()} ${artist.artistForLyricsSearch()}"
            val searchUrl = "$API/spotify/search".toHttpUrl().newBuilder().addQueryParameter("q", query).build()
            val searchRequest = Request.Builder().url(searchUrl).header("Authorization", "Bearer $key").header("Accept", "application/json").build()
            val searchBody = client.newCall(searchRequest).execute().use { response ->
                check(response.isSuccessful) { "PaxSenix Spotify search HTTP ${response.code}" }
                response.body?.string().orEmpty()
            }
            val trackId = findTrackId(JSONObject(searchBody), title.forLyricsSearch(), artist.artistForLyricsSearch())
                ?: error("No matching Spotify recording found")
            val lyricsUrl = "$API/lyrics/spotify".toHttpUrl().newBuilder().addQueryParameter("id", trackId).build()
            val lyricsRequest = Request.Builder().url(lyricsUrl).header("Authorization", "Bearer $key").header("Accept", "application/json, text/plain, */*").build()
            client.newCall(lyricsRequest).execute().use { response ->
                check(response.isSuccessful) { "PaxSenix Spotify lyrics HTTP ${response.code}" }
                extractLyrics(response.body?.string().orEmpty()) ?: error("No Spotify lyrics found")
            }
        }
    }

    private fun extractLyrics(body: String): String? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return body.trim().takeIf { it.isNotBlank() }
        return listOf("syncedLyrics", "lyrics", "lrc", "plainLyrics", "text", "content")
            .firstNotNullOfOrNull { root.optString(it).takeIf { value -> value.isNotBlank() && value != "null" } }
    }

    private fun findTrackId(root: Any?, wantedTitle: String, wantedArtist: String): String? {
        val candidates = mutableListOf<JSONObject>()
        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> {
                    if (value.has("id") || value.has("track_id") || value.has("trackId")) candidates += value
                    val keys = value.keys()
                    while (keys.hasNext()) walk(value.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until value.length()) walk(value.opt(i))
            }
        }
        walk(root)
        fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        return candidates.mapNotNull { obj ->
            val name = obj.optString("name", obj.optString("title", obj.optString("track_name")))
            val by = obj.optString("artistName", obj.optString("artist", obj.optString("artist_name")))
            val trackId = obj.optString("id", obj.optString("trackId", obj.optString("track_id"))).takeIf { it.isNotBlank() && it != "null" }
            val score = (if (norm(name) == norm(wantedTitle)) 2 else if (norm(name).contains(norm(wantedTitle)) || norm(wantedTitle).contains(norm(name))) 1 else 0) +
                (if (norm(by).contains(norm(wantedArtist)) || norm(wantedArtist).contains(norm(by))) 2 else 0)
            trackId?.let { it to score }
        }.maxByOrNull { it.second }?.takeIf { it.second >= 3 }?.first
    }
}
