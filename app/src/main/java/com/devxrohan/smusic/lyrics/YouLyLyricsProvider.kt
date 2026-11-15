package com.devxrohan.smusic.lyrics

import android.content.Context
import com.devxrohan.smusic.constants.EnableYouLyLyricsKey
import com.devxrohan.smusic.lyrics.LyricsProvider
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object YouLyLyricsProvider : LyricsProvider {
    override val name: String = "YouLyPlus"

    // Preferred mirror order; concurrent requests avoid waiting on an unavailable mirror.
    private val baseUrls = listOf(
        "https://lyricsplus.prjktla.my.id/",
        "https://lyricsplus.atomix.one/",
        "https://lyricsplus.binimum.org/",
        "https://lyricsplus.prjktla.workers.dev/",
        "https://lyricsplus-seven.vercel.app/",
        "https://lyrics-plus-backend.vercel.app/",
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableYouLyLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanTitle = title.forLyricsSearch()
            val cleanArtist = artist.artistForLyricsSearch()
            require(cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) { "Title and artist required" }
            val encTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            val encArtist = URLEncoder.encode(cleanArtist, "UTF-8")
            coroutineScope {
                val pending = baseUrls.map { baseUrl ->
                    async(Dispatchers.IO) { fetchFromMirror(baseUrl, encTitle, encArtist, duration) }
                }.toMutableList()
                try {
                    while (pending.isNotEmpty()) {
                        val (finished, lyrics) = select<Pair<kotlinx.coroutines.Deferred<String?>, String?>> {
                            pending.forEach { job -> job.onAwait { job to it } }
                        }
                        pending.remove(finished)
                        if (!lyrics.isNullOrBlank()) return@coroutineScope lyrics
                    }
                    error("Lyrics not found on YouLyPlus mirrors")
                } finally {
                    pending.forEach { it.cancel() }
                }
            }
        }
    }

    private fun fetchFromMirror(baseUrl: String, title: String, artist: String, duration: Int): String? {
        val durationQuery = if (duration > 0) "&duration=$duration" else ""
        val ttmlUrl = "${baseUrl}v1/ttml/get?title=$title&artist=$artist$durationQuery"
        val ttml = runCatching {
            val request = Request.Builder().url(ttmlUrl)
                .header("User-Agent", "SMusic/1.0").header("Accept", "application/json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null
                else JSONObject(response.body?.string().orEmpty()).optString("ttml")
                    .trim().takeIf(String::isNotBlank)
            }
        }.getOrNull()
        if (!ttml.isNullOrBlank()) return ttml

        val lrcUrl = "${baseUrl}v2/lyrics/get?title=$title&artist=$artist$durationQuery"
        return runCatching {
            val request = Request.Builder().url(lrcUrl)
                .header("User-Agent", "SMusic/1.0").header("Accept", "application/json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null
                else JSONObject(response.body?.string().orEmpty()).optString("lyrics")
                    .trim().takeIf(String::isNotBlank)
            }
        }.getOrNull()
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
}
