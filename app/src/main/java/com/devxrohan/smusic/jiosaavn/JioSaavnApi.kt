package com.devxrohan.smusic.jiosaavn

import com.devxrohan.smusic.innertube.models.Artist
import com.devxrohan.smusic.innertube.models.Album
import com.devxrohan.smusic.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object JioSaavnApi {
    private val client = OkHttpClient()
    private const val BASE_URL = "https://shnwazdevjiosaavn.vercel.app"

    val streamUrlCache = ConcurrentHashMap<String, String>()

    suspend fun getTrendingSongs(): Result<List<SongItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/api/trending/songs")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty body")
            val json = JSONObject(body)
            val data = json.getJSONObject("data")
            val results = data.getJSONArray("results")
            val songs = mutableListOf<SongItem>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                songs.add(parseSong(item))
            }
            songs
        }
    }

    suspend fun searchSongs(query: String): Result<List<SongItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/api/search/songs?query=${java.net.URLEncoder.encode(query, "UTF-8")}")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty body")
            val json = JSONObject(body)
            val data = json.getJSONObject("data")
            val results = data.getJSONArray("results")
            val songs = mutableListOf<SongItem>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                songs.add(parseSong(item))
            }
            songs
        }
    }

    suspend fun getStreamUrl(id: String): String? = withContext(Dispatchers.IO) {
        val mappedId = if (id.startsWith("JS:")) id else "JS:$id"
        streamUrlCache[mappedId]?.let { return@withContext it }
        val originalId = mappedId.removePrefix("JS:")
        
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/songs/$originalId")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val data = json.optJSONArray("data")
            if (data != null && data.length() > 0) {
                val item = data.getJSONObject(0)
                val downloadUrls = item.optJSONArray("downloadUrl")
                if (downloadUrls != null && downloadUrls.length() > 0) {
                    // Prefer an explicitly labelled 320 kbps source. Older API responses can
                    // omit quality metadata, in which case keep the highest entry as fallback.
                    var bestUrl: String? = null
                    var bestKbps = -1
                    for (i in 0 until downloadUrls.length()) {
                        val candidate = downloadUrls.optJSONObject(i) ?: continue
                        val url = candidate.optString("url").takeIf { it.startsWith("http") } ?: continue
                        val quality = candidate.optString("quality")
                        val kbps = Regex("(\\d+)").find(quality)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                        if (quality.contains("320", ignoreCase = true)) {
                            bestUrl = url
                            bestKbps = 320
                            break
                        }
                        if (kbps > bestKbps) {
                            bestUrl = url
                            bestKbps = kbps
                        }
                    }
                    if (bestUrl != null) {
                        streamUrlCache[mappedId] = bestUrl
                        return@withContext bestUrl
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    private fun parseSong(item: JSONObject): SongItem {
        val id = "JS:" + item.getString("id")
        val name = item.getString("name")
        val duration = item.optInt("duration", 0)
        
        val albumJson = item.optJSONObject("album")
        val album = albumJson?.optString("name")?.let { Album(it, "") }
        
        val artistsJson = item.optJSONObject("artists")
        val primaryArtists = artistsJson?.optJSONArray("primary")
        val artists = mutableListOf<Artist>()
        if (primaryArtists != null) {
            for (j in 0 until primaryArtists.length()) {
                val artistObj = primaryArtists.getJSONObject(j)
                artists.add(Artist(name = artistObj.getString("name"), id = null))
            }
        } else {
            artists.add(Artist(name = "Unknown Artist", id = null))
        }

        var thumbnailUrl = ""
        val images = item.optJSONArray("image")
        if (images != null && images.length() > 0) {
            thumbnailUrl = images.getJSONObject(images.length() - 1).getString("url")
        }

        var streamUrl = ""
        val downloadUrls = item.optJSONArray("downloadUrl")
        if (downloadUrls != null && downloadUrls.length() > 0) {
            streamUrl = downloadUrls.getJSONObject(downloadUrls.length() - 1).getString("url")
        }

        if (streamUrl.isNotEmpty()) {
            streamUrlCache[id] = streamUrl
        }

        return SongItem(
            id = id,
            title = name,
            artists = artists,
            album = album,
            duration = duration,
            thumbnail = thumbnailUrl,
            explicit = item.optBoolean("explicitContent", false)
        )
    }
}
