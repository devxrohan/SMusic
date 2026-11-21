package com.devxrohan.smusic

import com.devxrohan.smusic.utils.RequestCoalescer
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class RequestCoalescerTest {
    @Test fun concurrentScreenRequestsFetchOnlyOnce() = runBlocking {
        val cache = RequestCoalescer<String, String>()
        val count = AtomicInteger()
        val results = (1..12).map { async(Dispatchers.Default) {
            cache.load("artist") { count.incrementAndGet(); delay(30); Result.success("page") }.getOrThrow()
        } }.awaitAll()
        assertEquals(List(12) { "page" }, results)
        assertEquals(1, count.get())
    }
    @Test fun expiryForceAndEvictionFetchFreshData() = runBlocking {
        var now = 0L
        var count = 0
        val cache = RequestCoalescer<String, Int>(maxEntries = 1, ttlMillis = 100, clock = { now })
        suspend fun load(key: String, force: Boolean = false) = cache.load(key, force) { Result.success(++count) }.getOrThrow()
        assertEquals(1, load("a")); assertEquals(1, load("a"))
        now = 101; assertEquals(2, load("a"))
        assertEquals(3, load("a", true))
        assertEquals(4, load("b")); assertEquals(5, load("a"))
    }
    @Test fun failuresAndAccountKeysDoNotReuseAnotherResult() = runBlocking {
        val cache = RequestCoalescer<String, String>()
        assertTrue(cache.load("account-a") { Result.failure(IllegalStateException()) }.isFailure)
        assertEquals("new", cache.load("account-a") { Result.success("new") }.getOrThrow())
        assertEquals("other", cache.load("account-b") { Result.success("other") }.getOrThrow())
    }
}
