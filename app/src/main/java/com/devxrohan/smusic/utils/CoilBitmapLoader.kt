@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import java.util.concurrent.ExecutionException

/**
 * Loads the bitmap used for the lock-screen / notification media player.
 *
 * This runs in the background, often right after the screen locks, which is
 * exactly when a device's network connection is least reliable — Doze mode,
 * a per-app data saver rule, or an OEM battery manager can all briefly cut a
 * background app's network access. A single failed fetch here used to mean
 * no cover art for the rest of that playback session; there was no retry.
 * This now retries a couple of times with a short backoff, which is usually
 * enough to ride out a momentary network hiccup, and explicitly turns the
 * cache on so a thumbnail already shown in the app's own UI (very likely,
 * since the same song's artwork was just on screen to start playback) is
 * reused instantly instead of being re-fetched over the network at all.
 */
class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope,
) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(data, 0, data.size)
                ?: error("Could not decode image data")
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            var lastError: Throwable? = null
            for (attempt in 0 until MAX_ATTEMPTS) {
                val bitmap = try {
                    val result =
                        context.imageLoader.execute(
                            ImageRequest
                                .Builder(context)
                                .data(uri)
                                .allowHardware(false)
                                // The song's own thumbnail was almost certainly already
                                // loaded (and cached) to draw the player UI moments ago —
                                // always allow reusing that instead of hitting the network
                                // again just for the notification.
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                        )
                    if (result is ErrorResult) throw result.throwable
                    (result.drawable as BitmapDrawable).bitmap
                } catch (e: Exception) {
                    lastError = e
                    null
                }
                if (bitmap != null) return@future bitmap
                if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS * (attempt + 1))
            }
            throw ExecutionException(lastError ?: IllegalStateException("Unknown artwork load failure"))
        }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 400L
    }
}
