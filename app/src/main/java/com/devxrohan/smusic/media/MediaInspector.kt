package com.devxrohan.smusic.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log

object MediaInspector {

    private const val TAG = "MediaInspector"

    fun inspect(context: Context, uri: Uri): MediaInfo {
        val extractor = MediaExtractor()
        var fileSize = 0L
        var pfd: ParcelFileDescriptor? = null

        try {
            pfd = try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (_: Exception) {
                null
            }
            if (pfd != null) {
                fileSize = pfd.statSize
                extractor.setDataSource(pfd.fileDescriptor)
            } else {
                extractor.setDataSource(context, uri, null)
            }

            var hasAudio = false
            var isVideo = false
            var durationUs = 0L
            var sampleRate = 44100
            var channelCount = 2
            var audioMime: String? = null
            var audioTrackIndex = -1

            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("video/", ignoreCase = true)) {
                    isVideo = true
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = maxOf(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                    }
                } else if (mime.startsWith("audio/", ignoreCase = true) && !hasAudio) {
                    hasAudio = true
                    audioTrackIndex = i
                    audioMime = mime
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = maxOf(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                    }
                }
            }

            return MediaInfo(
                hasAudio = hasAudio,
                isVideo = isVideo,
                durationMs = durationUs / 1000L,
                sampleRate = sampleRate,
                channelCount = channelCount,
                audioMime = audioMime,
                fileSize = fileSize,
                audioTrackIndex = audioTrackIndex
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting media from URI: $uri", e)
            return MediaInfo(
                hasAudio = false,
                isVideo = false,
                durationMs = 0L,
                sampleRate = 44100,
                channelCount = 2,
                audioMime = null,
                fileSize = fileSize,
                audioTrackIndex = -1
            )
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {}
            try {
                pfd?.close()
            } catch (_: Exception) {}
        }
    }
}
