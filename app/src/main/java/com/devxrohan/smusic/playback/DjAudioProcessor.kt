package com.devxrohan.smusic.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

import androidx.annotation.DrawableRes
import com.devxrohan.smusic.R

/** In-chain stereo effects processor for delay, filtering, modulation and saturation. */
enum class DjPreset(val title: String, val subtitle: String, @param:DrawableRes val iconRes: Int) {
    DEFAULT("Flat / Reset", "Pure original mix", R.drawable.headphones),
    CLUB_BOOTH("Club Booth", "Warm echo & bass pump", R.drawable.graphic_eq),
    SLOWED_REVERB("Slowed + Reverb", "0.85x speed & spacious reverb", R.drawable.bedtime),
    NIGHTCORE("Nightcore", "1.25x speed & high pitch", R.drawable.speed),
    BASS_BOMB("Bass Bomb", "Subwoofer drive & punch", R.drawable.volume_up),
    LOFI_VINYL("Lo-Fi Radio", "Muffled telephone filter & warmth", R.drawable.radio),
    SPACE_ECHO("Cosmic Echo", "Infinite stereo ping-pong", R.drawable.waves),
    EIGHT_D("8D Audio", "360° orbital surround motion", R.drawable.eight_d),
    SPATIAL_SOUND("Spatial Sound", "Widened stereo image & cross-feed", R.drawable.compare_arrows)
}

@UnstableApi
class DjAudioProcessor : BaseAudioProcessor() {

    // Clear delay, filter and modulation buffers when enabling effects to prevent stale audio replay.
    @Volatile
    private var _enabled: Boolean = false
    var enabled: Boolean
        get() = _enabled
        set(value) {
            if (value && !_enabled) {
                // Fresh activation: wipe any stale samples before we start writing again.
                resetState()
            }
            _enabled = value
        }

    @Volatile
    var bypass: Boolean = false

    // ==========================================
    // Echo / Delay Parameters
    // ==========================================
    @Volatile
    private var _echoEnabled: Boolean = false
    var echoEnabled: Boolean
        get() = _echoEnabled
        set(value) {
            if (value && !_echoEnabled) {
                // Same reasoning as `enabled` above: a preset can toggle echo on while
                // the processor was already enabled (buffer not otherwise cleared).
                delayBufL.fill(0f)
                delayBufR.fill(0f)
                delayWritePos = 0
                combBufAL.fill(0f); combBufAR.fill(0f)
                combBufBL.fill(0f); combBufBR.fill(0f)
                combBufCL.fill(0f); combBufCR.fill(0f)
                combPosA = 0; combPosB = 0; combPosC = 0
            }
            _echoEnabled = value
        }

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

    // The lo-fi path combines band limiting, amplitude wow and low-level noise.
    @Volatile
    var bassCutHz: Float = 0f // 0 = off
        set(value) {
            field = value.coerceIn(0f, 400f)
        }

    @Volatile
    var wowDepth: Float = 0f // 0 = off, amplitude-modulation "wow" (open-source lo-fi technique)
        set(value) {
            field = value.coerceIn(0f, 0.3f)
        }

    @Volatile
    var wowRateHz: Float = 5f
        set(value) {
            field = value.coerceIn(0.5f, 8f)
        }

    @Volatile
    var vinylNoiseLevel: Float = 0f // 0 = off
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    // Use a free-running duck/recovery envelope for the club preset; this is not a beat detector.
    @Volatile
    var pumpDepth: Float = 0f // 0 = off
        set(value) {
            field = value.coerceIn(0f, 0.4f)
        }

    @Volatile
    var pumpRateHz: Float = 2.0f
        set(value) {
            field = value.coerceIn(0.5f, 4f)
        }

    // Apply the bass shelf before saturation and limiting; downstream hardware gain is outside this
    // limiter.
    @Volatile
    var bassShelfGain: Float = 0f // 0 = off, ~1.0 = a solid, controlled lift
        set(value) {
            field = value.coerceIn(0f, 1.6f)
        }

    @Volatile
    var bassShelfHz: Float = 130f
        set(value) {
            field = value.coerceIn(60f, 250f)
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

    // Echo ring buffers (1 second maximum delay per channel) -- this is still used as
    // the single long "slap" tap (gives Club Booth / Cosmic Echo a clear rhythmic
    // repeat, which is what an "echo" preset is supposed to have).
    private var delayBufL = FloatArray(48000)
    private var delayBufR = FloatArray(48000)
    private var delayWritePos: Int = 0

    // Parallel short comb filters diffuse delay taps into a reverb tail; lengths scale with sample
    // rate.
    private var combALenMs = 23.2f
    private var combBLenMs = 29.7f
    private var combCLenMs = 37.1f
    private var combBufAL = FloatArray(2200)
    private var combBufAR = FloatArray(2200)
    private var combBufBL = FloatArray(2200)
    private var combBufBR = FloatArray(2200)
    private var combBufCL = FloatArray(2200)
    private var combBufCR = FloatArray(2200)
    private var combPosA = 0
    private var combPosB = 0
    private var combPosC = 0

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

    // Apply a final 50 Hz high-pass while effects are active to limit sub-bass excursion.
    private var rumbleInL = 0f
    private var rumbleOutL = 0f
    private var rumbleInR = 0f
    private var rumbleOutR = 0f

    // Lo-fi bass-cut high-pass state (one-pole, same DC-blocker shape as the rumble
    // guard above, but with a settable cutoff instead of a fixed 50 Hz).
    private var bcInL = 0f
    private var bcOutL = 0f
    private var bcInR = 0f
    private var bcOutR = 0f

    // Wow (slow amplitude wobble) LFO phase.
    private var wowPhase = 0.0

    // Pump (sidechain-style duck) cycle phase, 0..1.
    private var pumpPhase = 0.0

    // Bass shelf's own dedicated one-pole low-pass state (kept independent of the
    // user-facing filterSweep, which Lo-Fi etc. also use for a different purpose).
    private var shelfStateL = 0f
    private var shelfStateR = 0f

    // Vinyl noise/crackle generator state.
    private val vinylRng = java.util.Random(0x5EED)
    private var cracklePopSamplesLeftL = 0
    private var cracklePopSamplesLeftR = 0
    private var cracklePopAmpL = 0f
    private var cracklePopAmpR = 0f

    // Use fast-attack, smooth-release limiting to constrain transients without sustained clipping.
    private var limiterGain = 1f

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
        // Allocate for up to 50ms (comfortably covers the longest comb at its maximum
        // runtime scale factor) so the per-call read-offset in queueInput never has to
        // reallocate -- only the offset within this buffer changes at runtime.
        val combMaxSamples = (sampleRate * 0.05f).toInt().coerceAtLeast(64)
        if (combBufAL.size < combMaxSamples) { combBufAL = FloatArray(combMaxSamples); combBufAR = FloatArray(combMaxSamples) }
        if (combBufBL.size < combMaxSamples) { combBufBL = FloatArray(combMaxSamples); combBufBR = FloatArray(combMaxSamples) }
        if (combBufCL.size < combMaxSamples) { combBufCL = FloatArray(combMaxSamples); combBufCR = FloatArray(combMaxSamples) }

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
        rumbleInL = 0f; rumbleOutL = 0f; rumbleInR = 0f; rumbleOutR = 0f
        bcInL = 0f; bcOutL = 0f; bcInR = 0f; bcOutR = 0f
        wowPhase = 0.0
        pumpPhase = 0.0
        shelfStateL = 0f; shelfStateR = 0f
        cracklePopSamplesLeftL = 0; cracklePopSamplesLeftR = 0
        cracklePopAmpL = 0f; cracklePopAmpR = 0f
        limiterGain = 1f
        combBufAL.fill(0f); combBufAR.fill(0f)
        combBufBL.fill(0f); combBufBR.fill(0f)
        combBufCL.fill(0f); combBufCR.fill(0f)
        combPosA = 0; combPosB = 0; combPosC = 0
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
        bassCutHz = 0f
        wowDepth = 0f
        vinylNoiseLevel = 0f
        pumpDepth = 0f
        bassShelfGain = 0f
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        // Use native byte order for Media3 PCM reads and writes.
        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        // If bypass is active or all features disabled, fast untouched copy
        val isEchoOn = echoEnabled && echoWetMix > 0.01f
        val isFilterOn = abs(filterSweep) > 0.02f
        val isFlangerOn = flangerEnabled && flangerDepth > 0.01f
        val isSatOn = saturation > 0.02f
        val isBassCutOn = bassCutHz > 1f
        val isWowOn = wowDepth > 0.001f
        val isVinylNoiseOn = vinylNoiseLevel > 0.01f
        val isPumpOn = pumpDepth > 0.01f
        val isBassShelfOn = bassShelfGain > 0.01f

        if (bypass || !enabled || (!isEchoOn && !isFilterOn && !isFlangerOn && !isSatOn && !isBassCutOn && !isWowOn && !isVinylNoiseOn && !isPumpOn && !isBassShelfOn)) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val sr = sampleRate.toFloat()
        val feedback = echoFeedback
        // Delay Time slider scales the diffuser's size, clamped so the longest comb
        // never crosses ~45ms (see field comments: that's the point a repeat stops
        // fusing into a wash and starts sounding like a distinct "double").
        val combScale = (echoDelayMs / 280f).coerceIn(0.45f, 1.25f)
        val combASamples = (sr * combALenMs * combScale / 1000f).roundToInt().coerceIn(8, combBufAL.size - 1)
        val combBSamples = (sr * combBLenMs * combScale / 1000f).roundToInt().coerceIn(8, combBufBL.size - 1)
        val combCSamples = (sr * combCLenMs * combScale / 1000f).roundToInt().coerceIn(8, combBufCL.size - 1)
        val wet = echoWetMix
        // Use complementary dry/wet gains so increasing the wet mix does not duplicate the dry signal.
        val dry = 1.0f - wet
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
            // Resonance trimmed (was 1/1.20): the peak this creates right at the
            // cutoff was piling extra energy on top of saturation/boost specifically
            // in the bass region on low-pass-heavy presets.
            svfQ = 1.0f / (1.45f)
        }

        // Flanger LFO parameters
        val flangerLfoInc = (2.0 * PI * flangerRate) / sr
        val flangerBaseDelay = (sr * 0.0025f) // 2.5 ms center
        val flangerModDepth = (sr * 0.0018f * flangerDepth) // Use shallow delay modulation and bounded drive to limit sustained saturation.
        val satDrive = 1.0f + saturation * 1.5f

        // One-pole DC-blocker-style high-pass at ~50 Hz for the rumble guard above.
        val rumbleAlpha = kotlin.math.exp(-2.0 * PI * 50.0 / sr).toFloat()

        // Lo-fi bass-cut high-pass coefficient (settable cutoff, same shape as above).
        val bcAlpha = if (isBassCutOn) kotlin.math.exp(-2.0 * PI * bassCutHz / sr).toFloat() else 0f

        // Wow (amplitude-modulation) LFO increment -- per the open-source reference
        // technique: output = input * (1 + depth * sin(2*pi*rate*t)).
        val wowInc = (2.0 * PI * wowRateHz) / sr

        // Pump cycle increment (0..1 per cycle at pumpRateHz).
        val pumpInc = pumpRateHz / sr

        // Bass shelf's one-pole low-pass coefficient.
        val shelfAlpha = if (isBassShelfOn) (2.0 * PI * bassShelfHz / sr).toFloat().coerceIn(0.001f, 0.9f) else 0f

        // Reserve true-peak headroom and apply instantaneous attack with smooth release; no lookahead
        // is used.
        val limiterThreshold = 24500f
        val limiterReleaseCoef = kotlin.math.exp(-1.0 / (sr * 0.100)).toFloat()

        while (inputBuffer.remaining() >= 4) {
            val rawShortL = inputBuffer.short
            val rawShortR = inputBuffer.short

            var sampleL = rawShortL.toFloat()
            var sampleR = rawShortR.toFloat()

            // Band-limit the lo-fi preset from both ends instead of only attenuating high frequencies.
            if (isBassCutOn) {
                val bcL = sampleL - bcInL + bcAlpha * bcOutL
                bcInL = sampleL; bcOutL = bcL; sampleL = bcL
                val bcR = sampleR - bcInR + bcAlpha * bcOutR
                bcInR = sampleR; bcOutR = bcR; sampleR = bcR
            }

            // 0b. PUMP (sidechain-style duck) -- see field comment above. Fast duck,
            // smooth exponential recovery: the classic sidechain envelope shape.
            if (isPumpOn) {
                pumpPhase += pumpInc
                if (pumpPhase >= 1.0) pumpPhase -= 1.0
                val pumpEnv = 1.0f - pumpDepth * kotlin.math.exp(-pumpPhase * 7.0).toFloat()
                sampleL *= pumpEnv
                sampleR *= pumpEnv
            }

            // Apply the bass shelf before saturation and limiting; downstream hardware gain is outside
            // this limiter.
            if (isBassShelfOn) {
                shelfStateL += shelfAlpha * (sampleL - shelfStateL)
                sampleL += shelfStateL * bassShelfGain
                shelfStateR += shelfAlpha * (sampleR - shelfStateR)
                sampleR += shelfStateR * bassShelfGain
            }

            // Parallel short comb filters diffuse delay taps into a reverb tail; lengths scale with
            // sample rate.
            if (isEchoOn) {
                val readA = (combPosA - combASamples + combBufAL.size) % combBufAL.size
                val outAL = combBufAL[readA]
                val outAR = combBufAR[readA]
                combBufAL[combPosA] = sampleL + outAL * feedback
                combBufAR[combPosA] = sampleR + outAR * feedback
                combPosA++
                if (combPosA >= combBufAL.size) combPosA = 0

                val readB = (combPosB - combBSamples + combBufBL.size) % combBufBL.size
                val outBL = combBufBL[readB]
                val outBR = combBufBR[readB]
                combBufBL[combPosB] = sampleL + outBL * feedback
                combBufBR[combPosB] = sampleR + outBR * feedback
                combPosB++
                if (combPosB >= combBufBL.size) combPosB = 0

                val readC = (combPosC - combCSamples + combBufCL.size) % combBufCL.size
                val outCL = combBufCL[readC]
                val outCR = combBufCR[readC]
                combBufCL[combPosC] = sampleL + outCL * feedback
                combBufCR[combPosC] = sampleR + outCR * feedback
                combPosC++
                if (combPosC >= combBufCL.size) combPosC = 0

                val diffL = (outAL + outBL + outCL) * 0.3333f
                val diffR = (outAR + outBR + outCR) * 0.3333f

                val echoOutL: Float
                val echoOutR: Float
                if (isPingPong) {
                    echoOutL = diffL * 0.65f + diffR * 0.35f
                    echoOutR = diffR * 0.65f + diffL * 0.35f
                } else {
                    echoOutL = diffL
                    echoOutR = diffR
                }

                sampleL = sampleL * dry + echoOutL * wet
                sampleR = sampleR * dry + echoOutR * wet
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

            // 4. ANALOG TAPE SATURATION
            if (isSatOn) {
                sampleL = softClip(sampleL, satDrive)
                sampleR = softClip(sampleR, satDrive)
            }

            // 5. WOW -- slow amplitude wobble, the published open-source technique for
            // emulating tape/turntable speed instability (output = input * (1 + depth
            // * sin(2*pi*rate*t))), used by Lo-Fi Radio.
            if (isWowOn) {
                wowPhase += wowInc
                if (wowPhase > 2.0 * PI) wowPhase -= 2.0 * PI
                val wowMul = 1.0f + wowDepth * sin(wowPhase).toFloat()
                sampleL *= wowMul
                sampleR *= wowMul
            }

            // 6. VINYL NOISE / CRACKLE -- low-level hiss plus occasional short decaying
            // "pop" transients, same recipe as published open-source lo-fi noise
            // generators, used by Lo-Fi Radio.
            if (isVinylNoiseOn) {
                val hiss = (vinylRng.nextFloat() * 2f - 1f) * vinylNoiseLevel * 420f
                sampleL += hiss * (0.85f + vinylRng.nextFloat() * 0.3f)
                sampleR += hiss * (0.85f + vinylRng.nextFloat() * 0.3f)

                if (cracklePopSamplesLeftL <= 0 && vinylRng.nextFloat() < 0.000005f * vinylNoiseLevel) {
                    cracklePopSamplesLeftL = (sr * 0.012f).toInt()
                    cracklePopAmpL = (vinylRng.nextFloat() * 2f - 1f) * vinylNoiseLevel * 2600f
                }
                if (cracklePopSamplesLeftL > 0) {
                    sampleL += cracklePopAmpL * (cracklePopSamplesLeftL / (sr * 0.012f))
                    cracklePopSamplesLeftL--
                }
                if (cracklePopSamplesLeftR <= 0 && vinylRng.nextFloat() < 0.000005f * vinylNoiseLevel) {
                    cracklePopSamplesLeftR = (sr * 0.012f).toInt()
                    cracklePopAmpR = (vinylRng.nextFloat() * 2f - 1f) * vinylNoiseLevel * 2600f
                }
                if (cracklePopSamplesLeftR > 0) {
                    sampleR += cracklePopAmpR * (cracklePopSamplesLeftR / (sr * 0.012f))
                    cracklePopSamplesLeftR--
                }
            }

            // 7. SUB-BASS RUMBLE GUARD (see field comment above)
            val rbOutL = sampleL - rumbleInL + rumbleAlpha * rumbleOutL
            rumbleInL = sampleL
            rumbleOutL = rbOutL
            sampleL = rbOutL

            val rbOutR = sampleR - rumbleInR + rumbleAlpha * rumbleOutR
            rumbleInR = sampleR
            rumbleOutR = rbOutR
            sampleR = rbOutR

            // Apply a linked-channel peak limiter with attack/release smoothing to protect bass transients.
            val peak = max(abs(sampleL), abs(sampleR))
            val desiredGain = if (peak > limiterThreshold) limiterThreshold / peak else 1f
            // A smoothed attack without look-ahead lets the first transient samples
            // escape and then hard-clips them. Apply gain reduction immediately; only
            // the recovery is smoothed. This is much cleaner on kick/bass transients.
            limiterGain = if (desiredGain < limiterGain) desiredGain else
                limiterReleaseCoef * limiterGain + (1f - limiterReleaseCoef) * desiredGain
            sampleL *= limiterGain
            sampleR *= limiterGain

            // Final soft ceiling avoids a hard discontinuity if a transient or a
            // downstream combination still exceeds the PCM range by a few samples.
            val finalL = sampleL.coerceIn(-32767f, 32767f).toInt().toShort()
            val finalR = sampleR.coerceIn(-32767f, 32767f).toInt().toShort()

            outputBuffer.putShort(finalL)
            outputBuffer.putShort(finalR)
        }

        outputBuffer.flip()
    }

    private fun softClip(sample: Float, drive: Float): Float {
        // Normalized, gain-compensated tanh saturation. The old polynomial branch
        // increased average gain even for ordinary samples (and could add several dB
        // before the limiter), which made vocals and bass sound torn on loud masters.
        val x = (sample / 32768f).coerceIn(-2f, 2f)
        val driven = x * drive
        val normalization = kotlin.math.tanh(drive.toDouble()).toFloat().coerceAtLeast(0.001f)
        return (kotlin.math.tanh(driven.toDouble()).toFloat() / normalization) * 32768f
    }

}
