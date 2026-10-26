package com.devxrohan.smusic.recognition

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * New (not in SMusic) — SMusic feeds extracted audio into its own Composite/AudD recognition
 * engines, which accept arbitrary sample rates. SMusic's existing Shazam pipeline
 * (shazamkit/ShazamSignatureGenerator, already used by MusicRecognitionScreen.kt for mic input)
 * expects 16-bit mono PCM at a fixed sample rate, so this reads the WAV file [AudioExtractor]
 * produces, downmixes to mono if needed, and linearly resamples to [targetSampleRateHz].
 */
object WavPcmReader {

    fun readAsPcm16Mono(file: File, sourceSampleRate: Int, sourceChannels: Int, targetSampleRateHz: Int): ShortArray {
        val raf = RandomAccessFile(file, "r")
        val dataBytes = try {
            // Skip the standard 44-byte WAV header written by AudioExtractor.writeWavHeader.
            val headerSize = 44L
            val dataSize = (raf.length() - headerSize).coerceAtLeast(0L).toInt()
            raf.seek(headerSize)
            val buf = ByteArray(dataSize)
            raf.readFully(buf)
            buf
        } finally {
            raf.close()
        }

        val bb = ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        val totalSamples = dataBytes.size / 2 // 16-bit samples
        val interleaved = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            interleaved[i] = bb.short
        }

        // Downmix to mono.
        val mono = if (sourceChannels <= 1) {
            interleaved
        } else {
            ShortArray(interleaved.size / sourceChannels) { i ->
                var sum = 0
                for (c in 0 until sourceChannels) {
                    sum += interleaved[i * sourceChannels + c]
                }
                (sum / sourceChannels).toShort()
            }
        }

        if (sourceSampleRate == targetSampleRateHz || mono.isEmpty()) return mono

        // Simple linear resampling — adequate for fingerprinting purposes.
        val ratio = targetSampleRateHz.toDouble() / sourceSampleRate.toDouble()
        val outLength = (mono.size * ratio).toInt().coerceAtLeast(1)
        return ShortArray(outLength) { i ->
            val srcPos = i / ratio
            val idx = srcPos.toInt().coerceIn(0, mono.size - 1)
            val nextIdx = (idx + 1).coerceAtMost(mono.size - 1)
            val frac = srcPos - idx
            (mono[idx] * (1 - frac) + mono[nextIdx] * frac).toInt().toShort()
        }
    }
}
