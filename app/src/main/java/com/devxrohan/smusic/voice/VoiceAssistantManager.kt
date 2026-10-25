package com.devxrohan.smusic.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.devxrohan.smusic.playback.MusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Hands-free voice engine for SMusic.
 *
 * Works the way a normal assistant does: while the voice service is enabled it keeps ONE speech
 * recognizer cycling in the background (screen off, earbuds, app in background - no "Speak" tap needed).
 *
 *  - No raw AudioRecord loop and no noise-triggered start/stop. That loop used to fight the recognizer
 *    for the microphone, clipped the first words ("smusic ...") and re-triggered itself non-stop, which
 *    was the cause of the hangs and battery drain.
 *  - Prefers the on-device recognizer (cheap, no network round trip per cycle).
 *  - Adaptive back-off: while nobody is talking the pause between cycles grows, so idle cost stays low;
 *    it snaps back to instant the moment speech is heard.
 *  - Only real commands run. Random speech / music lyrics are ignored instead of being treated as a
 *    song search. In "wake word" mode a command must start with "SMusic" / "Hey SMusic".
 *  - The recognizer's start/stop beeps are muted only for a few hundred ms around each cycle, never
 *    for the whole session, so notification sounds keep working.
 */
class VoiceAssistantManager(
    private val context: Context,
    private val onWakeWordHeard: ((String) -> Unit)? = null,
    private val onCommandRecognized: (VoiceCommand, String) -> Unit
) : RecognitionListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var isRunning = false
    private var oneShotSession = false          // Settings "test" / HUD tap / notification Speak when service idle
    private var requireWakeWord = false
    private var isRecognizing = false
    private var followUpSession = false         // "Hey SMusic" alone -> next utterance is the command
    private var consecutiveIdleCycles = 0
    private var consecutiveErrors = 0

    @Volatile
    private var isTtsSpeaking = false
    private var ttsFinishedTimestamp = 0L

    private var duckedFrom: Float? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var mutedAtVolumeSystem = -1
    private var mutedAtVolumeNotification = -1
    private var isSystemMuted = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow<String?>(null)
    val lastRecognizedText: StateFlow<String?> = _lastRecognizedText.asStateFlow()

    private val _audioRms = MutableStateFlow(0f)
    val audioRms: StateFlow<Float> = _audioRms.asStateFlow()

    companion object {
        private const val TTS_SILENCE_GRACE_MS = 700L
        private const val MIN_RESTART_DELAY_MS = 150L
        private const val MAX_IDLE_RESTART_DELAY_MS = 900L
        private const val LONG_IDLE_RESTART_DELAY_MS = 2000L
        private const val BEEP_MUTE_WINDOW_MS = 450L

        private val WAKE_PREFIX = Regex(
            "^(?:(?:hey|hi|hello|ok|okay|hai|yo)\\s+)?(?:s\\s*music|smusic|ess\\s*music|es\\s*music|s\\s*musik|sm\\s*music|as\\s*music)\\b[,\\s]*"
        )
    }

    // ------------------------------------------------------------------ public API

    fun setTtsSpeaking(speaking: Boolean) {
        isTtsSpeaking = speaking
        if (speaking) {
            cancelRecognition()
        } else {
            ttsFinishedTimestamp = System.currentTimeMillis()
            if (isRunning) scheduleNextCycle(TTS_SILENCE_GRACE_MS)
        }
    }

    fun start(requireWakeWord: Boolean = false) {
        this.requireWakeWord = requireWakeWord
        if (isRunning) return
        isRunning = true
        oneShotSession = false
        consecutiveIdleCycles = 0
        consecutiveErrors = 0
        _isListening.value = false
        Timber.i("VoiceAssistantManager started continuous listening (requireWakeWord=%b)", requireWakeWord)
        scheduleNextCycle(0L)
    }

    fun stop() {
        isRunning = false
        oneShotSession = false
        followUpSession = false
        mainHandler.removeCallbacksAndMessages(null)
        _isListening.value = false
        cancelRecognition()
    }

    /** Only restarts nothing: just changes how the next results are interpreted. */
    fun updateSettings(requireWakeWord: Boolean) {
        this.requireWakeWord = requireWakeWord
    }

    /** Notification "Speak", HUD tap, or Settings test: listen right now for one command. */
    fun triggerListeningSession() {
        mainHandler.post {
            val now = System.currentTimeMillis()
            if (isTtsSpeaking || (now - ttsFinishedTimestamp < TTS_SILENCE_GRACE_MS)) return@post
            followUpSession = true
            if (!isRunning) {
                isRunning = true
                oneShotSession = true
            }
            cancelPendingCycle()
            if (isRecognizing) {
                // Restart so the user gets a fresh follow-up window.
                try { speechRecognizer?.cancel() } catch (_: Exception) {}
                isRecognizing = false
            }
            startCycle()
        }
    }

    fun destroy() {
        stop()
        mainHandler.post {
            restoreSystemSound()
            try { speechRecognizer?.destroy() } catch (_: Exception) {}
            speechRecognizer = null
        }
    }

    // ------------------------------------------------------------------ cycle control

    private fun cancelPendingCycle() = mainHandler.removeCallbacks(cycleRunnable)

    private val cycleRunnable = Runnable { startCycle() }

    private fun scheduleNextCycle(delayMs: Long) {
        if (!isRunning) return
        cancelPendingCycle()
        mainHandler.postDelayed(cycleRunnable, delayMs)
    }

    /** Pause between cycles grows while nobody talks (battery) and snaps back to ~instant on speech. */
    private fun idleDelay(): Long {
        val cap = if (consecutiveIdleCycles > 40) LONG_IDLE_RESTART_DELAY_MS else MAX_IDLE_RESTART_DELAY_MS
        return (MIN_RESTART_DELAY_MS + consecutiveIdleCycles * 150L).coerceAtMost(cap)
    }

    /** Lower the music while a command is being said so the recognizer hears the voice, not the song. */
    private fun duckMusic() {
        mainHandler.post {
            runCatching {
                val p = MusicService.instance?.player ?: return@runCatching
                if (duckedFrom == null && p.isPlaying) {
                    duckedFrom = p.volume
                    p.volume = p.volume * 0.2f
                }
            }
        }
    }

    private fun restoreMusic() {
        mainHandler.post {
            runCatching {
                val from = duckedFrom ?: return@runCatching
                MusicService.instance?.player?.volume = from
                duckedFrom = null
            }
        }
    }

    private fun inCall(): Boolean = audioManager?.mode.let {
        it == AudioManager.MODE_IN_CALL || it == AudioManager.MODE_IN_COMMUNICATION || it == AudioManager.MODE_RINGTONE
    }

    private fun createRecognizer(): SpeechRecognizer? = try {
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            try {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context.applicationContext)
            } catch (_: Throwable) {
                SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            }
        } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        } else null
        r?.setRecognitionListener(this)
        r
    } catch (e: Throwable) {
        Timber.e(e, "Failed to create SpeechRecognizer")
        null
    }

    private fun startCycle() {
        if (!isRunning || isRecognizing) return
        val now = System.currentTimeMillis()
        if (isTtsSpeaking || now - ttsFinishedTimestamp < TTS_SILENCE_GRACE_MS) {
            scheduleNextCycle(TTS_SILENCE_GRACE_MS)
            return
        }
        if (inCall()) { // never touch the mic during phone/VoIP calls
            scheduleNextCycle(3000L)
            return
        }
        try {
            if (speechRecognizer == null) speechRecognizer = createRecognizer()
            val recognizer = speechRecognizer
            if (recognizer == null) {
                Timber.w("No speech recognizer available on this device")
                stop()
                return
            }
            muteBeep()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            }
            isRecognizing = true
            recognizer.startListening(intent)
        } catch (e: Throwable) {
            Timber.e(e, "Failed to start SpeechRecognizer cycle")
            isRecognizing = false
            consecutiveErrors++
            recoverFromError()
        }
    }

    private fun finishCycle(heardSomething: Boolean) {
        restoreMusic()
        isRecognizing = false
        _isListening.value = false
        muteBeep()
        mainHandler.postDelayed({ restoreSystemSound() }, BEEP_MUTE_WINDOW_MS)

        if (heardSomething) consecutiveIdleCycles = 0 else {
            consecutiveIdleCycles++
            followUpSession = false // follow-up window expires when nothing was said
        }

        if (oneShotSession && !followUpSession) {
            oneShotSession = false
            isRunning = false
            return
        }
        scheduleNextCycle(idleDelay())
    }

    private fun recoverFromError() {
        // Rebuild the recognizer after repeated failures (busy/client errors are common on some OEMs).
        if (consecutiveErrors >= 3) {
            consecutiveErrors = 0
            try { speechRecognizer?.destroy() } catch (_: Exception) {}
            speechRecognizer = null
        }
        scheduleNextCycle((1000L * (consecutiveErrors + 1)).coerceAtMost(4000L))
    }

    private fun cancelRecognition() {
        restoreMusic()
        isRecognizing = false
        _isListening.value = false
        mainHandler.post {
            restoreSystemSound()
            try { speechRecognizer?.cancel() } catch (_: Exception) {}
        }
    }

    // ------------------------------------------------------------------ beep muting (short windows only)

    private fun muteBeep() {
        val am = audioManager ?: return
        try {
            if (!isSystemMuted) {
                mutedAtVolumeSystem = am.getStreamVolume(AudioManager.STREAM_SYSTEM)
                mutedAtVolumeNotification = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            }
            try { am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0) } catch (_: Exception) {}
            try { am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0) } catch (_: Exception) {}
            isSystemMuted = true
        } catch (_: Exception) {}
    }

    private fun restoreSystemSound() {
        if (!isSystemMuted || isRecognizing) return
        val am = audioManager ?: return
        try {
            try { am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0) } catch (_: Exception) {}
            try { am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0) } catch (_: Exception) {}
            if (mutedAtVolumeSystem != -1) try { am.setStreamVolume(AudioManager.STREAM_SYSTEM, mutedAtVolumeSystem, 0) } catch (_: Exception) {}
            if (mutedAtVolumeNotification != -1) try { am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, mutedAtVolumeNotification, 0) } catch (_: Exception) {}
        } catch (_: Exception) {}
        isSystemMuted = false
        mutedAtVolumeSystem = -1
        mutedAtVolumeNotification = -1
    }

    // ------------------------------------------------------------------ RecognitionListener

    override fun onReadyForSpeech(params: Bundle?) {
        _isListening.value = true
        consecutiveErrors = 0
        mainHandler.postDelayed({ if (isRecognizing) restoreSystemSound() }, BEEP_MUTE_WINDOW_MS)
    }

    override fun onBeginningOfSpeech() {
        _isListening.value = true
        consecutiveIdleCycles = 0
    }

    override fun onRmsChanged(rmsdB: Float) {
        _audioRms.value = (rmsdB + 2f).coerceAtLeast(0f) * 6f
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _isListening.value = false
        muteBeep()
    }

    override fun onError(error: Int) {
        Timber.d("SpeechRecognizer onError: %d", error)
        _isListening.value = false
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                Timber.w("Microphone permission missing; stopping voice assistant loop")
                isRecognizing = false
                stop()
            }
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                consecutiveErrors = 0
                finishCycle(heardSomething = false)
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                isRecognizing = false
                restoreMusic()
                consecutiveErrors++
                try { speechRecognizer?.cancel() } catch (_: Exception) {}
                restoreSystemSound()
                recoverFromError()
            }
            else -> finishCycle(heardSomething = false)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val partialText = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
        if (!partialText.isNullOrBlank()) {
            _lastRecognizedText.value = partialText
            // Only light up the HUD for real wake phrases / manual sessions so ambient talk stays invisible.
            if (followUpSession || WAKE_PREFIX.containsMatchIn(normalize(partialText))) {
                duckMusic()
                onWakeWordHeard?.invoke(partialText)
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onResults(results: Bundle?) {
        _isListening.value = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            .map { it.trim() }.filter { it.isNotBlank() }
        if (matches.isEmpty()) {
            finishCycle(heardSomething = false)
            return
        }
        _lastRecognizedText.value = matches.first()
        Timber.i("Voice Assistant heard: %s", matches.joinToString(" | "))

        val wasFollowUp = followUpSession
        followUpSession = false
        var handled = false

        for (candidate in matches) {
            val normalized = normalize(candidate)
            val wake = WAKE_PREFIX.find(normalized)

            if (wake != null) {
                val remainder = normalized.removeRange(wake.range).trim()
                onWakeWordHeard?.invoke("Hey SMusic")
                if (remainder.isBlank()) {
                    // "Hey SMusic" alone: pause music so the user is heard, then take the command.
                    runCatching { MusicService.instance?.player?.pause() }
                    followUpSession = true
                    handled = true
                    break
                }
                val command = VoiceCommandParser.parse(remainder, requireWakeWord = false)
                onCommandRecognized(
                    if (command is VoiceCommand.Unknown) VoiceCommand.PlaySong(remainder) else command,
                    remainder,
                )
                handled = true
                break
            }

            // No wake phrase: only act in direct-command mode (or a manual/follow-up session).
            if (!requireWakeWord || wasFollowUp) {
                val command = VoiceCommandParser.parse(candidate, requireWakeWord = false)
                if (command !is VoiceCommand.Unknown) {
                    onCommandRecognized(command, candidate)
                    handled = true
                    break
                }
            }
        }

        // Manual session (Speak / HUD): anything said is treated as "play this".
        if (!handled && wasFollowUp) {
            val top = matches.first()
            if (top.length >= 2) {
                onCommandRecognized(VoiceCommand.PlaySong(top), top)
                handled = true
            }
        }

        finishCycle(heardSomething = handled)
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}
