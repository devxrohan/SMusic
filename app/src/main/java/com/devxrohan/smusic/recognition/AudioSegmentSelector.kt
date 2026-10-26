package com.devxrohan.smusic.recognition

object AudioSegmentSelector {

    data class SelectedWindow(
        val startMs: Long,
        val durationMs: Long
    )

    // 12 seconds is the optimal fingerprint window for Shazam
    private const val SEGMENT_DURATION_MS = 12_000L

    fun getCandidateCount(totalDurationMs: Long): Int {
        return when {
            totalDurationMs >= 60_000L -> 5 // Long video / YouTube: test 5 distinct windows
            totalDurationMs >= 25_000L -> 4 // Medium video (Reels / Shorts): test 4 windows
            else -> 2 // Short video (< 25s)
        }
    }

    fun selectSegment(totalDurationMs: Long, candidateIndex: Int = 0): SelectedWindow {
        val duration = if (totalDurationMs > 0) totalDurationMs else 30_000L

        val windows = when {
            // Long tracks / Music videos (> 1 minute, e.g. YouTube):
            // Test past movie/talking intros directly into verses and choruses!
            duration >= 60_000L -> listOf(
                // Candidate 0: 35s - 47s (Past movie/dialogue intros, where singing/verse begins)
                SelectedWindow(
                    startMs = 35_000L.coerceAtMost((duration - SEGMENT_DURATION_MS).coerceAtLeast(0L)),
                    durationMs = minOf(SEGMENT_DURATION_MS, duration)
                ),
                // Candidate 1: 65s - 77s (First chorus / main hook)
                SelectedWindow(
                    startMs = 65_000L.coerceAtMost((duration - SEGMENT_DURATION_MS).coerceAtLeast(0L)),
                    durationMs = minOf(SEGMENT_DURATION_MS, duration)
                ),
                // Candidate 2: 15s - 27s (Early melody / instrumental intro)
                SelectedWindow(
                    startMs = 15_000L.coerceAtMost((duration - SEGMENT_DURATION_MS).coerceAtLeast(0L)),
                    durationMs = minOf(SEGMENT_DURATION_MS, duration)
                ),
                // Candidate 3: 100s - 112s (Second hook / drop)
                SelectedWindow(
                    startMs = 100_000L.coerceAtMost((duration - SEGMENT_DURATION_MS).coerceAtLeast(0L)),
                    durationMs = minOf(SEGMENT_DURATION_MS, duration)
                ),
                // Candidate 4: 0s - 12s (Track start)
                SelectedWindow(
                    startMs = 0L,
                    durationMs = minOf(SEGMENT_DURATION_MS, duration)
                )
            )

            // Medium video (25s - 60s, e.g. Instagram Reels, TikToks, Shorts):
            duration >= 25_000L -> listOf(
                // Candidate 0: 3s - 15s (Core audio, skipping initial share stutter)
                SelectedWindow(
                    startMs = 3_000L,
                    durationMs = minOf(SEGMENT_DURATION_MS, (duration - 3_000L).coerceAtLeast(6_000L))
                ),
                // Candidate 1: 14s - 26s (Middle beat drop / hook)
                SelectedWindow(
                    startMs = 14_000L.coerceAtMost((duration - 6_000L).coerceAtLeast(0L)),
                    durationMs = minOf(SEGMENT_DURATION_MS, (duration - 14_000L).coerceAtLeast(6_000L))
                ),
                // Candidate 2: 0s - 12s (Start from beginning)
                SelectedWindow(
                    startMs = 0L,
                    durationMs = minOf(SEGMENT_DURATION_MS, duration)
                ),
                // Candidate 3: Ending segment
                SelectedWindow(
                    startMs = (duration - SEGMENT_DURATION_MS).coerceAtLeast(0L),
                    durationMs = minOf(SEGMENT_DURATION_MS, duration)
                )
            )

            // Short clips (< 25s, e.g. Snapchat):
            else -> listOf(
                SelectedWindow(
                    startMs = 0L,
                    durationMs = minOf(SEGMENT_DURATION_MS, duration)
                ),
                SelectedWindow(
                    startMs = (duration / 3).coerceAtLeast(0L),
                    durationMs = minOf(SEGMENT_DURATION_MS, (duration - duration / 3).coerceAtLeast(5_000L))
                )
            )
        }

        return windows.getOrElse(candidateIndex % windows.size) { windows[0] }
    }
}
