package com.devxrohan.smusic.ui.sharedmusic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devxrohan.smusic.media.MediaInspector
import com.devxrohan.smusic.media.TemporaryMediaManager
import com.devxrohan.smusic.recognition.AudioExtractor
import com.devxrohan.smusic.recognition.AudioSegmentSelector
import com.devxrohan.smusic.recognition.WavPcmReader
import com.devxrohan.smusic.shazamkit.Shazam
import com.devxrohan.smusic.shazamkit.ShazamSignatureGenerator
import com.devxrohan.smusic.shazamkit.models.RecognitionResult
import com.devxrohan.smusic.share.SharedContent
import com.devxrohan.smusic.share.SharedContentType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class SharedMusicUiState {
    data object Idle : SharedMusicUiState()
    data class Processing(val message: String) : SharedMusicUiState()
    data class Success(val result: RecognitionResult) : SharedMusicUiState()
    data object NoMatch : SharedMusicUiState()
    data object NoAudio : SharedMusicUiState()
    data class Error(val message: String) : SharedMusicUiState()
    data class UrlShared(val url: String) : SharedMusicUiState()
}

/**
 * New (not a direct SMusic port): SMusic wires its share-to-identify flow through a larger
 * multi-provider usecase/songs abstraction (providers/, songs/, usecases/ packages) which were
 * not ported — see ACCOUNT_INTEGRATION_REPORT.md. This reuses SMusic's own, already-working
 * Shazam pipeline (the same ShazamSignatureGenerator/Shazam.recognize() MusicRecognitionScreen.kt
 * already uses for mic input) and feeds it audio extracted from the shared video/audio file via
 * the ported AudioExtractor + AudioSegmentSelector, trying a few candidate windows until one
 * resolves — the same "try several windows" strategy SMusic' selector encodes.
 */
@HiltViewModel
class SharedMusicViewModel @Inject constructor(
    application: Application,
) : AndroidViewModel(application) {

    private val tempManager = TemporaryMediaManager(application)

    private val _uiState = MutableStateFlow<SharedMusicUiState>(SharedMusicUiState.Idle)
    val uiState: StateFlow<SharedMusicUiState> = _uiState.asStateFlow()

    fun processSharedContent(content: SharedContent) {
        when (content.type) {
            SharedContentType.URL -> {
                val sharedUrl = content.text?.trim().orEmpty()
                if (sharedUrl.isBlank()) {
                    _uiState.value = SharedMusicUiState.Error("No link was shared")
                } else {
                    resolveSharedUrl(sharedUrl)
                }
            }
            SharedContentType.TEXT -> {
                _uiState.value = SharedMusicUiState.Error("No video or audio was shared")
            }
            SharedContentType.VIDEO, SharedContentType.AUDIO -> {
                val uri = content.uri ?: run {
                    _uiState.value = SharedMusicUiState.NoAudio
                    return
                }
                identify(uri)
            }
            SharedContentType.UNKNOWN -> {
                _uiState.value = SharedMusicUiState.Error("Unsupported shared content")
            }
        }
    }

    /**
     * Resolve a shared public media link into a temporary audio/video file, then pass it
     * through the same Shazam recognition path used for shared files.
     */
    private fun resolveSharedUrl(url: String) {
        _uiState.value = SharedMusicUiState.Processing("Resolving shared link...")
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val resolver = com.devxrohan.smusic.media.LinkMediaResolver(context, tempManager)
                val file = resolver.resolveMedia(url)
                if (file == null || !file.exists() || file.length() == 0L) {
                    _uiState.value = SharedMusicUiState.Error(
                        "Couldn't extract audio from this link. Try sharing the video/audio file instead."
                    )
                    return@launch
                }
                identify(android.net.Uri.fromFile(file))
            } catch (e: Exception) {
                _uiState.value = SharedMusicUiState.Error(
                    e.message ?: "Couldn't resolve the shared link"
                )
            }
        }
    }

    private fun identify(uri: android.net.Uri) {
        _uiState.value = SharedMusicUiState.Processing("Reading shared media...")
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val extractor = AudioExtractor(context, tempManager)
                try {
                    val mediaInfo = MediaInspector.inspect(context, uri)
                    if (!mediaInfo.hasAudio) {
                        _uiState.value = SharedMusicUiState.NoAudio
                        return@withContext
                    }

                    val candidateCount = AudioSegmentSelector.getCandidateCount(mediaInfo.durationMs)
                    for (candidateIndex in 0 until candidateCount) {
                        val window = AudioSegmentSelector.selectSegment(mediaInfo.durationMs, candidateIndex)
                        _uiState.value = SharedMusicUiState.Processing(
                            "Listening (${candidateIndex + 1}/$candidateCount)...",
                        )

                        val audioSource = try {
                            extractor.extractSegment(uri, mediaInfo, window.startMs, window.durationMs)
                        } catch (e: Exception) {
                            continue
                        }

                        val samples = WavPcmReader.readAsPcm16Mono(
                            audioSource.file,
                            audioSource.sampleRate,
                            audioSource.channels,
                            targetSampleRateHz = 16000,
                        )
                        tempManager.cleanup(audioSource.file)

                        if (samples.isEmpty()) continue

                        val signature = ShazamSignatureGenerator().apply {
                            feedPcm16Mono(samples)
                        }.nextSignatureOrNull() ?: continue

                        val result = Shazam.recognize(signature.uri, signature.sampleDurationMs)
                        result.onSuccess {
                            _uiState.value = SharedMusicUiState.Success(it)
                            return@withContext
                        }
                    }

                    _uiState.value = SharedMusicUiState.NoMatch
                } catch (e: Exception) {
                    _uiState.value = SharedMusicUiState.Error(e.message ?: "Recognition failed")
                } finally {
                    tempManager.cleanupAll()
                }
            }
        }
    }
}
