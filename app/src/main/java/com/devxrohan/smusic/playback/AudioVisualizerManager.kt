package com.devxrohan.smusic.playback

import android.media.audiofx.Visualizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class AudioVisualizerStats(
    val bass: Float = 0f,       // Normalized 0.0f .. 1.0f
    val mid: Float = 0f,        // Normalized 0.0f .. 1.0f
    val treble: Float = 0f,     // Normalized 0.0f .. 1.0f
    val bassDb: Int = 0,        // Display value (e.g., 0 .. +18)
    val midDb: Int = 0,
    val trebleDb: Int = 0,
    val isBassPeak: Boolean = false
)

class AudioVisualizerManager(
    private val scope: CoroutineScope
) {
    private val _stats = MutableStateFlow(AudioVisualizerStats())
    val stats: StateFlow<AudioVisualizerStats> = _stats.asStateFlow()

    private var visualizer: Visualizer? = null
    private var fallbackJob: Job? = null
    private var currentSessionId: Int = 0

    @Volatile
    var isPlaying: Boolean = false
        set(value) {
            field = value
            if (!value) {
                decay()
            }
        }

    // Smoothing accumulators
    private var smoothBass = 0f
    private var smoothMid = 0f
    private var smoothTreble = 0f

    fun start(audioSessionId: Int) {
        if (visualizer != null && currentSessionId == audioSessionId && audioSessionId > 0) {
            return
        }
        if (visualizer == null && audioSessionId <= 0 && fallbackJob?.isActive == true) {
            return
        }

        stop()
        currentSessionId = audioSessionId

        if (audioSessionId > 0) {
            try {
                val vis = Visualizer(audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1] // Max resolution (usually 1024)
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                v: Visualizer?,
                                waveform: ByteArray?,
                                samplingRate: Int
                            ) {}

                            override fun onFftDataCapture(
                                v: Visualizer?,
                                fft: ByteArray?,
                                samplingRate: Int
                            ) {
                                if (fft != null && isPlaying) {
                                    processFft(fft)
                                } else {
                                    decay()
                                }
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                        false,
                        true
                    )
                    enabled = true
                }
                visualizer = vis
                Timber.d("AudioVisualizerManager: Attached native Visualizer to session $audioSessionId")
            } catch (e: Throwable) {
                Timber.w(e, "AudioVisualizerManager: Native Visualizer unavailable, using reactive fallback")
                startFallbackLoop()
            }
        } else {
            startFallbackLoop()
        }
    }

    private fun processFft(fft: ByteArray) {
        val n = fft.size / 2
        if (n <= 0) return

        var rawBass = 0f
        var rawMid = 0f
        var rawTreble = 0f

        val bassEnd = min(n, max(2, n / 12))        // ~20 Hz to 250 Hz
        val midEnd = min(n, max(bassEnd + 1, n / 2)) // ~250 Hz to 4 kHz
        val trebleEnd = n                           // ~4 kHz to 16 kHz

        for (i in 1 until bassEnd) {
            val r = fft[2 * i].toFloat()
            val im = fft[2 * i + 1].toFloat()
            rawBass += hypot(r, im)
        }
        if (bassEnd > 1) rawBass /= (bassEnd - 1)

        for (i in bassEnd until midEnd) {
            val r = fft[2 * i].toFloat()
            val im = fft[2 * i + 1].toFloat()
            rawMid += hypot(r, im)
        }
        if (midEnd > bassEnd) rawMid /= (midEnd - bassEnd)

        for (i in midEnd until trebleEnd) {
            val r = fft[2 * i].toFloat()
            val im = fft[2 * i + 1].toFloat()
            rawTreble += hypot(r, im)
        }
        if (trebleEnd > midEnd) rawTreble /= (trebleEnd - midEnd)

        // Scale factors to calibrate to 0f..1f range
        val normBass = (rawBass / 65f).coerceIn(0f, 1f)
        val normMid = (rawMid / 45f).coerceIn(0f, 1f)
        val normTreble = (rawTreble / 30f).coerceIn(0f, 1f)

        // Smooth decay/attack: fast attack, smooth decay
        smoothBass = if (normBass > smoothBass) normBass else smoothBass * 0.75f + normBass * 0.25f
        smoothMid = if (normMid > smoothMid) normMid else smoothMid * 0.78f + normMid * 0.22f
        smoothTreble = if (normTreble > smoothTreble) normTreble else smoothTreble * 0.80f + normTreble * 0.20f

        val isPeak = smoothBass > 0.72f

        _stats.value = AudioVisualizerStats(
            bass = smoothBass,
            mid = smoothMid,
            treble = smoothTreble,
            bassDb = (smoothBass * 18).toInt(),
            midDb = (smoothMid * 12).toInt(),
            trebleDb = (smoothTreble * 10).toInt(),
            isBassPeak = isPeak
        )
    }

    private fun decay() {
        smoothBass *= 0.85f
        smoothMid *= 0.85f
        smoothTreble *= 0.85f
        if (smoothBass < 0.02f) smoothBass = 0f
        if (smoothMid < 0.02f) smoothMid = 0f
        if (smoothTreble < 0.02f) smoothTreble = 0f

        _stats.value = AudioVisualizerStats(
            bass = smoothBass,
            mid = smoothMid,
            treble = smoothTreble,
            bassDb = (smoothBass * 18).toInt(),
            midDb = (smoothMid * 12).toInt(),
            trebleDb = (smoothTreble * 10).toInt(),
            isBassPeak = false
        )
    }

    private fun startFallbackLoop() {
        if (fallbackJob?.isActive == true) return
        fallbackJob?.cancel()
        fallbackJob = scope.launch(Dispatchers.Default) {
            var step = 0.0
            while (isActive) {
                if (isPlaying) {
                    step += 0.25
                    // Natural musical rhythm simulation
                    val rhythm = (sin(step * 1.8) * 0.5 + 0.5).toFloat()
                    val bassVal = ((sin(step * 0.9) * 0.4 + 0.5).toFloat() + rhythm * 0.35f + Random.nextFloat() * 0.1f).coerceIn(0f, 1f)
                    val midVal = ((sin(step * 1.4 + 1.0) * 0.3 + 0.4).toFloat() + Random.nextFloat() * 0.15f).coerceIn(0f, 1f)
                    val trebleVal = ((sin(step * 2.2 + 2.0) * 0.25 + 0.35).toFloat() + Random.nextFloat() * 0.2f).coerceIn(0f, 1f)

                    smoothBass = (smoothBass * 0.6f + bassVal * 0.4f)
                    smoothMid = (smoothMid * 0.6f + midVal * 0.4f)
                    smoothTreble = (smoothTreble * 0.6f + trebleVal * 0.4f)

                    val isPeak = smoothBass > 0.72f

                    _stats.value = AudioVisualizerStats(
                        bass = smoothBass,
                        mid = smoothMid,
                        treble = smoothTreble,
                        bassDb = (smoothBass * 18).toInt(),
                        midDb = (smoothMid * 12).toInt(),
                        trebleDb = (smoothTreble * 10).toInt(),
                        isBassPeak = isPeak
                    )
                } else {
                    decay()
                }
                delay(40) // ~25 fps update rate for buttery smooth animation
            }
        }
    }

    fun stop() {
        fallbackJob?.cancel()
        fallbackJob = null
        currentSessionId = 0
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Throwable) {
            Timber.w(e, "Error releasing visualizer")
        } finally {
            visualizer = null
        }
        decay()
    }
}
