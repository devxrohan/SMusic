package com.devxrohan.smusic.discordrpc.repository

import com.devxrohan.smusic.discordrpc.rpc.DiscordArtworkResolver
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DiscordRpcRepository(private val token: String? = null) {
    private val client = lazy { HttpClient() }
    private val artwork = DiscordArtworkResolver(fetch = { applicationId, url ->
        if (token.isNullOrBlank()) null else registerImage(applicationId, url)
    })

    suspend fun getImage(url: String, applicationId: String? = null): String? = artwork.resolve(url, applicationId)

    private suspend fun registerImage(applicationId: String, url: String): String? = try {
        withTimeoutOrNull(4_000L) {
            val response = client.value.post("https://discord.com/api/v9/applications/$applicationId/external-assets") {
                header("Authorization", token)
                header("User-Agent", "Discord-Android/314013;RNA")
                header("Content-Type", "application/json")
                setBody(buildJsonObject { put("urls", buildJsonArray { add(url) }) }.toString())
            }
            if (response.status.value !in 200..299) return@withTimeoutOrNull null
            Json.parseToJsonElement(response.bodyAsText()).jsonArray.firstOrNull()?.jsonObject
                ?.get("external_asset_path")?.jsonPrimitive?.contentOrNull
        }
    } catch (cancel: CancellationException) { throw cancel }
    catch (_: Exception) { null }

    fun close() { if (client.isInitialized()) client.value.close() }
}
