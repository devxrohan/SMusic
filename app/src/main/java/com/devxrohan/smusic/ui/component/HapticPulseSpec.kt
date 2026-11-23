package com.devxrohan.smusic.ui.component

/** Shorter ticks keep rapid slider gestures distinct rather than continuous. */
internal data class HapticPulseSpec(val durationMs: Long, val amplitude: Int) {
    companion object {
        fun fromStrength(strength: Int, tick: Boolean): HapticPulseSpec {
            val level = strength.coerceIn(0, 100)
            if (level == 0) return HapticPulseSpec(0, 0)
            val duration = if (tick) 6L + level * 12L / 100 else 12L + level * 33L / 100
            return HapticPulseSpec(duration, 32 + level * 223 / 100)
        }
    }
}
