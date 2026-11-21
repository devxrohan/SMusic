/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
// Adapted for SMusic on 2026-11-21; preserves the upstream account protocol.

package com.devxrohan.smusic.spotify

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import com.devxrohan.smusic.spotify.models.SpotifyAlbum
import com.devxrohan.smusic.spotify.models.SpotifyArtist
import com.devxrohan.smusic.spotify.models.SpotifyImage
import com.devxrohan.smusic.spotify.models.SpotifyPaging
import com.devxrohan.smusic.spotify.models.SpotifyPlaylist
import com.devxrohan.smusic.spotify.models.SpotifyPlaylistOwner
import com.devxrohan.smusic.spotify.models.SpotifyPlaylistTrack
import com.devxrohan.smusic.spotify.models.SpotifyPlaylistTracksRef
import com.devxrohan.smusic.spotify.models.SpotifyRecommendations
import com.devxrohan.smusic.spotify.models.SpotifySavedTrack
import com.devxrohan.smusic.spotify.models.SpotifySearchResult
import com.devxrohan.smusic.spotify.models.SpotifySimpleAlbum
import com.devxrohan.smusic.spotify.models.SpotifySimpleArtist
import com.devxrohan.smusic.spotify.models.SpotifyTrack
import com.devxrohan.smusic.spotify.models.SpotifyUser

/**
 * Spotify API client that uses the internal GraphQL API (api-partner.spotify.com)
 * for most operations, falling back to the public REST API (api.spotify.com/v1/)
 * only for endpoints without a GraphQL equivalent (top tracks/artists,
 * recommendations, related artists).
 *
 * GraphQL persisted-query hashes sourced from:
 * https://github.com/sonic-liberation/hetu_spotify_gql_client
 */
object Spotify {
    @Volatile
    var accessToken: String? = null

    private const val GQL_URL = "https://api-partner.spotify.com/pathfinder/v2/query"

    private fun randomUserAgent(): String {
        val osOptions =
            arrayOf(
                "Windows NT 10.0; Win64; x64",
                "Macintosh; Intel Mac OS X 10_15_7",
                "X11; Linux x86_64",
            )
        val chromeBase = 140
        val chromeMajor = chromeBase - (0..4).random()
        val chromePatch = (0..499).random()
        val os = osOptions.random()
        return "Mozilla/5.0 ($os) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$chromeMajor.0.$chromePatch.0 Safari/537.36"
    }

    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    private val restClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
            defaultRequest {
                url("https://api.spotify.com/v1/")
                header("User-Agent", randomUserAgent())
                header("app-platform", "WebPlayer")
                header("Origin", "https://open.spotify.com")
                header("Referer", "https://open.spotify.com/")
            }
            expectSuccess = false
        }
    }

    private val gqlClient by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
            defaultRequest {
                header("User-Agent", randomUserAgent())
                header("app-platform", "WebPlayer")
                header("Origin", "https://open.spotify.com")
                header("Referer", "https://open.spotify.com/")
                header("Accept", "application/json")
            }
            expectSuccess = false
        }
    }

    class SpotifyException(
        val statusCode: Int,
        override val message: String,
        val retryAfterSec: Long = 0,
        cause: Throwable? = null,
    ) : Exception(message, cause)

    @Volatile
    var logger: ((level: String, message: String) -> Unit)? = null

    private fun log(
        level: String,
        message: String,
    ) {
        logger?.invoke(level, message)
    }

    // ── JSON navigation helpers ──────────────────────────────────────────

    private fun JsonObject.obj(key: String): JsonObject? =
        try {
            this[key]?.takeIf { it !is JsonNull }?.jsonObject
        } catch (_: Exception) {
            null
        }

    private fun JsonObject.str(key: String): String? =
        try {
            this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) {
            null
        }

    private fun JsonObject.int(key: String): Int? =
        try {
            this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull
        } catch (_: Exception) {
            null
        }

    private fun JsonObject.arr(key: String): JsonArray? =
        try {
            this[key]?.takeIf { it !is JsonNull }?.jsonArray
        } catch (_: Exception) {
            null
        }

    // ── GraphQL core ─────────────────────────────────────────────────────

    /**
     * Callback invoked when a GQL hash is rejected (PersistedQueryNotFound).
     * The app module sets this to trigger a remote hash refresh.
     */
    @Volatile
    var onHashExpired: ((operationName: String) -> Unit)? = null

    private suspend fun graphqlPost(
        operationName: String,
        variables: JsonObject = buildJsonObject {},
    ): JsonObject {
        val token =
            accessToken ?: throw SpotifyException(401, "Not authenticated").also {
                log("E", "GQL $operationName — no token")
            }

        val primaryHash = SpotifyHashProvider.getHash(operationName)
        val hashCandidates =
            buildList {
                add(primaryHash)
                SpotifyHashProvider.getPreviousHash(operationName)?.let { prev ->
                    if (prev != primaryHash) add(prev)
                }
            }

        for ((hashIdx, sha256Hash) in hashCandidates.withIndex()) {
            val body = buildGqlBody(operationName, sha256Hash, variables)
            val result = executeGqlWithRetries(operationName, token, body)

            if (result.isPersistedQueryNotFound) {
                if (hashIdx < hashCandidates.lastIndex) {
                    log("W", "GQL $operationName hash rejected, trying previous_hash")
                    continue
                }
                log("E", "GQL $operationName all known hashes rejected, triggering remote refresh")
                onHashExpired?.invoke(operationName)
                throw SpotifyException(412, "PersistedQueryNotFound for $operationName — hash may have rotated")
            }

            return result.json!!
        }

        throw SpotifyException(412, "No valid hash found for $operationName")
    }

    private fun buildGqlBody(
        operationName: String,
        sha256Hash: String,
        variables: JsonObject,
    ): JsonObject =
        buildJsonObject {
            put("variables", variables)
            put("operationName", operationName)
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("version", 1)
                    put("sha256Hash", sha256Hash)
                }
            }
        }

    private class GqlResult(
        val json: JsonObject?,
        val isPersistedQueryNotFound: Boolean,
    )

    private suspend fun executeGqlWithRetries(
        operationName: String,
        token: String,
        body: JsonObject,
    ): GqlResult {
        val maxRetries = 3
        for (attempt in 0 until maxRetries) {
            log(
                "D",
                "GQL POST $operationName (token: [redacted])" +
                    if (attempt > 0) " [retry $attempt]" else "",
            )

            val response =
                gqlClient.post(GQL_URL) {
                    header("Authorization", "Bearer $token")
                    setBody(
                        TextContent(
                            body.toString(),
                            ContentType.Application.Json.withParameter("charset", "UTF-8"),
                        ),
                    )
                }

            log("D", "GQL POST $operationName -> ${response.status.value}")

            if (response.status == HttpStatusCode.Unauthorized) {
                throw SpotifyException(401, "Token expired or invalid")
            }
            if (response.status == HttpStatusCode.TooManyRequests) {
                val retryAfter = response.headers["Retry-After"]?.toLongOrNull() ?: (2L * (attempt + 1))
                if (attempt < maxRetries - 1 && retryAfter <= 5) {
                    log("W", "GQL $operationName -> 429, waiting ${retryAfter}s (attempt ${attempt + 1}/$maxRetries)")
                    delay(retryAfter * 1000)
                    continue
                }
                throw SpotifyException(429, "Rate limited", retryAfterSec = retryAfter)
            }
            if (response.status == HttpStatusCode.PreconditionFailed) {
                return GqlResult(json = null, isPersistedQueryNotFound = true)
            }
            if (response.status.value !in 200..299) {
                val bodyText = response.bodyAsText()
                log("E", "GQL $operationName FAILED: ${response.status.value} — ${bodyText.take(200)}")
                throw SpotifyException(response.status.value, "GraphQL error ${response.status.value}: $bodyText")
            }

            val responseJson = json.parseToJsonElement(response.bodyAsText()).jsonObject

            val errors = responseJson.arr("errors")
            if (errors != null && errors.isNotEmpty()) {
                val errorMsg = errors[0].jsonObject.str("message") ?: "Unknown GraphQL error"
                if (errorMsg.contains("PersistedQueryNotFound", ignoreCase = true)) {
                    return GqlResult(json = null, isPersistedQueryNotFound = true)
                }
                log("E", "GQL $operationName returned error: $errorMsg")
                throw SpotifyException(400, "GraphQL: $errorMsg")
            }

            return GqlResult(json = responseJson, isPersistedQueryNotFound = false)
        }

        throw SpotifyException(429, "Rate limited after $maxRetries retries")
    }

    // ── REST core (fallback for endpoints without GQL equivalent) ────────

    private suspend inline fun <reified T> authenticatedGet(
        endpoint: String,
        failFastOn429: Boolean = false,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T {
        val token =
            accessToken ?: throw SpotifyException(401, "Not authenticated").also {
                log("E", "REST $endpoint — no token")
            }

        val maxRetries = if (failFastOn429) 1 else 3
        val maxRetryDelaySec = 3L
        for (attempt in 0 until maxRetries) {
            log(
                "D",
                "REST GET $endpoint (token: [redacted])" +
                    if (attempt > 0) " [retry $attempt]" else "",
            )
            val response =
                restClient.get(endpoint) {
                    header("Authorization", "Bearer $token")
                    block()
                }
            log("D", "REST GET $endpoint -> ${response.status.value}")

            if (response.status == HttpStatusCode.Unauthorized) {
                throw SpotifyException(401, "Token expired or invalid")
            }
            if (response.status == HttpStatusCode.TooManyRequests) {
                val retryAfter = response.headers["Retry-After"]?.toLongOrNull() ?: (2L * (attempt + 1))
                if (failFastOn429 || retryAfter > maxRetryDelaySec) {
                    log("W", "REST $endpoint -> 429, failing fast (retryAfter=${retryAfter}s)")
                    throw SpotifyException(429, "Rate limited", retryAfterSec = retryAfter)
                }
                if (attempt < maxRetries - 1) {
                    log("W", "REST $endpoint -> 429, waiting ${retryAfter}s (attempt ${attempt + 1}/$maxRetries)")
                    delay(retryAfter * 1000)
                    continue
                }
                throw SpotifyException(429, "Rate limited", retryAfterSec = retryAfter)
            }
            if (response.status.value !in 200..299) {
                val bodyText = response.bodyAsText()
                log("E", "REST $endpoint FAILED: ${response.status.value} — ${bodyText.take(200)}")
                throw SpotifyException(response.status.value, "Spotify API error ${response.status.value}: $bodyText")
            }
            return response.body()
        }

        throw SpotifyException(429, "Rate limited after $maxRetries retries")
    }

    // ── GQL response converters ──────────────────────────────────────────

    private fun parseGqlImage(source: JsonObject): SpotifyImage? {
        val url = source.str("url") ?: return null
        return SpotifyImage(url = url, height = source.int("height"), width = source.int("width"))
    }

    private fun parseGqlImages(sources: JsonArray?): List<SpotifyImage> =
        sources?.mapNotNull { parseGqlImage(it.jsonObject) } ?: emptyList()

    private fun parseGqlSimpleArtist(artistObj: JsonObject): SpotifySimpleArtist? {
        val uri = artistObj.str("uri") ?: return null
        return SpotifySimpleArtist(
            id = uri.substringAfterLast(":"),
            name = artistObj.obj("profile")?.str("name") ?: "",
            uri = uri,
        )
    }

    /**
     * Parses the common track data structure shared across multiple GQL
     * operations (fetchPlaylist, fetchLibraryTracks, queryArtistOverview, etc.).
     *
     * @param albumOverride When non-null, used instead of the `albumOfTrack`
     *   field (needed for album-track responses where no albumOfTrack is present).
     * @param uriOverride When non-null, used as the track URI instead of
     *   reading it from [trackData]. Needed when the URI lives on a wrapper
     *   object (e.g. `track._uri`) rather than inside `track.data`.
     */
    private fun parseGqlTrack(
        trackData: JsonObject,
        albumOverride: SpotifySimpleAlbum? = null,
        uriOverride: String? = null,
    ): SpotifyTrack {
        val uri =
            uriOverride
                ?: trackData.str("uri")
                ?: trackData.str("_uri")
                ?: ""
        val trackId = uri.substringAfterLast(":")
        val isLocal = uri.startsWith("spotify:local:")

        val artists =
            trackData.obj("artists")?.arr("items")?.mapNotNull { elem ->
                parseGqlSimpleArtist(elem.jsonObject)
            } ?: emptyList()

        val album =
            albumOverride ?: run {
                val albumData = trackData.obj("albumOfTrack")
                val albumUri = albumData?.str("uri") ?: ""
                val albumId = albumUri.substringAfterLast(":")
                SpotifySimpleAlbum(
                    id = albumId,
                    name = albumData?.str("name") ?: "",
                    images = parseGqlImages(albumData?.obj("coverArt")?.arr("sources")),
                    uri = albumUri.ifEmpty { null },
                )
            }

        return SpotifyTrack(
            id = trackId,
            name = trackData.str("name") ?: "",
            artists = artists,
            album = album,
            durationMs = parseGqlTrackDurationMs(trackData),
            uri = uri.ifEmpty { null },
            isLocal = isLocal,
        )
    }

    /**
     * Extracts track duration in ms from GQL track payload.
     * Tries multiple keys because different operations may return duration
     * as nested (duration.totalMilliseconds) or flat (durationMs / duration_ms).
     */
    private fun parseGqlTrackDurationMs(trackData: JsonObject): Int {
        trackData.obj("duration")?.int("totalMilliseconds")?.let { if (it > 0) return it }
        trackData.int("durationMs")?.let { if (it > 0) return it }
        trackData.int("duration_ms")?.let { if (it > 0) return it }
        // Some APIs return duration in seconds
        trackData.int("duration")?.let { sec -> if (sec > 0) return sec * 1000 }
        return 0
    }

    /**
     * Flattens the nested `images.items[].sources[]` structure used by
     * playlists in the GQL response.
     */
    private fun parseGqlPlaylistImages(imagesObj: JsonObject?): List<SpotifyImage> =
        imagesObj?.arr("items")?.flatMap { imageGroup ->
            parseGqlImages(imageGroup.jsonObject.arr("sources"))
        } ?: emptyList()


    suspend fun me(): Result<SpotifyUser> =
        runCatching {
            try {
                val response =
                    graphqlPost(
                        operationName = "profileAttributes",
                    )
                val profile =
                    response.obj("data")?.obj("me")?.obj("profile")
                        ?: throw SpotifyException(500, "Invalid profileAttributes response")

                val uri = profile.str("uri") ?: ""
                SpotifyUser(
                    id = uri.substringAfterLast(":"),
                    displayName = profile.str("name"),
                    email = null,
                    images = parseGqlImages(profile.obj("avatar")?.arr("sources")),
                )
            } catch (e: Exception) {
                log("W", "GQL me() failed, falling back to REST: ${e.message}")
                authenticatedGet<SpotifyUser>("me")
            }
        }

    // ── Playlists (GQL: libraryV3) ──────────────────────────────────────

    suspend fun myPlaylists(
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyPlaylist>> =
        runCatching {
            val vars =
                buildJsonObject {
                    putJsonArray("filters") { add("Playlists") }
                    put("order", null as String?)
                    put("textFilter", "")
                    putJsonArray("features") {
                        add("LIKED_SONGS")
                        add("YOUR_EPISODES_V2")
                        add("PRERELEASES")
                        add("EVENTS")
                    }
                    put("limit", limit)
                    put("offset", offset)
                    // Ask Spotify to return every leaf playlist regardless of folder
                    // nesting. Without flatten=true the response only contains root-
                    // level items: top-level playlists plus FolderResponseWrapper
                    // entries whose contents are never expanded — the parser below
                    // ignores non-PlaylistResponseWrapper items, so anything inside
                    // a folder would otherwise be invisible (issues #46, #78).
                    put("flatten", true)
                    putJsonArray("expandedFolders") {}
                    put("folderUri", null as String?)
                    put("includeFoldersWhenFlattening", false)
                }

            val response =
                graphqlPost(
                    operationName = "libraryV3",
                    variables = vars,
                )

            val libraryData =
                response.obj("data")?.obj("me")?.obj("libraryV3")
                    ?: throw SpotifyException(500, "Invalid libraryV3 response")

            val totalCount = libraryData.int("totalCount") ?: 0
            val pagingInfo = libraryData.obj("pagingInfo")

            val playlists =
                libraryData.arr("items")?.mapNotNull { itemElem ->
                    val wrapper = itemElem.jsonObject.obj("item") ?: return@mapNotNull null
                    if (wrapper.str("__typename") != "PlaylistResponseWrapper") return@mapNotNull null
                    parsePlaylistWrapper(wrapper)
                } ?: emptyList()

            SpotifyPaging(
                items = playlists,
                total = totalCount,
                limit = pagingInfo?.int("limit") ?: limit,
                offset = pagingInfo?.int("offset") ?: offset,
            )
        }


    private fun parsePlaylistWrapper(wrapper: JsonObject): SpotifyPlaylist? {
        val data = wrapper.obj("data") ?: return null
        if (data.str("__typename") != "Playlist") return null
        val playlistUri = wrapper.str("_uri") ?: return null
        val playlistId = playlistUri.substringAfterLast(":")
        val ownerData = data.obj("ownerV2")?.obj("data")
        val ownerId = ownerData?.str("uri")?.substringAfterLast(":") ?: ownerData?.str("id") ?: ""
        return SpotifyPlaylist(
            id = playlistId,
            name = data.str("name") ?: "",
            description = data.str("description"),
            images = parseGqlPlaylistImages(data.obj("images")),
            owner =
                SpotifyPlaylistOwner(
                    id = ownerId,
                    displayName = ownerData?.str("name"),
                    uri = ownerData?.str("uri"),
                ),
            tracks = SpotifyPlaylistTracksRef(total = parsePlaylistTrackCount(data)),
            uri = playlistUri,
        )
    }

    private fun parsePlaylistTrackCount(data: JsonObject): Int? =
        data.obj("content")?.int("totalCount")
            ?: data.obj("contents")?.int("totalCount")
            ?: data.obj("tracks")?.int("totalCount")
            ?: data.obj("tracksV2")?.int("totalCount")
            ?: data.int("totalCount")
            ?: data.int("trackCount")
            ?: data.int("numTracks")


    suspend fun playlist(playlistId: String): Result<SpotifyPlaylist> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("uri", "spotify:playlist:$playlistId")
                    put("offset", 0)
                    put("limit", 25)
                    put("enableWatchFeedEntrypoint", true)
                }

            val response =
                graphqlPost(
                    operationName = "fetchPlaylist",
                    variables = vars,
                )

            val playlist =
                response.obj("data")?.obj("playlistV2")
                    ?: throw SpotifyException(500, "Invalid fetchPlaylist response")

            val ownerData = playlist.obj("ownerV2")?.obj("data")
            val ownerUri = ownerData?.str("uri") ?: ""

            val images =
                playlist.obj("images")?.arr("items")?.firstOrNull()?.let {
                    parseGqlImages(it.jsonObject.arr("sources"))
                } ?: emptyList()

            SpotifyPlaylist(
                id = playlistId,
                name = playlist.str("name") ?: "",
                description = playlist.str("description"),
                images = images,
                owner =
                    SpotifyPlaylistOwner(
                        id = ownerUri.substringAfterLast(":"),
                        displayName = ownerData?.str("name"),
                        uri = ownerUri.ifEmpty { null },
                    ),
                tracks = SpotifyPlaylistTracksRef(total = parsePlaylistTrackCount(playlist)),
                collaborative = (playlist.obj("members")?.arr("items")?.size ?: 0) > 1,
            )
        }

    suspend fun playlistTracks(
        playlistId: String,
        limit: Int = 100,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyPlaylistTrack>> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("uri", "spotify:playlist:$playlistId")
                    put("offset", offset)
                    put("limit", limit)
                    put("enableWatchFeedEntrypoint", false)
                }

            val response =
                graphqlPost(
                    operationName = "fetchPlaylist",
                    variables = vars,
                )

            val content =
                response.obj("data")?.obj("playlistV2")?.obj("content")
                    ?: throw SpotifyException(500, "No content in fetchPlaylist response")

            val tracks =
                content.arr("items")?.mapNotNull { elem ->
                    val itemWrapper = elem.jsonObject.obj("itemV2") ?: return@mapNotNull null
                    val itemData = itemWrapper.obj("data") ?: return@mapNotNull null
                    val wrapperUri = itemWrapper.str("_uri") ?: itemWrapper.str("uri")
                    val uid = elem.jsonObject.str("uid") ?: itemWrapper.str("uid")
                    SpotifyPlaylistTrack(
                        track = parseGqlTrack(itemData, uriOverride = wrapperUri),
                        uid = uid,
                    )
                } ?: emptyList()

            SpotifyPaging(
                items = tracks,
                total = content.int("totalCount") ?: 0,
                limit = limit,
                offset = offset,
            )
        }


    suspend fun likedSongs(
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifySavedTrack>> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("offset", offset)
                    put("limit", limit)
                }

            val response =
                graphqlPost(
                    operationName = "fetchLibraryTracks",
                    variables = vars,
                )

            val tracksData =
                response
                    .obj("data")
                    ?.obj("me")
                    ?.obj("library")
                    ?.obj("tracks")
                    ?: throw SpotifyException(500, "Invalid fetchLibraryTracks response")

            val savedTracks =
                tracksData.arr("items")?.mapNotNull { elem ->
                    val trackWrapper = elem.jsonObject.obj("track") ?: return@mapNotNull null
                    val trackData = trackWrapper.obj("data") ?: return@mapNotNull null
                    val wrapperUri = trackWrapper.str("_uri") ?: trackWrapper.str("uri")
                    SpotifySavedTrack(track = parseGqlTrack(trackData, uriOverride = wrapperUri))
                } ?: emptyList()

            SpotifyPaging(
                items = savedTracks,
                total = tracksData.int("totalCount") ?: 0,
                limit = limit,
                offset = offset,
            )
        }


}
