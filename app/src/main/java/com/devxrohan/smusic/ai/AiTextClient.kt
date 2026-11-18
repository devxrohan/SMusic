package com.devxrohan.smusic.ai

import android.content.Context
import com.devxrohan.smusic.constants.AiProviderKey
import com.devxrohan.smusic.constants.OpenRouterApiKey
import com.devxrohan.smusic.constants.OpenRouterBaseUrlKey
import com.devxrohan.smusic.constants.OpenRouterModelKey
import com.devxrohan.smusic.utils.dataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

internal object AiTextClient {
    private val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()

    suspend fun complete(context: Context, prompt: String, maxTokens: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val preferences = context.dataStore.data.first()
            val config = AiProviderConfigurations.resolve(
                preferences[AiProviderKey] ?: "Google",
                preferences[OpenRouterBaseUrlKey] ?: "",
                preferences[OpenRouterApiKey] ?: "",
                preferences[OpenRouterModelKey] ?: "",
            ) ?: return@withContext Result.failure(IllegalStateException("No chat provider configured"))
            val body = AiTextProtocol.request(config.model, prompt, maxTokens)
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val builder = Request.Builder().url(config.endpoint).post(body)
            if (config.apiKey.isNotEmpty()) {
                if (config.anthropic) builder.header("x-api-key", config.apiKey).header("anthropic-version", "2023-06-01")
                else builder.header("Authorization", "Bearer ${config.apiKey}")
            }
            client.newCall(builder.build()).execute().use { response ->
                coroutineContext.ensureActive()
                if (!response.isSuccessful) return@withContext Result.failure(
                    IllegalStateException("AI provider request failed (HTTP ${response.code})"))
                Result.success(AiTextProtocol.message(response.body?.string().orEmpty(), config.anthropic))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Do not surface URLs, response bodies or credentials in UI/work logs.
            Result.failure(IllegalStateException("AI provider is unavailable; check its configuration"))
        }
    }
}
