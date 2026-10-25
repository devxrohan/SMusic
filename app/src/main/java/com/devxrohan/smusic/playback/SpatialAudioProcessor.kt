package com.devxrohan.smusic.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * High-performance spatial audio processor: expands the stereo image with
 * mid/side processing and acoustic cross-feed delay modeling.
 * O(1) complexity per sample with zero allocations during playback.
 */
@UnstableApi
class SpatialAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var bypass: Boolean = false

    /** How much wider the stereo image gets. 1.0 = untouched. */
    private val widthGain = 2.5f

    /** Makeup attenuation after widening, so the wider side energy does not clip. */
    private val outputGain = 0.82f

    /** How much of the delayed, low-passed opposite channel gets mixed back in. */
    private val crossfeedGain = 0.2f

    /** One-pole lowpass factor applied to the cross-fed signal — acoustic head shadow model. */
    private val lowpassCoeff = 0.3f

    private var delayLeft = ShortArray(0)
    private var delayRight = ShortArray(0)
    private var delayIndex = 0
    private var lowpassLeft = 0f
    private var lowpassRight = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        val delaySamples = (inputAudioFormat.sampleRate * DELAY_MS / 1000f)
            .roundToInt()
            .coerceAtLeast(1)
        delayLeft = ShortArray(delaySamples)
        delayRight = ShortArray(delaySamples)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
        return inputAudioFormat
    }

    override fun onFlush() {
        delayLeft.fill(0)
        delayRight.fill(0)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
    }

    fun resetState() {
        delayLeft.fill(0)
        delayRight.fill(0)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (bypass || !enabled) {
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val frameCount = remaining / BYTES_PER_FRAME
        if (frameCount == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val bytesToProcess = frameCount * BYTES_PER_FRAME
        val outputBuffer = replaceOutputBuffer(bytesToProcess)

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        val delaySize = delayLeft.size
        repeat(frameCount) {
            val left = inputBuffer.short.toInt()
            val right = inputBuffer.short.toInt()

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f * widthGain
            var widenedLeft = mid + side
            var widenedRight = mid - side

            val delayedRight = delayRight[delayIndex].toFloat()
            val delayedLeft = delayLeft[delayIndex].toFloat()
            lowpassLeft += lowpassCoeff * (delayedRight - lowpassLeft)
            lowpassRight += lowpassCoeff * (delayedLeft - lowpassRight)
            widenedLeft += lowpassLeft * crossfeedGain
            widenedRight += lowpassRight * crossfeedGain

            delayLeft[delayIndex] = left.toShort()
            delayRight[delayIndex] = right.toShort()
            delayIndex = (delayIndex + 1) % delaySize

            outputBuffer.putShort(clampToShort(widenedLeft * outputGain))
            outputBuffer.putShort(clampToShort(widenedRight * outputGain))
        }
        if (inputBuffer.hasRemaining()) {
            inputBuffer.position(inputBuffer.limit())
        }
        outputBuffer.flip()
    }

    private fun clampToShort(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    private companion object {
        const val BYTES_PER_FRAME = 4 // stereo, 16-bit
        const val DELAY_MS = 15
    }
}
