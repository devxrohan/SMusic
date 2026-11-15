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
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Authenticated Musixmatch lyrics route using the configured lyrics API. */
object PaxsenixMusixmatchLyricsProvider : LyricsProvider {
    override val name = "PaxSenix · Musixmatch"
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
            require(key.isNotBlank()) { "PaxSenix API key is required for Musixmatch lyrics" }
            val url = "$API/lyrics/musixmatch".toHttpUrl().newBuilder()
                .addQueryParameter("t", title.forLyricsSearch())
                .addQueryParameter("a", artist.artistForLyricsSearch())
                .addQueryParameter("d", duration.toString())
                .build()
            val request = Request.Builder().url(url).header("Authorization", "Bearer $key").header("Accept", "application/json, text/plain, */*").build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "PaxSenix Musixmatch HTTP ${response.code}" }
                val body = response.body?.string().orEmpty()
                val root = runCatching { JSONObject(body) }.getOrNull()
                val lyrics = root?.let { json -> listOf("syncedLyrics", "lyrics", "lrc", "plainLyrics", "text", "content").firstNotNullOfOrNull { keyName -> json.optString(keyName).takeIf { it.isNotBlank() && it != "null" } } }
                    ?: body.trim().takeIf { it.isNotBlank() }
                lyrics ?: error("No Musixmatch lyrics found")
            }
        }
    }
}
