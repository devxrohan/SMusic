@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.devxrohan.smusic.spotify

import android.content.Context
import androidx.work.*
import com.devxrohan.smusic.constants.SpotifyWriteBackKey
import com.devxrohan.smusic.utils.NetworkConnectivityObserver
import com.devxrohan.smusic.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Local-first, conflated immediate delivery and persisted retry, adapted from Stash's like coordinator (GPL-3.0). */
@Singleton
class SpotifyLikeDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val outbound: SpotifyOutboundSync,
) {
    data class Delivery(val pending: Int = 0, val message: String = "", val retryable: Boolean = false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val drainLock = Mutex()
    private val mutableState = MutableStateFlow(Delivery())
    val state = mutableState.asStateFlow()

    init {
        scope.launch { for (signal in wake) drain() }
        scope.launch {
            val observer = NetworkConnectivityObserver(context)
            context.dataStore.data.map { it[SpotifyWriteBackKey] == true }.distinctUntilChanged()
                .flatMapLatest { if (it) observer.networkStatus else flowOf(false) }
                .collect { if (it) wake.trySend(Unit) }
        }
        scope.launch {
            context.dataStore.data.map { it[SpotifyWriteBackKey] == true }.distinctUntilChanged().collect { enabled ->
                if (enabled) wake.trySend(Unit)
                else { outbound.discardPending(); mutableState.value = Delivery() }
            }
        }
    }

    fun requestDelivery() {
        wake.trySend(Unit)
        scheduleRetry()
    }

    private fun scheduleRetry() {
        val work = OneTimeWorkRequestBuilder<SpotifyLikeWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniqueWork("spotify_like_delivery", ExistingWorkPolicy.KEEP, work)
    }

    suspend fun drain(): Delivery = drainLock.withLock {
        val pending = try { outbound.pendingCount() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return@withLock Delivery(message = "Saved Spotify delivery state could not be read. Reconnect the account.").also { mutableState.value = it } }
        if (pending == 0) return@withLock Delivery().also { mutableState.value = it }
        mutableState.value = Delivery(pending, "Sending $pending changes to Spotify…")
        val result = try {
            val report = outbound.flushPendingChanges()
            val remaining = outbound.pendingCount()
            Delivery(remaining, when {
                report.unmatchedSongs > 0 -> "${report.unmatchedSongs} songs could not be matched on Spotify. Tap Retry to try again."
                remaining > 0 -> "Changes are waiting. Check Send changes permission and your Spotify connection."
                else -> "Changes sent to Spotify."
            })
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            val retryable = error is IOException || (error is Spotify.SpotifyException && (error.statusCode == 429 || error.statusCode >= 500))
            Delivery(runCatching { outbound.pendingCount() }.getOrDefault(pending), when {
                error is Spotify.SpotifyException && error.statusCode == 401 -> "Spotify sign-in expired. Reconnect to send waiting likes."
                error is Spotify.SpotifyException && error.statusCode == 429 -> "Spotify is rate limiting requests. Waiting likes will retry."
                error is Spotify.SpotifyException -> "Spotify rejected the request (HTTP ${error.statusCode}). Your likes remain queued."
                retryable -> "Waiting for a working connection. Your likes are saved and will retry."
                else -> "Could not send to Spotify. Your likes remain queued; tap Retry."
            }, retryable)
        }
        mutableState.value = result
        result
    }
}

class SpotifyLikeWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val delivery = spotifyServices(applicationContext).likeDispatcher().drain()
        return if (delivery.retryable && delivery.pending > 0) Result.retry() else Result.success()
    }
}
