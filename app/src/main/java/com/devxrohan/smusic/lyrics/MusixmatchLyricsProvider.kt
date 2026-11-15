package com.devxrohan.smusic.lyrics

import android.content.Context
import com.devxrohan.smusic.constants.EnableMusixmatchLyricsKey
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Dedicated keyless Musixmatch route; parsed through the common lyrics decoder. */
object MusixmatchLyricsProvider : LyricsProvider {
    override val name = "Musixmatch"

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build()
    }

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableMusixmatchLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://lyrics.paxsenix.org/musixmatch/lyrics?title=" +
                URLEncoder.encode(title.forLyricsSearch(), "UTF-8") +
                "&artist=" + URLEncoder.encode(artist.artistForLyricsSearch(), "UTF-8")
            val request = Request.Builder().url(url).header("Accept", "application/json, text/plain, */*")
                .header("User-Agent", "SMusic").build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Musixmatch HTTP ${response.code}")
                response.body?.string() ?: error("Empty Musixmatch response")
            }
            val trimmed = body.trim()
            val content = if (trimmed.startsWith("{")) {
                extractLyrics(JSONObject(trimmed))
            } else trimmed
            content?.takeIf { it.isNotBlank() } ?: error("No Musixmatch lyrics")
        }
    }

    private fun extractLyrics(json: JSONObject): String? {
        for (key in listOf("lyrics", "lrc", "content", "text", "syncedLyrics", "plainLyrics")) {
            val value = json.optString(key).trim()
            if (value.isNotBlank() && value != "null") return value
        }
        for (key in listOf("data", "result", "response")) {
            val nested = json.optJSONObject(key) ?: continue
            extractLyrics(nested)?.let { return it }
        }
        return null
    }
}
