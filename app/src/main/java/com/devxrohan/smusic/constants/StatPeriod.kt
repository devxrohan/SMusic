package com.devxrohan.smusic.constants

import com.devxrohan.smusic.ui.screens.OptionStats
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class StatPeriod {
    WEEK_1,
    MONTH_1,
    MONTH_3,
    MONTH_6,
    YEAR_1,
    ALL,
    ;

    fun toTimeMillis(): Long =
        when (this) {
            WEEK_1 ->
                LocalDateTime
                    .now()
                    .minusWeeks(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            MONTH_1 ->
                LocalDateTime
                    .now()
                    .minusMonths(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            MONTH_3 ->
                LocalDateTime
                    .now()
                    .minusMonths(3)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            MONTH_6 ->
                LocalDateTime
                    .now()
                    .minusMonths(6)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            YEAR_1 ->
                LocalDateTime
                    .now()
                    .minusMonths(12)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            ALL -> 0
        }
}

fun statToPeriod(
    selection: OptionStats,
    test: Int,
): Long =
    when (selection) {
        OptionStats.WEEKS -> {
            LocalDateTime
                .now()
                .toLocalDate().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).atStartOfDay()
                .minusWeeks(test.coerceAtLeast(0).toLong())
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli() - 1L
        }

        OptionStats.MONTHS -> {
            LocalDateTime
                .now()
                .withDayOfMonth(1)
                .toLocalDate().atStartOfDay()
                .minusMonths(test.coerceAtLeast(0).toLong())
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli() - 1L
        }

        OptionStats.YEARS -> {
            LocalDateTime
                .now()
                .withDayOfMonth(1)
                .withMonth(1)
                .toLocalDate().atStartOfDay()
                .minusYears(test.coerceAtLeast(0).toLong())
                .toInstant(
                    ZoneOffset.UTC,
                ).toEpochMilli() - 1L
        }

        OptionStats.CONTINUOUS -> {
            val index = test.coerceIn(0, StatPeriod.entries.lastIndex)
            StatPeriod.entries[index].toTimeMillis()
        }
    }
