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
    SPACE_ECHO("Cosmic Echo", "Infinite stereo ping-pong", R.drawable.waves),
    EIGHT_D("8D Audio", "360° orbital surround motion", R.drawable.eight_d),
    SPATIAL_SOUND("Spatial Sound", "Widened stereo image & cross-feed", R.drawable.compare_arrows)
}

@UnstableApi
class DjAudioProcessor : BaseAudioProcessor() {

    // NOTE: `enabled` and `echoEnabled` use explicit backing fields (instead of a plain
    // @Volatile var) so that we can clear the stale delay/flanger/filter buffers the
    // instant either one flips from OFF -> ON. Without this, turning the master FX
    // switch on and jumping straight into a preset like "Club Booth" or
    // "Slowed + Reverb" would replay whatever old audio was still sitting in the delay
    // buffer from the last time effects were enabled, which is heard as the song
    // "playing twice" / overlapping itself. Routing through Flat/Reset used to work
    // only because resetFx() happened to clear the buffer as a side effect.
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

    // ==========================================
    // Lo-Fi character: a real telephone/cassette "lo-fi" sound narrows the band from
    // BOTH ends (cuts sub-bass as well as highs -- per published lo-fi DSP chains,
    // roughly a 100Hz-2.8kHz window), adds a slow amplitude "wow" wobble (tape-speed
    // instability), and a low-level noise/crackle layer. `filterSweep` above only ever
    // cuts highs OR lows, never both, so bass was never actually reduced for the Lo-Fi
    // preset -- this fixed high-pass stage is the missing "cut the bass too" half.
    // ==========================================
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

    // ==========================================
    // "Pump" (free-running sidechain-style ducking): per published club/EDM mixing
    // references, the single most defining characteristic of a "club PA" sound is
    // sidechain compression -- the beat rhythmically "ducks" the rest of the mix down
    // and lets it breathe back. We have no real beat detector, so this is a
    // free-running duck-then-recover envelope at a generic dance-music rate rather
    // than a true per-track sidechain -- kept gentle enough that it still reads as
    // "groove"/movement rather than "wrong" on a track that isn't exactly on that
    // tempo. Used by Club Booth.
    // ==========================================
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

    // ==========================================
    // In-chain bass shelf boost. IMPORTANT: this exists because the app's other "bass
    // boost" control (Android's system LoudnessEnhancer, attached to the audio
    // session) runs *after* this whole software processor, on the audio session
    // itself -- our peak limiter further down this file can only see and protect
    // audio that flows through this class, so it has zero visibility into, or control
    // over, whatever LoudnessEnhancer adds afterward. That's why bass-heavy presets
    // kept crackling even after adding the limiter: the actual extra boost was
    // happening downstream of it. A bass lift done here instead -- a standard
    // "boosted = dry + gain * lowpassed(dry)" shelf, used by countless real bass-boost
    // plugins -- flows through saturation and the limiter like everything else, so it
    // is actually protected from clipping.
    // ==========================================
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

    // Diffuser: three short parallel comb filters (Schroeder/Freeverb-style -- this is
    // the standard, well-documented open-source technique for turning a single clean
    // delay tap into something that sounds like a reverb wash instead of a distinct
    // repeated "double" copy of the signal). Each comb's length is a different short,
    // mutually-prime-ish value well under the ~35-50ms point at which a human ear
    // starts perceiving a separate discrete echo rather than one blended, "spacious"
    // sound (the Haas / precedence effect) -- summing several such short, differently
    // timed repeats is what makes them fuse into a wash instead of a clear repeat.
    // Base lengths at the "neutral" echoDelayMs=280 setting; scaled by the Delay Time
    // slider at runtime (see queueInput) but always clamped well under the ~45ms Haas
    // threshold, so moving that slider still audibly changes the wash's size/texture
    // without ever bringing back a single, clearly-separated discrete repeat.
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

    // Sub-bass "rumble guard": a fixed ~50 Hz high-pass applied at the very end of the
    // chain whenever any effect is active. Phone speakers physically cannot move
    // enough air to reproduce content below roughly 60-80 Hz cleanly -- feeding them
    // boosted sub-bass (from the resonant filter sweep, saturation drive, and the
    // loudness boost all stacking around the low end on presets like Bass Bomb /
    // Lo-Fi Radio) doesn't make the music sound "bassier" through a small speaker, it
    // just makes the speaker's voice coil over-excurse and rattle/crackle. This is a
    // standard, well-established technique in mobile audio engineering (every proper
    // bass-boost implementation pairs the boost with a sub-bass cut) and only removes
    // a band that a phone speaker can't play back anyway, so it doesn't change how any
    // preset is meant to sound on real speakers/headphones.
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

    // Final peak limiter (attack/release) -- this is what actually tames bass-drum/kick
    // transients. The old static per-sample soft-knee only rounds off a sample that is
    // ALREADY at the ceiling; it can't see a loud transient coming and reduce gain
    // ahead of it, so the stacked echo+filter+saturation+boost gain on a bass hit could
    // still slam the ceiling hard enough to sound like a crackle on every beat. A
    // proper feed-forward limiter (this is how essentially every real loudness
    // limiter/mastering limiter works) reacts within a few milliseconds and releases
    // smoothly afterward, which sounds like "controlled and punchy" instead of "clipped".
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
        // Media3 PCM buffers are native-endian. Set the order explicitly: unlike the
        // spatial processors this processor previously read/wrote Shorts using the
        // ByteBuffer default (big-endian), which can turn normal PCM into byte-swapped
        // samples and sound like harsh crackling/distortion on affected devices.
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
        // BUG FIX (one-line, textbook dry/wet crossfade -- nothing else changed): this
        // used to be `1.0f - (wet * 0.35f)`, which barely reduced the original signal
        // even at full wet (wet=0.50 -> dry=0.825). That left the original vocal and a
        // single ~300-450ms-delayed copy playing at nearly equal volume, which for
        // short/repeated lyric lines is heard as the song literally "playing twice".
        // The standard dry/wet mix (dry+wet sum to 1) still lets the effect reach full
        // intensity at wet=1.0, so the preset's character/strength is unchanged -- it
        // just no longer stacks two near-equal-volume copies at moderate settings.
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
        val flangerModDepth = (sr * 0.0018f * flangerDepth) // +/- 1.8 ms modulation

        // Drive multiplier reduced (2.2 -> 1.5): a limiter can only catch a *brief*
        // overshoot -- it cannot fully undo a boost this large without itself sounding
        // squashed/pumping, which reads as "still distorted" even once hard clipping
        // is gone. Cutting the drive itself, globally, is safer than leaning on the
        // limiter to rescue it.
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

        // Peak limiter: fast attack (catches a transient within ~2ms), slower release
        // (~120ms, so it doesn't pump audibly) -- standard mastering-limiter timing.
        // More headroom (27000 -> 23000) and a faster attack (2ms -> 0.7ms): a simple
        // limiter with no lookahead can still let a fast transient's first instant
        // through before it reacts, so the only reliable protection is leaving enough
        // headroom below the ceiling that even that brief overshoot doesn't reach it.
        // Leave enough true-peak headroom for the downstream Android output path.
        // The attack is instantaneous (no look-ahead buffer), while release is smooth.
        val limiterThreshold = 24500f
        val limiterReleaseCoef = kotlin.math.exp(-1.0 / (sr * 0.100)).toFloat()

        while (inputBuffer.remaining() >= 4) {
            val rawShortL = inputBuffer.short
            val rawShortR = inputBuffer.short

            var sampleL = rawShortL.toFloat()
            var sampleR = rawShortR.toFloat()

            // 0. LO-FI BASS CUT -- see field comment: narrows the band from the bottom
            // end too (a real lo-fi/telephone sound is band-limited on both sides, not
            // just dulled on top), which is also what keeps Lo-Fi Radio's bass from
            // overloading a phone speaker.
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

            // 0c. IN-CHAIN BASS SHELF BOOST (see field comment above). Standard
            // "boosted = dry + gain * lowpassed(dry)" shelf -- flows through
            // saturation and the limiter below like everything else, unlike the
            // system LoudnessEnhancer boost.
            if (isBassShelfOn) {
                shelfStateL += shelfAlpha * (sampleL - shelfStateL)
                sampleL += shelfStateL * bassShelfGain
                shelfStateR += shelfAlpha * (sampleR - shelfStateR)
                sampleR += shelfStateR * bassShelfGain
            }

            // 1. ECHO / REVERB PROCESSING -- Schroeder/Freeverb-style diffuser: three
            // short parallel comb filters (see field comments above) summed together,
            // instead of one single long delay tap. A lone tap at 180-450ms is always
            // perceived as a distinct repeated copy of the signal no matter how it's
            // mixed -- that IS the "double voice" that was being reported on Slowed +
            // Reverb, Cosmic Echo and Lo-Fi Radio. Three short, differently-timed
            // repeats summed together fuse into one continuous wash instead (the
            // standard, published technique behind essentially every real-time
            // software reverb), while `echoFeedback` still controls how long that
            // wash sustains and `echoWetMix` still controls how present it is.
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

            // 8. PEAK LIMITER (attack/release) -- see field comment on `limiterGain`.
            // This is what actually stops a bass-heavy beat from crackling: it looks at
            // the loudest of the two channels and, if it's above threshold, pulls the
            // gain down fast (2ms) and lets it back up slowly (120ms) rather than
            // waiting for a hard clip and rounding it off after the fact.
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
