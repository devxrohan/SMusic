package com.devxrohan.smusic.discordrpc.rpc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DiscordArtworkResolverTest {
    @Test fun registeredCoverIsCachedAcrossUpdates() = runBlocking {
        var requests = 0
        val resolver = DiscordArtworkResolver(fetch = { _, _ -> requests++; "external/cover" })
        repeat(3) { assertEquals("mp:external/cover", resolver.resolve("https://example.com/cover.jpg", "123")) }
        assertEquals(1, requests)
    }
    @Test fun concurrentUpdatesShareOneRegistration() = runBlocking {
        var requests = 0
        val resolver = DiscordArtworkResolver(fetch = { _, _ -> requests++; "external/cover" })
        coroutineScope { List(8) { async { resolver.resolve("https://example.com/cover.jpg", "123") } }.awaitAll() }
        assertEquals(1, requests)
    }
    @Test fun temporaryFailureUsesPublicUrlAndRetriesAfterCooldown() = runBlocking {
        var now = 0L
        var requests = 0
        val url = "https://example.com/cover.jpg"
        val resolver = DiscordArtworkResolver(fetch = { _, _ -> requests++; null }, clock = { now })
        assertEquals(url, resolver.resolve(url, "123"))
        assertEquals(url, resolver.resolve(url, "123"))
        assertEquals(1, requests)
        now = 30_001
        resolver.resolve(url, "123")
        assertEquals(2, requests)
    }
    @Test fun localFilesAreNeverSentToDiscord() = runBlocking {
        val resolver = DiscordArtworkResolver(fetch = { _, _ -> error("Unexpected network request") })
        assertNull(resolver.resolve("content://media/cover", "123"))
        assertNull(resolver.resolve("file:///storage/cover.png", "123"))
        assertNull(resolver.resolve("https://user:secret@example.com/image", "123"))
    }
    @Test fun missingApplicationIdKeepsPublicImageWithoutRegistration() = runBlocking {
        val resolver = DiscordArtworkResolver(fetch = { _, _ -> error("Unexpected network request") })
        assertEquals("https://example.com/cover.jpg", resolver.resolve("https://example.com/cover.jpg", null))
    }
    @Test fun cancellationDoesNotBecomeAnImageFailure() = runBlocking {
        val resolver = DiscordArtworkResolver(fetch = { _, _ -> throw CancellationException() })
        try {
            resolver.resolve("https://example.com/cover.jpg", "123")
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
