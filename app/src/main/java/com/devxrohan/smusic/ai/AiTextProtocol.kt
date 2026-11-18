package com.devxrohan.smusic.ai

import kotlinx.serialization.json.*

internal data class AiSongSuggestion(val title: String, val artist: String)

internal object AiTextProtocol {
    fun request(model: String, prompt: String, maxTokens: Int): String = buildJsonObject {
        put("model", model)
        put("max_tokens", maxTokens)
        putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", prompt) } }
    }.toString()

    fun message(body: String, anthropic: Boolean): String {
        val json = Json.parseToJsonElement(body).jsonObject
        val content = if (anthropic) json["content"]
            else json["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")
        val text = when (content) {
            is JsonPrimitive -> content.contentOrNull.orEmpty()
            is JsonArray -> content.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }.joinToString("\n")
            else -> ""
        }.trim()
        check(text.isNotEmpty()) { "AI provider returned an empty message" }
        return text
    }

    fun songs(text: String): List<AiSongSuggestion> {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        require(start >= 0 && end > start) { "AI provider did not return a song list" }
        return Json.parseToJsonElement(text.substring(start, end + 1)).jsonArray.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val title = item["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val artist = item["artist"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (title.isEmpty()) null else AiSongSuggestion(title, artist)
        }.distinctBy { it.title.lowercase() to it.artist.lowercase() }.take(20)
    }
}
