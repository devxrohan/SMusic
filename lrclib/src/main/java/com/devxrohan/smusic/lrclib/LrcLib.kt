package com.devxrohan.smusic.lrclib

import com.devxrohan.smusic.lrclib.models.Track
import com.devxrohan.smusic.lrclib.models.bestMatchingFor
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.math.abs

object LrcLib {
    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }

            // This was missing, and LrcLib is tried first for almost every song: without an explicit timeout the
            // CIO engine falls back to the OS's raw socket timeout, which on a slow or flaky connection can hang
            // for a long time before this provider fails over to the next one — that hang, repeated for every
            // song, is most of why lyrics felt slow. A 10s budget here fixes that.
            install(HttpTimeout) {
                requestTimeoutMillis = 10000L
                connectTimeoutMillis = 10000L
                socketTimeoutMillis = 10000L
            }

            defaultRequest {
                url("https://lrclib.net")
            }

            expectSuccess = true
        }
    }

    private suspend fun queryLyrics(
        artist: String,
        title: String,
        album: String? = null,
    ): List<Track> = runCatching {
        client
            .get("/api/search") {
                parameter("track_name", title)
                parameter("artist_name", artist)
                if (album != null) parameter("album_name", album)
            }.body<List<Track>>()
            .filter { it.syncedLyrics != null }
    }.getOrElse { emptyList() }

    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ) = runCatching {
        val tracks = queryLyrics(artist, title, album)

        val res = tracks.bestMatchingFor(duration)?.syncedLyrics?.let(LrcLib::Lyrics)
        if (res != null) {
            return@runCatching res.text
        } else {
            throw IllegalStateException("Lyrics unavailable")
        }
    }

    suspend fun getAllLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (String) -> Unit,
    ) {
        val tracks = queryLyrics(artist, title, album)
        var count = 0
        var plain = 0
        tracks.forEach {
            if (count <= 4) {
                if (it.syncedLyrics != null && duration == -1)
                    {
                        count++
                        it.syncedLyrics.let(callback)
                    } else {
                    if (it.syncedLyrics != null && abs(it.duration - duration) <= 2) {
                        count++
                        it.syncedLyrics.let(callback)
                    }
                    if (it.plainLyrics != null && abs(it.duration - duration) <= 2 && plain == 0) {
                        count++
                        plain++
                        it.plainLyrics.let(callback)
                    }
                }
            }
        }
    }

    suspend fun lyrics(
        artist: String,
        title: String,
    ) = runCatching {
        queryLyrics(artist = artist, title = title, album = null)
    }

    @JvmInline
    value class Lyrics(
        val text: String,
    ) {
        val sentences
            get() =
                runCatching {
                    buildMap {
                        put(0L, "")
                        text.trim().lines().filter { it.length >= 10 }.forEach {
                            put(
                                it[8].digitToInt() * 10L +
                                    it[7].digitToInt() * 100 +
                                    it[5].digitToInt() * 1000 +
                                    it[4].digitToInt() * 10000 +
                                    it[2].digitToInt() * 60 * 1000 +
                                    it[1].digitToInt() * 600 * 1000,
                                it.substring(10),
                            )
                        }
                    }
                }.getOrNull()
    }
}
