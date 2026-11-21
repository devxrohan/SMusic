package com.devxrohan.smusic.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bounded short-term reuse; concurrent callers await one fetch without orphan background jobs. */
internal class RequestCoalescer<K : Any, V>(
    private val maxEntries: Int = 12,
    private val ttlMillis: Long = 30_000,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private data class Entry<V>(val value: V, val savedAt: Long)
    private val entries = LinkedHashMap<K, Entry<V>>(16, .75f, true)
    private val gates = Array(16) { Mutex() }

    suspend fun load(key: K, force: Boolean = false, fetch: suspend () -> Result<V>): Result<V> =
        gates[(key.hashCode() and Int.MAX_VALUE) % gates.size].withLock {
            val cached = synchronized(entries) { entries[key] }
            if (!force && cached != null && clock() - cached.savedAt < ttlMillis) return@withLock Result.success(cached.value)
            val result = fetch()
            val error = result.exceptionOrNull()
            if (error is CancellationException) throw error
            result.onSuccess { value ->
                synchronized(entries) {
                    entries[key] = Entry(value, clock())
                    while (entries.size > maxEntries) entries.remove(entries.keys.first())
                }
            }
            result
        }
}
