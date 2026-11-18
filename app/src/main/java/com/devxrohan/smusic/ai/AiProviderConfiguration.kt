package com.devxrohan.smusic.ai

import java.net.URI

internal data class AiProviderConfiguration(val endpoint: String, val model: String, val apiKey: String, val anthropic: Boolean)

internal object AiProviderConfigurations {
    const val PUTER_ENDPOINT = "https://api.puter.com/puterai/openai/v1/chat/completions"
    private val endpoints = mapOf(
        "puter" to PUTER_ENDPOINT,
        "openai" to "https://api.openai.com/v1/chat/completions",
        "openrouter" to "https://openrouter.ai/api/v1/chat/completions",
        "perplexity" to "https://api.perplexity.ai/chat/completions",
        "claude" to "https://api.anthropic.com/v1/messages",
        "gemini" to "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
        "xai" to "https://api.x.ai/v1/chat/completions",
        "mistral" to "https://api.mistral.ai/v1/chat/completions",
        "nvidia" to "https://integrate.api.nvidia.com/v1/chat/completions",
        "orcarouter" to "https://api.orcarouter.com/v1/chat/completions",
        "groq" to "https://api.groq.com/openai/v1/chat/completions",
    )

    fun resolve(provider: String, baseUrl: String, apiKey: String, model: String): AiProviderConfiguration? {
        val name = provider.trim().lowercase()
        // These two entries configure translation, not a chat-completion service.
        if (name == "google" || name == "deepl" || name.isEmpty()) return null
        if (apiKey.isBlank() && name != "custom") return null
        if (name == "custom" && baseUrl.isBlank()) return null
        val fallback = endpoints[name] ?: if (name == "custom") baseUrl else return null
        var endpoint = baseUrl.trim().ifBlank { fallback }.trimEnd('/')
        val uri = URI(endpoint)
        require(uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank()) { "Invalid AI endpoint" }
        if (name == "puter" && (uri.host == "api.puter.com" || endpoint == endpoints["openrouter"])) {
            endpoint = PUTER_ENDPOINT
        } else if (name != "custom" && uri.host == "api.puter.com") {
            endpoint = fallback
        } else if (name == "claude" && endpoint == endpoints["openrouter"]) {
            endpoint = fallback
        } else if (endpoint.endsWith("/v1") || endpoint.endsWith("/v1beta/openai")) {
            endpoint += if (name == "claude") "/messages" else "/chat/completions"
        }
        val defaultModel = when (name) {
            "puter", "openai", "orcarouter" -> "gpt-4o-mini"
            "claude" -> "claude-3-5-haiku-latest"
            "perplexity" -> "sonar"
            "gemini" -> "gemini-2.5-flash-lite"
            "xai" -> "grok-2-latest"
            "mistral" -> "mistral-small-latest"
            "nvidia" -> "meta/llama-3.1-70b-instruct"
            "groq" -> "llama-3.3-70b-versatile"
            else -> "google/gemini-2.5-flash-lite"
        }
        val selectedModel = if (name == "puter" && model == "google/gemini-2.5-flash-lite") defaultModel
            else model.trim().ifBlank { defaultModel }
        return AiProviderConfiguration(endpoint, selectedModel, apiKey.trim(), name == "claude")
    }
}
