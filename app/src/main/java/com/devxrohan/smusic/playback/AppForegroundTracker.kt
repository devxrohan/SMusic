package com.devxrohan.smusic.playback

/**
 * Set from MainActivity's onStart/onStop — a simple flag other parts of
 * the playback layer (MusicService in particular) can check to know
 * whether there's a visible Activity right now, without needing a direct
 * reference to it. Genuinely missing from the project before this: used
 * in MainActivity but never actually declared anywhere.
 */
object AppForegroundTracker {
    @Volatile
    var isForeground: Boolean = false
}
