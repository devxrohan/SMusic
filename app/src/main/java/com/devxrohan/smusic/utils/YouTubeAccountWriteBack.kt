package com.devxrohan.smusic.utils

import com.devxrohan.smusic.App
import com.devxrohan.smusic.constants.InnerTubeCookieKey
import com.devxrohan.smusic.constants.DataSyncIdKey
import com.devxrohan.smusic.constants.VisitorDataKey
import com.devxrohan.smusic.innertube.utils.parseCookieString
import com.devxrohan.smusic.innertube.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import timber.log.Timber

/** Remote writes require both a live account session and explicit user consent. */
suspend fun isYouTubeAccountWriteBackAllowed(): Boolean = runCatching {
    // DataStore is authoritative. In particular, a stale in-memory cookie must
    // never re-authenticate a request after the user disconnects/signs out.
    val store = App.instance.dataStore
    val cookie = store.getSuspend(InnerTubeCookieKey, "").orEmpty()
    val authenticated = cookie.isNotBlank() && "SAPISID" in parseCookieString(cookie)
    YouTube.cookie = cookie.takeIf { authenticated }
    if (authenticated) {
        val rawSyncId = store.getSuspend(DataSyncIdKey, "").orEmpty()
        YouTube.dataSyncId = rawSyncId.takeIf { it.isNotBlank() }?.let {
            it.takeIf { value -> !value.contains("||") }
                ?: it.takeIf { value -> value.endsWith("||") }?.substringBefore("||")
                ?: it.substringAfter("||")
        }
        store.getSuspend(VisitorDataKey, "").orEmpty().takeIf { it.isNotBlank() }?.let {
            YouTube.visitorData = it
        }
    } else {
        // Keep the guest visitorData intact; only clear the account-bound sync ID.
        YouTube.dataSyncId = null
    }
    authenticated && store.getSuspend(com.devxrohan.smusic.constants.YouTubeWriteBackKey, false) == true && AccountWriteConsent.isApproved(App.instance, false)
}.getOrDefault(false)

/** A small bounded retry for transient write failures; never reports success without server success. */
suspend fun writeBackWithRetry(operation: suspend () -> Result<*>): Boolean {
    if (!isYouTubeAccountWriteBackAllowed()) return false
    repeat(2) { attempt ->
        if (!isYouTubeAccountWriteBackAllowed()) return false
        val result: Result<*> = try {
            operation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure<Any?>(error)
        }
        // A sign-out/session replacement during the first attempt cancels any
        // subsequent remote write. Local SMusic changes remain local.
        if (result.isSuccess) return true
        Timber.w(result.exceptionOrNull(), "YouTube account write-back failed (attempt ${attempt + 1}/2)")
        if (attempt == 0 && isYouTubeAccountWriteBackAllowed()) delay(500)
    }
    return false
}
