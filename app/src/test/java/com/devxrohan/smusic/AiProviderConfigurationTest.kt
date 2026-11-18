package com.devxrohan.smusic

import com.devxrohan.smusic.ai.AiProviderConfigurations
import org.junit.Assert.*
import org.junit.Test

class AiProviderConfigurationTest {
    @Test fun migratesRetiredPuterEndpointAndModelDefaults() {
        val config = AiProviderConfigurations.resolve("Puter", "https://api.puter.com/v1/chat/completions",
            "test-token", "google/gemini-2.5-flash-lite")!!
        assertEquals(AiProviderConfigurations.PUTER_ENDPOINT, config.endpoint)
        assertEquals("gpt-4o-mini", config.model)
    }

    @Test fun replacesStaleOpenRouterDefaultsForPuter() {
        val config = AiProviderConfigurations.resolve("Puter", "https://openrouter.ai/api/v1/chat/completions",
            "test-token", "gpt-4o-mini")!!
        assertEquals(AiProviderConfigurations.PUTER_ENDPOINT, config.endpoint)
    }

    @Test fun doesNotAttemptUnauthenticatedOrTranslationOnlyChatRequests() {
        assertNull(AiProviderConfigurations.resolve("Puter", "", "", ""))
        assertNull(AiProviderConfigurations.resolve("Google", "", "", ""))
        assertNull(AiProviderConfigurations.resolve("DeepL", "", "test-token", ""))
    }

    @Test fun supportsExplicitLocalCustomEndpointsWithoutAuthentication() {
        val config = AiProviderConfigurations.resolve("Custom", "http://localhost:8080/v1/", "", "local-model")!!
        assertEquals("http://localhost:8080/v1/chat/completions", config.endpoint)
        assertEquals("local-model", config.model)
    }

    @Test fun preservesConfiguredProviderModelsAndCredentials() {
        val config = AiProviderConfigurations.resolve(" OpenAI ", "https://api.openai.com/v1/chat/completions",
            " test-token ", "custom-model")!!
        assertEquals("custom-model", config.model)
        assertEquals("test-token", config.apiKey)
        assertFalse(config.anthropic)
    }

    @Test fun doesNotSendOpenAiRequestsToStalePuterDefaults() {
        val config = AiProviderConfigurations.resolve("OpenAI", "https://api.puter.com/v1/chat/completions",
            "test-token", "gpt-4o-mini")!!
        assertEquals("https://api.openai.com/v1/chat/completions", config.endpoint)
    }
}
