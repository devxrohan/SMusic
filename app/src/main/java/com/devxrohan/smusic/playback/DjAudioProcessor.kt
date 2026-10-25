package com.devxrohan.smusic.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

import androidx.annotation.DrawableRes
import com.devxrohan.smusic.R

/**
 * Studio-Grade DJ Audio Processor for SMusic:
 * - Real-Time Echo & Ping-Pong Stereo Delay (20ms - 1000ms, feedback, wet/dry mix)
 * - Pioneer-Style Bipolar DJ Club Filter Sweep (Resonant LPF ◀ Flat ▶ HPF)
 * - Jet Flanger / Phase Sweep (variable LFO rate and depth)
 * - Analog Tape Saturation & Warmth (harmonic soft-clipping)
 *
 * Runs inside Media3 DefaultAudioSink with O(1) complexity and zero heap allocations per frame.
 */
enum class DjPreset(val title: String, val subtitle: String, @DrawableRes val iconRes: Int) {
    DEFAULT("Flat / Reset", "Pure original mix", R.drawable.headphones),
    CLUB_BOOTH("Club Booth", "Warm echo & bass pump", R.drawable.graphic_eq),
    SLOWED_REVERB("Slowed + Reverb", "0.85x speed & spacious reverb", R.drawable.bedtime),
    NIGHTCORE("Nightcore", "1.25x speed & high pitch", R.drawable.speed),
    BASS_BOMB("Bass Bomb", "Subwoofer drive & punch", R.drawable.volume_up),
    LOFI_VINYL("Lo-Fi Radio", "Muffled telephone filter & warmth", R.drawable.radio),
    SPACE_ECHO("Cosmic Echo", "Infinite stereo ping-pong", R.drawable.waves)
}

@UnstableApi
class DjAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var bypass: Boolean = false

    // ==========================================
    // Echo / Delay Parameters
    // ==========================================
    @Volatile
    var echoEnabled: Boolean = false

    @Volatile
    var echoDelayMs: Int = 280
        set(value) {
            field = value.coerceIn(20, 1000)
        }

    @Volatile
    var echoFeedback: Float = 0.40f
        set(value) {
            field = value.coerceIn(0.0f, 0.85f)
        }

    @Volatile
    var echoWetMix: Float = 0.45f
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
        }

    @Volatile
    var echoPingPong: Boolean = true

    // ==========================================
    // DJ Filter Sweep: -1.0 (Low-Pass) .. 0.0 (Flat) .. +1.0 (High-Pass)
    // ==========================================
    @Volatile
    var filterSweep: Float = 0.0f
        set(value) {
            field = value.coerceIn(-1.0f, 1.0f)
        }

    // ==========================================
    // Flanger Parameters
    // ==========================================
    @Volatile
    var flangerEnabled: Boolean = false

    @Volatile
    var flangerRate: Float = 0.5f // LFO rate in Hz
        set(value) {
            field = value.coerceIn(0.1f, 4.0f)
        }

    @Volatile
    var flangerDepth: Float = 0.5f
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
        }

    // ==========================================
    // Analog Warm Saturation
    // ==========================================
    @Volatile
    var saturation: Float = 0.0f
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
        }

    // DSP State
    private var sampleRate: Int = 44100

    // Echo ring buffers (1 second maximum delay per channel)
    private var delayBufL = FloatArray(48000)
    private var delayBufR = FloatArray(48000)
    private var delayWritePos: Int = 0

    // Resonant State-Variable Filter (SVF) states
    private var svfLowL = 0f
    private var svfBandL = 0f
    private var svfLowR = 0f
    private var svfBandR = 0f

    // Flanger delay buffer (~8ms at 48kHz = 384 samples)
    private var flangerBufL = FloatArray(512)
    private var flangerBufR = FloatArray(512)
    private var flangerWritePos: Int = 0
    private var flangerPhase: Double = 0.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate

        val maxDelaySamples = (sampleRate * 1.05f).toInt()
        if (delayBufL.size < maxDelaySamples) {
            delayBufL = FloatArray(maxDelaySamples)
            delayBufR = FloatArray(maxDelaySamples)
        }
        val maxFlangerSamples = (sampleRate * 0.015f).toInt().coerceAtLeast(512)
        if (flangerBufL.size < maxFlangerSamples) {
            flangerBufL = FloatArray(maxFlangerSamples)
            flangerBufR = FloatArray(maxFlangerSamples)
        }

        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    fun resetState() {
        delayBufL.fill(0f)
        delayBufR.fill(0f)
        delayWritePos = 0
        svfLowL = 0f
        svfBandL = 0f
        svfLowR = 0f
        svfBandR = 0f
        flangerBufL.fill(0f)
        flangerBufR.fill(0f)
        flangerWritePos = 0
        flangerPhase = 0.0
    }

    fun resetFx() {
        enabled = false
        echoEnabled = false
        echoDelayMs = 280
        echoFeedback = 0.40f
        echoWetMix = 0.0f
        echoPingPong = false
        filterSweep = 0.0f
        flangerEnabled = false
        flangerRate = 0.5f
        flangerDepth = 0.0f
        saturation = 0.0f
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)

        // If bypass is active or all features disabled, fast untouched copy
        val isEchoOn = echoEnabled && echoWetMix > 0.01f
        val isFilterOn = abs(filterSweep) > 0.02f
        val isFlangerOn = flangerEnabled && flangerDepth > 0.01f
        val isSatOn = saturation > 0.02f

        if (bypass || !enabled || (!isEchoOn && !isFilterOn && !isFlangerOn && !isSatOn)) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val sr = sampleRate.toFloat()
        val delaySamples = (sr * (echoDelayMs / 1000f)).roundToInt().coerceIn(1, delayBufL.size - 1)
        val feedback = echoFeedback
        val wet = echoWetMix
        val dry = 1.0f - (wet * 0.35f)
        val isPingPong = echoPingPong

        // Calculate SVF filter coefficients
        var filterActive = false
        var isHighPass = false
        var svfF = 0f
        var svfQ = 1.25f // Resonance Q for punchy club filter sweeps

        if (isFilterOn) {
            filterActive = true
            val sweep = filterSweep
            val cutoffHz = if (sweep < 0f) {
                // Low-Pass sweep: 350 Hz to 18000 Hz
                isHighPass = false
                val t = 1.0f + sweep // 0.0 at -1.0, 1.0 at 0.0
                (350f * (18000f / 350f).pow(t)).coerceIn(100f, sr * 0.45f)
            } else {
                // High-Pass sweep: 35 Hz to 4500 Hz
                isHighPass = true
                val t = sweep // 0.0 at 0.0, 1.0 at 1.0
                (35f * (4500f / 35f).pow(t)).coerceIn(20f, sr * 0.45f)
            }
            svfF = (2.0f * sin(PI * cutoffHz / sr)).toFloat().coerceIn(0.01f, 0.95f)
            svfQ = 1.0f / (1.20f)
        }

        // Flanger LFO parameters
        val flangerLfoInc = (2.0 * PI * flangerRate) / sr
        val flangerBaseDelay = (sr * 0.0025f) // 2.5 ms center
        val flangerModDepth = (sr * 0.0018f * flangerDepth) // +/- 1.8 ms modulation

        val satDrive = 1.0f + saturation * 2.2f

        while (inputBuffer.remaining() >= 4) {
            val rawShortL = inputBuffer.short
            val rawShortR = inputBuffer.short

            var sampleL = rawShortL.toFloat()
            var sampleR = rawShortR.toFloat()

            // 1. ECHO / DELAY PROCESSING
            if (isEchoOn) {
                val readPos = (delayWritePos - delaySamples + delayBufL.size) % delayBufL.size
                val delayedL = delayBufL[readPos]
                val delayedR = delayBufR[readPos]

                val echoOutL: Float
                val echoOutR: Float

                if (isPingPong) {
                    // Ping-pong cross reflections
                    echoOutL = delayedR
                    echoOutR = delayedL
                    delayBufL[delayWritePos] = sampleL + delayedR * feedback
                    delayBufR[delayWritePos] = sampleR + delayedL * feedback
                } else {
                    echoOutL = delayedL
                    echoOutR = delayedR
                    delayBufL[delayWritePos] = sampleL + delayedL * feedback
                    delayBufR[delayWritePos] = sampleR + delayedR * feedback
                }

                sampleL = sampleL * dry + echoOutL * wet
                sampleR = sampleR * dry + echoOutR * wet

                delayWritePos = (delayWritePos + 1) % delayBufL.size
            }

            // 2. DJ FILTER SWEEP (State-Variable Resonant Filter)
            if (filterActive) {
                // Left channel SVF
                svfLowL += svfF * svfBandL
                val highL = sampleL - svfLowL - svfQ * svfBandL
                svfBandL += svfF * highL
                sampleL = if (isHighPass) highL else svfLowL

                // Right channel SVF
                svfLowR += svfF * svfBandR
                val highR = sampleR - svfLowR - svfQ * svfBandR
                svfBandR += svfF * highR
                sampleR = if (isHighPass) highR else svfLowR
            }

            // 3. FLANGER / JET SWOOSH
            if (isFlangerOn) {
                flangerPhase += flangerLfoInc
                if (flangerPhase > 2.0 * PI) flangerPhase -= 2.0 * PI

                val lfoVal = sin(flangerPhase).toFloat()
                val currentFlangerDelay = (flangerBaseDelay + lfoVal * flangerModDepth).toInt()
                val flReadPos = (flangerWritePos - currentFlangerDelay + flangerBufL.size) % flangerBufL.size

                val flSampleL = flangerBufL[flReadPos]
                val flSampleR = flangerBufR[flReadPos]

                flangerBufL[flangerWritePos] = sampleL
                flangerBufR[flangerWritePos] = sampleR
                flangerWritePos = (flangerWritePos + 1) % flangerBufL.size

                sampleL = sampleL * 0.72f + flSampleL * 0.48f
                sampleR = sampleR * 0.72f + flSampleR * 0.48f
            }

            // 4. ANALOG TAPE SATURATION & SOFT LIMITER
            if (isSatOn) {
                sampleL = softClip(sampleL, satDrive)
                sampleR = softClip(sampleR, satDrive)
            }

            // Clamp to 16-bit PCM range
            val finalL = sampleL.coerceIn(-32768f, 32767f).toInt().toShort()
            val finalR = sampleR.coerceIn(-32768f, 32767f).toInt().toShort()

            outputBuffer.putShort(finalL)
            outputBuffer.putShort(finalR)
        }

        outputBuffer.flip()
    }

    private fun softClip(sample: Float, drive: Float): Float {
        val normalized = (sample / 32768f) * drive
        val clipped = if (normalized > 1.0f) {
            1.0f - (1.0f / (normalized + 1.0f))
        } else if (normalized < -1.0f) {
            -1.0f - (1.0f / (normalized - 1.0f))
        } else {
            normalized - (normalized * normalized * normalized) / 3.0f
        }
        return (clipped / drive.pow(0.5f)) * 32768f
    }
}
