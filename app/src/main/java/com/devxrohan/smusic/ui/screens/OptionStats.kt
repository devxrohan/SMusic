package com.devxrohan.smusic.ui.screens

/**
 * Genuinely missing after StatsScreen.kt was deleted (the Stats screen
 * feature was removed, but this enum — used by StatPeriod.kt, ChipsRow.kt,
 * and StatsViewModel.kt for period-selection logic unrelated to the Stats
 * screen itself — was accidentally deleted along with it).
 */
enum class OptionStats {
    WEEKS,
    MONTHS,
    YEARS,
    CONTINUOUS
}
