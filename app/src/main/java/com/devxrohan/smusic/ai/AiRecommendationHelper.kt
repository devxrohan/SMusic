package com.devxrohan.smusic.ai

import android.content.Context
import com.devxrohan.smusic.constants.AiProviderKey
import com.devxrohan.smusic.constants.DeeplApiKey
import com.devxrohan.smusic.constants.OpenRouterApiKey
import com.devxrohan.smusic.constants.OpenRouterBaseUrlKey
import com.devxrohan.smusic.constants.OpenRouterModelKey
import com.devxrohan.smusic.db.InternalDatabase
import com.devxrohan.smusic.db.entities.PlaylistEntity
import com.devxrohan.smusic.db.entities.PlaylistSongMap
import com.devxrohan.smusic.db.entities.Song
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.models.SongItem
import com.devxrohan.smusic.models.toMediaMetadata
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object AiRecommendationHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    const val PLAYLIST_NAME = "Recommended by AI"

    suspend fun generateRecommendations(
        context: Context,
        database: com.devxrohan.smusic.db.MusicDatabase? = null,
        customPrompt: String? = null,
        onLog: (suspend (String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val database = database ?: InternalDatabase.newInstance(context)

        onLog?.invoke("Analyzing your listening history...")
        // 1. Gather User Taste
        val mostPlayed: List<Song> = runCatching {
            database.mostPlayedSongs(fromTimeStamp = 0L, limit = 25).firstOrNull()
        }.getOrNull() ?: emptyList()

        val likedSongs: List<Song> = runCatching {
            database.likedSongsByPlayTimeAsc().firstOrNull()?.take(25)
        }.getOrNull() ?: emptyList()

        val recentSongs: List<Song> = if (mostPlayed.isEmpty() && likedSongs.isEmpty()) {
            runCatching {
                database.recentSongs(limit = 25).firstOrNull()
            }.getOrNull() ?: emptyList()
        } else {
            emptyList()
        }

        val pool = (mostPlayed + likedSongs + recentSongs).distinctBy { it.id }
        if (pool.isEmpty()) {
            onLog?.invoke("Not enough listening history found to generate recommendations.")
            return@withContext Result.failure(Exception("Not enough listening history found"))
        }

        val tasteList = pool.take(30).map { songObj ->
            val artists = songObj.artists.joinToString { it.name }.ifBlank { "Unknown Artist" }
            "${songObj.title} by $artists"
        }

        val prompt = """
            Based on the following list of songs the user enjoys listening to:
            ${tasteList.joinToString("\n")}

            ${customPrompt?.takeIf { it.isNotBlank() }?.let { "USER PLAYLIST REQUEST: $it\n" }.orEmpty()}
            Please recommend 20 songs that match the user's request and musical taste, energy, and vibe.
            CRITICAL RULES:
            1. You MUST output ONLY a valid JSON array of objects.
            2. Do NOT include markdown code blocks, backticks, or explanatory prose.
            3. Each object MUST contain "title" and "artist".

            Example:
            [
              {"title": "Song Name", "artist": "Artist Name"}
            ]
        """.trimIndent()

        // 2. Query AI Provider
        onLog?.invoke("Connecting to AI Provider...")
        val configuredProvider = context.dataStore.get(AiProviderKey, "Puter").trim().ifBlank { "Puter" }
        val configuredKey = if (configuredProvider.equals("DeepL", ignoreCase = true)) {
            context.dataStore.get(DeeplApiKey, "")
        } else {
            context.dataStore.get(OpenRouterApiKey, "")
        }
        // AI playlist creation must have a no-key path. Use Puter when the selected provider
        // requires a key that the user has not configured (or is translation-only like DeepL).
        val fallbackToPuter = configuredProvider.equals("DeepL", ignoreCase = true) ||
            (configuredKey.isBlank() && !configuredProvider.equals("Puter", ignoreCase = true))
        val aiProvider = if (fallbackToPuter || configuredProvider.equals("Puter", ignoreCase = true)) "Puter" else configuredProvider
        val apiKey = if (aiProvider.equals("Puter", ignoreCase = true)) "" else configuredKey
        if (fallbackToPuter) {
            onLog?.invoke("No API key configured; using the no-key Puter provider…")
        }

        val defaultBaseUrl = when (aiProvider) {
            "Puter" -> "https://api.puter.com/v1/chat/completions"
            "OpenAI" -> "https://api.openai.com/v1/chat/completions"
            "Perplexity" -> "https://api.perplexity.ai/chat/completions"
            "Claude" -> "https://api.anthropic.com/v1/messages"
            "Gemini" -> "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
            "XAi" -> "https://api.x.ai/v1/chat/completions"
            "Mistral" -> "https://api.mistral.ai/v1/chat/completions"
            "Nvidia" -> "https://integrate.api.nvidia.com/v1/chat/completions"
            "OrcaRouter" -> "https://api.orcarouter.com/v1/chat/completions"
            "Groq" -> "https://api.groq.com/openai/v1/chat/completions"
            "Custom" -> "https://openrouter.ai/api/v1/chat/completions"
            else -> "https://openrouter.ai/api/v1/chat/completions"
        }
        val baseUrl = if (fallbackToPuter || aiProvider.equals("Puter", ignoreCase = true)) defaultBaseUrl
            else context.dataStore.get(OpenRouterBaseUrlKey, defaultBaseUrl)
        val defaultModel = when (aiProvider) {
            "Puter", "OpenAI", "OrcaRouter" -> "gpt-4o-mini"
            "Claude" -> "claude-3-5-haiku-latest"
            "Perplexity" -> "sonar"
            "Gemini" -> "gemini-2.5-flash-lite"
            "XAi" -> "grok-2-latest"
            "Mistral" -> "mistral-small-latest"
            "Nvidia" -> "meta/llama-3.1-70b-instruct"
            "Groq" -> "llama-3.3-70b-versatile"
            else -> "google/gemini-2.5-flash-lite"
        }
        val model = if (fallbackToPuter || aiProvider.equals("Puter", ignoreCase = true)) defaultModel
            else context.dataStore.get(OpenRouterModelKey, defaultModel)

        if (apiKey.isBlank() && aiProvider != "Custom" && aiProvider != "Puter") {
            onLog?.invoke("API Key is missing. Please configure it in AI Settings.")
            return@withContext Result.failure(Exception("API Key is missing for $aiProvider"))
        }

        val jsonOutput = try {
            if (aiProvider == "Claude") {
                val reqBody = JSONObject().apply {
                    put("model", model.ifBlank { "claude-3-5-haiku-latest" })
                    put("max_tokens", 2048)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url(baseUrl.ifBlank { "https://api.anthropic.com/v1/messages" })
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .post(reqBody)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val err = "Claude request failed: code ${response.code}"
                    onLog?.invoke(err)
                    return@withContext Result.failure(Exception(err))
                }
                val bodyStr = response.body?.string() ?: ""
                val respJson = JSONObject(bodyStr)
                val contentArr = respJson.optJSONArray("content")
                contentArr?.optJSONObject(0)?.optString("text") ?: "[]"
            } else {
                val reqBody = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val reqBuilder = Request.Builder()
                    .url(baseUrl)
                    .post(reqBody)

                if (apiKey.isNotBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                val response = client.newCall(reqBuilder.build()).execute()
                if (!response.isSuccessful) {
                    val err = "AI Request failed: HTTP ${response.code}"
                    onLog?.invoke(err)
                    return@withContext Result.failure(Exception(err))
                }
                val bodyStr = response.body?.string() ?: ""
                val respJson = JSONObject(bodyStr)
                val choices = respJson.optJSONArray("choices")
                choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: "[]"
            }
        } catch (e: Exception) {
            val err = "Connection to AI Provider failed: ${e.localizedMessage}"
            onLog?.invoke(err)
            return@withContext Result.failure(e)
        }

        // Clean output just in case of markdown formatting
        val cleanJsonStr = jsonOutput
            .replace("```json", "")
            .replace("```", "")
            .trim()

        val jsonArray = try {
            onLog?.invoke("Parsing AI response...")
            val startIndex = cleanJsonStr.indexOf('[')
            val endIndex = cleanJsonStr.lastIndexOf(']')
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                JSONArray(cleanJsonStr.substring(startIndex, endIndex + 1))
            } else {
                JSONArray(cleanJsonStr)
            }
        } catch (e: Exception) {
            onLog?.invoke("Failed to parse AI output format.")
            return@withContext Result.failure(e)
        }

        // 3. Resolve with YouTube and Save
        val resolvedSongs = mutableListOf<SongItem>()
        val totalSongs = jsonArray.length()
        onLog?.invoke("Resolving $totalSongs recommendations with YouTube Music...")

        for (i in 0 until totalSongs) {
            val item = jsonArray.optJSONObject(i) ?: continue
            val title = item.optString("title")
            val artist = item.optString("artist")
            if (title.isNotBlank()) {
                val searchQuery = "$title $artist".trim()
                onLog?.invoke("Searching [${i + 1}/$totalSongs]: $searchQuery")
                val searchResult = runCatching {
                    YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                }.getOrNull()

                val topResult = searchResult?.items?.firstOrNull() as? SongItem
                if (topResult != null && resolvedSongs.none { it.id == topResult.id }) {
                    resolvedSongs.add(topResult)
                }
            }
        }

        if (resolvedSongs.isEmpty()) {
            val err = "Could not resolve any suggested songs on YouTube."
            onLog?.invoke(err)
            return@withContext Result.failure(Exception(err))
        }

        // 4. Update Database Playlist
        onLog?.invoke("Syncing ${resolvedSongs.size} tracks into 'Recommended by AI'...")
        var playlist = database.searchPlaylists(PLAYLIST_NAME).firstOrNull()
            ?.find { it.playlist.name == PLAYLIST_NAME }?.playlist

        if (playlist == null) {
            playlist = PlaylistEntity(
                name = PLAYLIST_NAME,
                createdAt = LocalDateTime.now(),
                lastUpdateTime = LocalDateTime.now(),
                bookmarkedAt = LocalDateTime.now(),
                isEditable = true
            )
            database.insert(playlist)
        } else {
            database.clearPlaylist(playlist.id)
            database.update(playlist.copy(lastUpdateTime = LocalDateTime.now()))
        }

        resolvedSongs.forEachIndexed { index, songItem ->
            database.insert(songItem.toMediaMetadata())
            database.insert(
                PlaylistSongMap(
                    playlistId = playlist.id,
                    songId = songItem.id,
                    position = index
                )
            )
        }

        onLog?.invoke("AI Recommendations successfully updated!")
        Result.success(resolvedSongs.size)
    }
}
