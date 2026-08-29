package com.devxrohan.smusic.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the update manifest and decides whether it describes something
 * newer than the build currently running.
 *
 * Ported from the SMusic (com.elite.music) project's UpdateChecker — same
 * worker endpoint, same manifest shape, same "newer than current build"
 * comparison. Uses plain HttpURLConnection rather than OkHttp since that's
 * what this project's networking already relies on for one-off unauthenticated
 * GETs (this app has no OkHttp dependency directly wired in).
 */
@Singleton
class UpdateChecker @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    /** Same worker this update system has always pointed at — now also serves the announcement.
     *  Kept out of source control: value comes from local.properties -> BuildConfig. */
    private val manifestUrl = com.devxrohan.smusic.BuildConfig.UPDATE_MANIFEST_URL

    /**
     * Fetches the combined manifest (update info + announcement) in one
     * network call. Null means the request/parse failed — callers treat
     * that the same as "nothing new to report" everywhere this is used.
     */
    suspend fun checkManifest(): AppManifest? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(manifestUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            // A stale CDN/proxy cache serving yesterday's manifest is
            // exactly wrong for this: the whole point is "is there
            // something newer right now".
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.connect()

            if (connection.responseCode !in 200..299) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString(AppManifest.serializer(), body)
        }.getOrNull()
    }

    /**
     * Null means "nothing to offer" — covers both a genuine network/parse
     * failure and a manifest that doesn't beat the running build. Those two
     * cases are handled identically everywhere this is called (silently do
     * nothing), so there's no reason to make callers distinguish them.
     */
    suspend fun checkForUpdate(): UpdateInfo? {
        val info = checkManifest()?.update ?: return null
        return info.takeIf { it.versionCode > com.devxrohan.smusic.BuildConfig.VERSION_CODE && it.apkUrl.isNotBlank() }
    }
}
