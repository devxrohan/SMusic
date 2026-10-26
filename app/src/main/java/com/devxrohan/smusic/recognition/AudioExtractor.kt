package com.devxrohan.smusic.recognition

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.devxrohan.smusic.media.MediaInfo
import com.devxrohan.smusic.media.TemporaryMediaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

class AudioExtractor(
    private val context: Context,
    private val tempManager: TemporaryMediaManager
) {

    companion object {
        private const val TAG = "AudioExtractor"
        private const val TIMEOUT_US = 10_000L
    }

    suspend fun extractSegment(
        uri: Uri,
        mediaInfo: MediaInfo,
        startMs: Long,
        durationMs: Long
    ): AudioSource = withContext(Dispatchers.IO) {
        val targetFile = tempManager.createTempFile("wav")
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var pfd: ParcelFileDescriptor? = null

        try {
            pfd = try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (_: Exception) {
                null
            }
            if (pfd != null) {
                extractor.setDataSource(pfd.fileDescriptor)
            } else {
                extractor.setDataSource(context, uri, null)
            }

            val trackIndex = mediaInfo.audioTrackIndex.takeIf { it >= 0 }
                ?: findAudioTrackIndex(extractor)
            if (trackIndex < 0) {
                throw IllegalStateException("No audio track found in media")
            }

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("Audio track has unknown MIME type")

            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100

            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            // Seek to start position
            if (startMs > 0) {
                extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val targetDurationUs = durationMs * 1000L
            var accumulatedUs = 0L
            var startPtsUs = -1L
            var totalPcmBytes = 0L

            var actualSampleRate = sampleRate
            var actualChannels = channels

            FileOutputStream(targetFile).use { fos ->
                // Write placeholder for 44-byte WAV header
                fos.write(ByteArray(44))

                val bufferInfo = MediaCodec.BufferInfo()
                var inputEOS = false
                var outputEOS = false

                while (!outputEOS && accumulatedUs < targetDurationUs) {
                    coroutineContext.ensureActive()

                    if (!inputEOS) {
                        val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inIndex >= 0) {
                            val inBuffer = codec.getInputBuffer(inIndex)
                            if (inBuffer != null) {
                                inBuffer.clear()
                                val sampleSize = extractor.readSampleData(inBuffer, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(
                                        inIndex, 0, 0, 0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputEOS = true
                                } else {
                                    val sampleTime = extractor.sampleTime
                                    codec.queueInputBuffer(
                                        inIndex, 0, sampleSize, sampleTime, 0
                                    )
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    when (outIndex) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFormat = codec.outputFormat
                            if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                actualSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                actualChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                            Log.d(TAG, "Audio output format updated: sampleRate=$actualSampleRate, channels=$actualChannels")
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                        else -> {
                            if (outIndex >= 0) {
                                val outBuffer = codec.getOutputBuffer(outIndex)
                                if (outBuffer != null && bufferInfo.size > 0) {
                                    if (startPtsUs < 0) {
                                        startPtsUs = bufferInfo.presentationTimeUs
                                    }
                                    accumulatedUs = bufferInfo.presentationTimeUs - startPtsUs

                                    outBuffer.position(bufferInfo.offset)
                                    outBuffer.limit(bufferInfo.offset + bufferInfo.size)

                                    val chunk = ByteArray(bufferInfo.size)
                                    outBuffer.get(chunk)
                                    fos.write(chunk)
                                    totalPcmBytes += chunk.size
                                }

                                codec.releaseOutputBuffer(outIndex, false)

                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    outputEOS = true
                                }
                            }
                        }
                    }
                }

                try {
                    codec.stop()
                    codec.release()
                } catch (_: Exception) {}
            }

            // Write real WAV header with total PCM size
            writeWavHeader(targetFile, totalPcmBytes, actualSampleRate, actualChannels)

            val actualDurationMs = if (actualSampleRate > 0 && actualChannels > 0) {
                (totalPcmBytes / (actualSampleRate * actualChannels * 2L)) * 1000L
            } else durationMs

            AudioSource(
                file = targetFile,
                durationMs = actualDurationMs,
                sampleRate = actualSampleRate,
                channels = actualChannels
            )
        } catch (e: Exception) {
            targetFile.delete()
            Log.e(TAG, "Audio extraction failed", e)
            throw e
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {}
            try {
                codec?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
            try {
                pfd?.close()
            } catch (_: Exception) {}
        }
    }

    private fun findAudioTrackIndex(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/", ignoreCase = true)) {
                return i
            }
        }
        return -1
    }

    private fun writeWavHeader(file: File, pcmDataLength: Long, sampleRate: Int, channels: Int) {
        val totalDataLen = pcmDataLength + 36
        val byteRate = (sampleRate * channels * 16) / 8
        val blockAlign = (channels * 16) / 8

        val header = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put('R'.code.toByte()); put('I'.code.toByte()); put('F'.code.toByte()); put('F'.code.toByte())
            putInt(totalDataLen.toInt())
            put('W'.code.toByte()); put('A'.code.toByte()); put('V'.code.toByte()); put('E'.code.toByte())
            put('f'.code.toByte()); put('m'.code.toByte()); put('t'.code.toByte()); put(' '.code.toByte())
            putInt(16) // Subchunk1Size (16 for PCM)
            putShort(1.toShort()) // AudioFormat (1 for PCM)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(16.toShort()) // BitsPerSample
            put('d'.code.toByte()); put('a'.code.toByte()); put('t'.code.toByte()); put('a'.code.toByte())
            putInt(pcmDataLength.toInt())
        }.array()

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header)
        }
    }
}
