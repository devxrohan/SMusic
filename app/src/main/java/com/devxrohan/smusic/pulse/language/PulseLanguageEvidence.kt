package com.devxrohan.smusic.pulse.language

internal data class PulseLanguageEvidence(
    val languages: Set<String>,
    val source: String,
    val confidence: Double,
) {
    val reliable: Boolean get() = languages.isNotEmpty() && confidence >= .95
}

internal object PulseLanguagePolicy {
    fun compatible(seed: PulseLanguageEvidence?, candidate: PulseLanguageEvidence?): Boolean {
        if (seed?.reliable != true || candidate?.reliable != true) return true
        if ("zxx" in seed.languages || "zxx" in candidate.languages) return true
        return seed.languages.any { it in candidate.languages }
    }

    fun fromConsistentSamples(samples: List<Pair<String, Double>>): PulseLanguageEvidence? {
        if (samples.size < 2 || samples.any { it.first == "und" || it.second < .95 }) return null
        val codes = samples.map { it.first }.toSet()
        if (codes.size != 1) return null
        return PulseLanguageEvidence(codes, "lyrics-model", samples.minOf { it.second })
    }
}
