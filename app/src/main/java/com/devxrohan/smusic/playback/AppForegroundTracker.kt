package com.devxrohan.smusic.playback

/** Tracks activity visibility for playback and background-work decisions. */
object AppForegroundTracker {
    @Volatile
    var isForeground: Boolean = false
}
