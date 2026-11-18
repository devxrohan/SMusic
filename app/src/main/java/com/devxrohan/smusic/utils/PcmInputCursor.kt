package com.devxrohan.smusic.utils

/** Splits decoded PCM into complete sample frames accepted by an encoder input buffer. */
internal class PcmInputCursor(
    private val byteCount: Int,
    channelCount: Int,
    private val sampleRate: Int,
    private val startTimeUs: Long,
) {
    private val frameBytes = channelCount * 2
    private var offset = 0

    init {
        require(channelCount > 0 && sampleRate > 0)
        require(byteCount >= 0 && byteCount % frameBytes == 0)
    }

    val complete: Boolean get() = offset == byteCount

    fun next(capacity: Int): Slice? {
        if (complete) return null
        val size = minOf(capacity, byteCount - offset).let { it - it % frameBytes }
        require(size > 0) { "Audio input buffer cannot hold one PCM frame" }
        return Slice(offset, size, startTimeUs + offset.toLong() / frameBytes * 1_000_000L / sampleRate)
            .also { offset += size }
    }

    data class Slice(val offset: Int, val size: Int, val timeUs: Long)
}
