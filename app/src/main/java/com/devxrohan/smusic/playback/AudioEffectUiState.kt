package com.devxrohan.smusic.playback

data class EqualizerUiState(
    val isAvailable: Boolean = false,
    val enabled: Boolean = false,
    val bandLevels: List<Short> = emptyList(),
    val centerFrequencies: List<Int> = emptyList(),
    val minBandLevel: Short = -1500,
    val maxBandLevel: Short = 1500,
)

data class AudioStudioUiState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bassStrength: Int = 0,
    val spaceStrength: Int = 0,
    val reverbPreset: Int = 0,
    val bassSupported: Boolean = false,
    val spaceSupported: Boolean = false,
    val reverbSupported: Boolean = false,
)
