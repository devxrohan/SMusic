package com.devxrohan.smusic.spotify

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import java.util.concurrent.ConcurrentHashMap

/** Stops an in-flight import before a local removal changes its playlist. */
internal object SpotifyPlaylistImports {
    private val jobs = ConcurrentHashMap<String, Job>()
    fun register(id: String, job: Job) { jobs.put(id, job)?.takeIf { it !== job }?.cancel() }
    fun finished(id: String, job: Job) { jobs.remove(id, job) }
    suspend fun cancel(id: String) { jobs.remove(id)?.cancelAndJoin() }
}
