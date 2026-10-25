package com.devxrohan.smusic.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Authentic 8D Audio spatial processor:
 * - Dynamic 360° orbital azimuth trajectory (Left <-> Right, Front <-> Back)
 * - Interaural Time Difference (ITD) using sub-millisecond fractional delay modeling
 * - HRTF head shadow (frequency roll-off on shadowed ear) & rear pinna attenuation
 * - Holographic 3D early reflection room diffuser for authentic spaciousness
 * - 1D to 16D real-time intensity scaling (orbital speed, separation, ambience depth)
 * - Zero GC allocations per audio frame, O(1) complexity.
 */
@UnstableApi
class EightDAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var bypass: Boolean = false

    /**
     * Effect intensity level from 1 to 16 (default 8 = classic 8D audio).
     * Level 1: Gentle, slow ambient rotation (~25s per circle).
     * Level 8: Classic sweet-spot 8D orbit (~9s per circle, balanced ITD & depth).
     * Level 16: Rapid, deep spatial 360° orbit (~3.5s per circle, maximum binaural separation).
     */
    @Volatile
    var level: Int = 8
        set(value) {
            field = value.coerceIn(1, 16)
        }

    private var sampleRate: Int = 44100

    // Orbit phase accumulator [0 .. 2*PI]
    private var orbitPhase: Double = 0.0

    // ITD Delay buffers (128 samples each covers up to ~2.6ms at 48kHz, ITD max is ~0.65ms)
    private var delayBufferL = ShortArray(128)
    private var delayBufferR = ShortArray(128)
    private var delayWriteIndex: Int = 0

    // Head-shadow dynamic one-pole lowpass states
    private var lowpassL: Float = 0f
    private var lowpassR: Float = 0f

    // 4-stage early reflection diffuser buffers for holographic 3D room simulation
    private var diffBuf1 = ShortArray(0)
    private var diffBuf2 = ShortArray(0)
    private var diffBuf3 = ShortArray(0)
    private var diffBuf4 = ShortArray(0)
    private var diffIdx1 = 0
    private var diffIdx2 = 0
    private var diffIdx3 = 0
    private var diffIdx4 = 0

    // Smooth crossfade state to prevent clicks/pops when toggling on/off
    private var currentEnabledAlpha: Float = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate

        // Allocate reflection buffers scaled to sample rate (~4ms to 12ms early reflections)
        val msFactor = sampleRate / 1000f
        diffBuf1 = ShortArray((4.1f * msFactor).toInt().coerceAtLeast(16))
        diffBuf2 = ShortArray((6.7f * msFactor).toInt().coerceAtLeast(16))
        diffBuf3 = ShortArray((9.3f * msFactor).toInt().coerceAtLeast(16))
        diffBuf4 = ShortArray((12.1f * msFactor).toInt().coerceAtLeast(16))

        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    fun resetState() {
        delayBufferL.fill(0)
        delayBufferR.fill(0)
        delayWriteIndex = 0
        lowpassL = 0f
        lowpassR = 0f
        diffBuf1.fill(0)
        diffBuf2.fill(0)
        diffBuf3.fill(0)
        diffBuf4.fill(0)
        diffIdx1 = 0
        diffIdx2 = 0
        diffIdx3 = 0
        diffIdx4 = 0
        orbitPhase = 0.0
        currentEnabledAlpha = 0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // If bypass is active or effect disabled, pass through untouched immediately
        if (bypass || !enabled) {
            currentEnabledAlpha = 0f
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

        val currentLevel = level
        // Orbit speed: level 1 (~0.04 Hz, 25s) -> level 8 (~0.11 Hz, 9s) -> level 16 (~0.28 Hz, 3.5s)
        val orbitHz = 0.035 + (currentLevel - 1) * 0.0163
        val phaseIncrement = (2.0 * PI * orbitHz) / sampleRate

        // Spatial depth factor: level 1 (0.45) -> level 8 (0.86) -> level 16 (1.00)
        val depth = 0.45f + (currentLevel - 1) * (0.55f / 15f)

        // ITD max delay in samples (~0.65ms natural interaural delay)
        val maxItdSamples = (0.00065f * sampleRate).coerceIn(12f, 40f)

        // Ambience reflection gain: level 1 (0.10) -> level 8 (0.16) -> level 16 (0.22)
        val ambienceGain = 0.10f + (currentLevel - 1) * (0.12f / 15f)

        val targetAlpha = if (enabled) 1f else 0f
        val alphaStep = 1f / (sampleRate * 0.035f) // 35ms smooth crossfade

        val delaySize = delayBufferL.size
        val dSize1 = diffBuf1.size
        val dSize2 = diffBuf2.size
        val dSize3 = diffBuf3.size
        val dSize4 = diffBuf4.size

        repeat(frameCount) {
            val rawLeft = inputBuffer.short.toFloat()
            val rawRight = inputBuffer.short.toFloat()

            // Smooth crossfade tracking
            if (currentEnabledAlpha < targetAlpha) {
                currentEnabledAlpha = (currentEnabledAlpha + alphaStep).coerceAtMost(targetAlpha)
            } else if (currentEnabledAlpha > targetAlpha) {
                currentEnabledAlpha = (currentEnabledAlpha - alphaStep).coerceAtLeast(targetAlpha)
            }

            // 1. Orbital position on unit circle:
            // sinTheta = Left (-1) to Right (+1)
            // cosTheta = Rear (-1) to Front (+1)
            val sinTheta = sin(orbitPhase).toFloat()
            val cosTheta = cos(orbitPhase).toFloat()
            orbitPhase = (orbitPhase + phaseIncrement) % (2.0 * PI)

            // 2. Constant-Power Panning Law:
            // Map sinTheta [-1 .. +1] to pan angle [0 .. PI/2]
            val panAngle = (sinTheta + 1f) * 0.25f * PI.toFloat()
            val panL = cos(panAngle.toDouble()).toFloat()
            val panR = sin(panAngle.toDouble()).toFloat()

            // Blend based on level depth
            val gainL = (1f - depth) * 0.707f + depth * panL
            val gainR = (1f - depth) * 0.707f + depth * panR

            // 3. Interaural Time Difference (ITD):
            // Write incoming samples to circular delay buffers
            delayBufferL[delayWriteIndex] = rawLeft.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            delayBufferR[delayWriteIndex] = rawRight.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

            // If sound is on the right (sinTheta > 0), left ear receives delayed signal
            // If sound is on the left (sinTheta < 0), right ear receives delayed signal
            val delayL = (sinTheta.coerceAtLeast(0f) * maxItdSamples * depth)
            val delayR = ((-sinTheta).coerceAtLeast(0f) * maxItdSamples * depth)

            val delayedLeft = readFractionalDelay(delayBufferL, delayWriteIndex, delayL, delaySize)
            val delayedRight = readFractionalDelay(delayBufferR, delayWriteIndex, delayR, delaySize)

            delayWriteIndex = (delayWriteIndex + 1) % delaySize

            // 4. Head Shadow Filtering (HRTF Acoustic Obstruction):
            // The shadowed ear receives attenuated high frequencies
            // Dynamic one-pole lowpass filter coefficient
            val shadowCoeffL = 0.35f + 0.65f * (1f - (sinTheta.coerceAtLeast(0f) * 0.70f * depth))
            val shadowCoeffR = 0.35f + 0.65f * (1f - ((-sinTheta).coerceAtLeast(0f) * 0.70f * depth))

            lowpassL += shadowCoeffL * (delayedLeft - lowpassL)
            lowpassR += shadowCoeffR * (delayedRight - lowpassR)

            val headShadowedL = lowpassL
            val headShadowedR = lowpassR

            // 5. Rear Pinna Perception:
            // Sound behind the head (cosTheta < 0) has gentle high-mid dampening
            val rearDampening = if (cosTheta < 0f) 1f + (cosTheta * 0.16f * depth) else 1f

            // Apply spatial gains, head shadow, and rear filtering
            var spatialL = headShadowedL * gainL * rearDampening
            var spatialR = headShadowedR * gainR * rearDampening

            // 6. Holographic 3D Room Ambience (Comb reflections for acoustic depth):
            if (dSize1 > 0 && dSize2 > 0 && dSize3 > 0 && dSize4 > 0) {
                val sumMid = (rawLeft + rawRight) * 0.5f

                val out1 = diffBuf1[diffIdx1].toFloat()
                val out2 = diffBuf2[diffIdx2].toFloat()
                val out3 = diffBuf3[diffIdx3].toFloat()
                val out4 = diffBuf4[diffIdx4].toFloat()

                diffBuf1[diffIdx1] = (sumMid + out1 * 0.35f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                diffBuf2[diffIdx2] = (sumMid + out2 * 0.35f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                diffBuf3[diffIdx3] = (sumMid + out3 * 0.32f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                diffBuf4[diffIdx4] = (sumMid + out4 * 0.32f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

                diffIdx1 = (diffIdx1 + 1) % dSize1
                diffIdx2 = (diffIdx2 + 1) % dSize2
                diffIdx3 = (diffIdx3 + 1) % dSize3
                diffIdx4 = (diffIdx4 + 1) % dSize4

                val roomReflectionsL = (out1 + out3) * 0.5f * ambienceGain
                val roomReflectionsR = (out2 + out4) * 0.5f * ambienceGain

                spatialL += roomReflectionsL
                spatialR += roomReflectionsR
            }

            // Master makeup attenuation to prevent any clipping from room reflections
            val finalSpatialL = spatialL * 0.88f
            val finalSpatialR = spatialR * 0.88f

            // Interpolate between dry and wet based on enabled crossfade alpha
            val outSampleL = rawLeft * (1f - currentEnabledAlpha) + finalSpatialL * currentEnabledAlpha
            val outSampleR = rawRight * (1f - currentEnabledAlpha) + finalSpatialR * currentEnabledAlpha

            outputBuffer.putShort(clampToShort(outSampleL))
            outputBuffer.putShort(clampToShort(outSampleR))
        }
        if (inputBuffer.hasRemaining()) {
            inputBuffer.position(inputBuffer.limit())
        }
        outputBuffer.flip()
    }

    private fun readFractionalDelay(buffer: ShortArray, writeIdx: Int, delay: Float, bufSize: Int): Float {
        val readPos = writeIdx - delay
        val baseIdx = (readPos.toInt() % bufSize + bufSize) % bufSize
        val nextIdx = (baseIdx + 1) % bufSize
        val frac = readPos - readPos.toInt()
        val s0 = buffer[baseIdx].toFloat()
        val s1 = buffer[nextIdx].toFloat()
        return s0 + frac * (s1 - s0)
    }

    private fun clampToShort(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    private companion object {
        const val BYTES_PER_FRAME = 4 // Stereo, 16-bit PCM
    }
}
