@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toBitmapOrNull
import kotlinx.coroutines.CancellationException
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

/** Loads cached media artwork with bounded retries and propagates coroutine cancellation. */
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
                                .data(cachedArtworkUrl(uri.toString()))
                                .size(512)
                                .allowHardware(false)
                                // Reuse the UI artwork cache when loading media-notification thumbnails.
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                        )
                    if (result is ErrorResult) throw result.throwable
                    result.drawable?.toBitmapOrNull() ?: error("Artwork has no bitmap")
                } catch (cancelled: CancellationException) {
                    throw cancelled
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
