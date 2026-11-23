package com.devxrohan.smusic.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.devxrohan.smusic.models.MediaMetadata
import com.devxrohan.smusic.ui.utils.resize
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** Platform-only AAC remuxing and cached artwork; no audio encoding or tagging dependency. */
internal object AudioFileExport {
    suspend fun artwork(context: Context, track: MediaMetadata): ByteArray? = try {
        withTimeoutOrNull(6_000L) {
            val original = DiscordCoverUrl.forSong(track.id, track.thumbnailUrl) ?: track.thumbnailUrl ?: return@withTimeoutOrNull null
            val key = ExportArtworkPolicy.cacheKey(track.id, original)
            val cached = ImageRequest.Builder(context).data(original).memoryCacheKey(key).diskCacheKey(key).size(512).allowHardware(false)
                .networkCachePolicy(CachePolicy.DISABLED).build()
            val result = (context.imageLoader.execute(cached) as? SuccessResult)
                ?: (context.imageLoader.execute(ImageRequest.Builder(context).data(original.resize(512, 512))
                    .memoryCacheKey(key).diskCacheKey(key).size(512).allowHardware(false).build()) as? SuccessResult)
                ?: return@withTimeoutOrNull null
            val bitmap = result.drawable.toBitmap()
            val scale = minOf(1f, 512f / maxOf(bitmap.width, bitmap.height))
            val compact = if (scale < 1f) Bitmap.createScaledBitmap(bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
            try { ByteArrayOutputStream().use { output ->
                check(compact.compress(Bitmap.CompressFormat.JPEG, 88, output))
                output.toByteArray()
            } } finally { if (compact !== bitmap) compact.recycle() }
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { Timber.w("Export cover unavailable; retaining audio without artwork"); null }

    suspend fun remuxAac(source: File, target: File) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        var completed = false
        try {
            extractor.setDataSource(source.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm"
            } ?: error("AAC export track is missing")
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val writer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = writer
            val outputTrack = writer.addTrack(format)
            writer.start()
            started = true
            val buffer = ByteBuffer.allocate(256 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                currentCoroutineContext().ensureActive()
                buffer.clear()
                val length = extractor.readSampleData(buffer, 0)
                if (length < 0) break
                require(length <= buffer.capacity() && extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0)
                info.set(0, length, extractor.sampleTime, extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC)
                writer.writeSampleData(outputTrack, buffer, info)
                extractor.advance()
            }
            writer.stop()
            started = false
            completed = true
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            extractor.release()
            if (!completed) target.delete()
        }
    }
}
