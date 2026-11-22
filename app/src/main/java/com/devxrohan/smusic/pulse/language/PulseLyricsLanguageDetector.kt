package com.devxrohan.smusic.pulse.language

import com.devxrohan.smusic.lyrics.SMusicLyricsUtils
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal object PulseLyricsLanguageDetector {
    suspend fun detect(lyrics: String): PulseLanguageEvidence? {
        val lines = SMusicLyricsUtils.parseAnyLyrics(lyrics).asSequence()
            .map { it.text.trim() }.filter { it.isNotBlank() && !it.startsWith("[") }
            .distinct().take(40).toList()
        if (lines.size < 4) return null
        val samples = lines.chunked((lines.size + 1) / 2).take(2).map { it.joinToString(" ") }
        if (samples.size != 2 || samples.any { sample -> sample.count { it.isLetter() } < 100 }) return null
        val identifier = LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder().setConfidenceThreshold(.5f).build())
        return try {
            val results = samples.map { sample ->
                suspendCancellableCoroutine<Pair<String, Double>> { continuation ->
                    identifier.identifyPossibleLanguages(sample.take(3000))
                        .addOnSuccessListener { found ->
                            val best = found.maxByOrNull { it.confidence }
                            if (continuation.isActive) continuation.resume(
                                (best?.languageTag ?: "und") to (best?.confidence?.toDouble() ?: 0.0))
                        }
                        .addOnFailureListener {
                            if (continuation.isActive) continuation.resume("und" to 0.0)
                        }
                }
            }
            PulseLanguagePolicy.fromConsistentSamples(results)
        } finally {
            identifier.close()
        }
    }
}
